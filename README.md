# CrowdSOS prototype

## 1. Dashboard (laptop)
    cd dashboard && node server.js        # needs Node 18+, no npm install
Open http://localhost:3000. Find the laptop IP (Windows: ipconfig, Mac/Linux: ifconfig) — phones POST to http://<ip>:3000/sos.

## 2. Android app (Android Studio)
1. New Project > "Empty Views Activity", name CrowdSOS, package **com.crowdsos**, Kotlin, min SDK 26.
2. In app/build.gradle(.kts) dependencies add:  implementation("com.google.android.gms:play-services-nearby:19.3.0")
3. Copy android/app-src/com/crowdsos/*.kt into app/src/main/java/com/crowdsos/ (replace the generated MainActivity.kt).
4. Apply android/AndroidManifest-additions.xml to app/src/main/AndroidManifest.xml.
5. Sync, then run on each phone (USB debugging).

## 3. Demo
- A, B, C: airplane mode ON, then turn Bluetooth + Wi-Fi back ON (do not join any network). Location ON.
- D: joined to the same Wi-Fi as the laptop. Enter http://<laptop-ip>:3000 in the URL box.
- Tap role A/B/C/D on the matching phone (grant permissions, tap the role again), wait for "Linked with …".
- On A tap Medical -> watch B, C relay, D deliver, dashboard show "Received through Crowd Relay · 3 hops".
