package com.crowdsos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Phone-to-phone SOS relay over Nearby Connections (Bluetooth + Wi-Fi).
 * Envelope (plaintext routing header): { id, ttl, hops, data }   data = AES-GCM encrypted payload.
 * Rules: drop duplicates -> if online deliver to server -> else ttl-1, hops+1, forward to neighbours.
 */
class RelayManager(
    private val ctx: Context,
    private val role: String,                       // "A".."D"
    private val serverUrl: () -> String,
    private val location: () -> Pair<Double, Double>,
    private val log: (String) -> Unit
) {
    private val client = Nearby.getConnectionsClient(ctx)
    private val service = "com.crowdsos.relay"
    private val connected = ConcurrentHashMap<String, String>()   // endpointId -> role
    private val names = ConcurrentHashMap<String, String>()
    private val seen: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    // DEMO chain: A - B - C - D. Phones in one room would all see each other, so we only link neighbours.
    private val chain = mapOf("A" to setOf("B"), "B" to setOf("A", "C"), "C" to setOf("B", "D"), "D" to setOf("C"))
    private fun allowed(other: String) = other in (chain[role] ?: emptySet())

    fun start() {
        client.startAdvertising(role, service, lifecycle,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { log("Advertising as $role") }
            .addOnFailureListener { log("Advertise failed: ${it.message}") }
        client.startDiscovery(service, discovery,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { log("Searching for neighbours…") }
            .addOnFailureListener { log("Discovery failed: ${it.message}") }
    }

    fun stop() { client.stopAllEndpoints(); client.stopAdvertising(); client.stopDiscovery(); connected.clear() }

    /** Called when the user taps an emergency type (phone A). */
    fun sendSos(type: String) {
        val (lat, lon) = location()
        val priority = when (type) { "medical", "accident", "fire" -> "critical"; "police" -> "urgent"; else -> "normal" } // TODO: replace with on-device AI model
        val payload = JSONObject().put("type", type).put("lat", lat).put("lon", lon)
            .put("ts", System.currentTimeMillis()).put("priority", priority)
        val id = "CS-" + Random.nextInt(1000, 9999)
        val env = JSONObject().put("id", id).put("ttl", 5).put("hops", 0).put("data", Crypto.encrypt(payload.toString()))
        seen.add(id)
        log("SOS $type created: $id (encrypted)")
        if (isOnline()) post(env) else if (connected.isEmpty()) log("No neighbours connected yet") else forward(env, null)
    }

    private fun handle(env: JSONObject, from: String) {
        val id = env.getString("id")
        if (!seen.add(id)) { log("Duplicate $id dropped"); return }
        log("RX $id  hops=${env.getInt("hops")}  ttl=${env.getInt("ttl")}")
        if (isOnline()) { post(env); return }
        val ttl = env.getInt("ttl")
        if (ttl <= 0) { log("TTL expired, dropped"); return }
        env.put("ttl", ttl - 1).put("hops", env.getInt("hops") + 1)
        forward(env, from)
    }

    private fun forward(env: JSONObject, except: String?) {
        val targets = connected.keys.filter { it != except }
        if (targets.isEmpty()) { log("Nobody to forward to"); return }
        targets.forEach { client.sendPayload(it, Payload.fromBytes(env.toString().toByteArray())) }
        log("Relayed ${env.getString("id")} to ${targets.map { connected[it] }}")
    }

    private fun isOnline(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && role == "D" // D may be on a local-only Wi-Fi with the laptop
    }

    private fun post(env: JSONObject) = thread {
        try {
            val c = URL(serverUrl().trimEnd('/') + "/sos").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 5000
            c.outputStream.use { it.write(env.toString().toByteArray()) }
            log("Delivered ${env.getString("id")} to dashboard (HTTP ${c.responseCode})")
        } catch (e: Exception) { log("Delivery failed: ${e.message}") }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) { p.asBytes()?.let { handle(JSONObject(String(it)), id) } }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (allowed(info.endpointName)) { names[id] = info.endpointName; client.acceptConnection(id, payloadCb) }
            else client.rejectConnection(id)
        }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (r.status.isSuccess) { connected[id] = names[id] ?: id; log("Linked with ${connected[id]}") }
            else log("Link failed (${r.status.statusCode})")
        }
        override fun onDisconnected(id: String) { log("Lost ${connected.remove(id)}") }
    }
    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            // only the lower-lettered phone dials, so two phones never dial each other at once
            if (allowed(info.endpointName) && role < info.endpointName)
                client.requestConnection(role, id, lifecycle).addOnFailureListener { log("Dial failed: ${it.message}") }
        }
        override fun onEndpointLost(id: String) {}
    }
}
