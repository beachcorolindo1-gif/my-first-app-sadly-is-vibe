LAN Buddy Voice (com.lanbuddy.voice)
====================================
Lightweight low-latency Wi-Fi LAN UDP Voice Intercom for Android.

Architecture:
- Package: com.lanbuddy.voice
- MainActivity.java: UI for peer/multicast IP, UDP port (50005), and runtime RECORD_AUDIO permission.
- VoiceService.java: Foreground service running 16kHz 16-bit Mono PCM AudioRecord -> DatagramSocket TX and DatagramSocket RX -> AudioTrack.
- Zero external libraries required: builds cleanly with Android SDK 34 & Gradle 8.5.

How to get the installable APK on your phone:
1. Cloud Build (No Android Studio required):
   Upload the exported ZIP to a free GitHub repository. The included .github/workflows/build-apk.yml automatically compiles and signs app-debug.apk in ~90 seconds under the "Actions" tab.
2. Local Build (Android Studio / Command Line):
   Run ./build-apk.sh or "gradle assembleDebug" and transfer app/build/outputs/apk/debug/app-debug.apk to your phone.
3. Instant Web/Phone Transceiver:
   Open this web app directly on your phone browser and tap "Add to Home Screen" to use the real-time voice transceiver right away.
