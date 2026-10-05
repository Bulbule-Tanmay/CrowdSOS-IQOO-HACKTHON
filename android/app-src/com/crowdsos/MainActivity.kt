package com.crowdsos

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var relay: RelayManager? = null
    private lateinit var logView: TextView
    private lateinit var urlInput: EditText
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(TextView(this).apply { text = "CrowdSOS"; textSize = 28f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "1. Pick this phone's role (A = victim … D = has internet)" })
        val roles = LinearLayout(this)
        listOf("A", "B", "C", "D").forEach { r ->
            roles.addView(Button(this).apply { text = r; setOnClickListener { startRole(r) } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        root.addView(roles)
        status = TextView(this).apply { text = "Not started" }
        root.addView(status)

        root.addView(TextView(this).apply { text = "Dashboard URL (used by the online phone D)" })
        urlInput = EditText(this).apply { setText("http://192.168.1.10:3000"); setSingleLine() }
        root.addView(urlInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(TextView(this).apply { text = "2. On phone A, send an SOS"; setPadding(0, pad, 0, 0) })
        listOf("medical" to "🚑 Medical", "police" to "🚔 Police", "fire" to "🔥 Fire", "accident" to "🚗 Accident", "other" to "🆘 Other").forEach { (k, label) ->
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { relay?.sendSos(k) ?: Toast.makeText(context, "Pick a role first", Toast.LENGTH_SHORT).show() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        logView = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE }
        root.addView(logView)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun startRole(r: String) {
        val missing = needed().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1); log("Grant permissions, then tap the role again"); return }
        relay?.stop()
        relay = RelayManager(this, r, { urlInput.text.toString() }, { lastLocation() }, { log(it) }).also { it.start() }
        status.text = "Running as phone $r"
    }

    private fun needed(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Pair<Double, Double> {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
            if (l != null) return l.latitude to l.longitude
        }
        return 18.5204 to 73.8567 // fallback demo coordinates (Pune) if no fix yet
    }

    private fun log(s: String) = runOnUiThread { logView.text = "${logView.text}\n$s" }

    override fun onDestroy() { relay?.stop(); super.onDestroy() }
}
