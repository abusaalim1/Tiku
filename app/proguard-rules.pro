# Tiku release keep rules (round 3, item 6 — R8 minify + shrinkResources).
# The app package is small, so it is kept whole; R8 strips dead third-party
# bytecode and unused resources. Firebase / Credential Manager / Coil ship
# their own consumer rules; the Roborazzi suite is the smoke check.

-keep class org.webrtc.** { *; }
-keep class com.tiku.app.** { *; }
-keep class androidx.credentials.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

-dontwarn org.webrtc.**
-dontwarn io.getstream.**
