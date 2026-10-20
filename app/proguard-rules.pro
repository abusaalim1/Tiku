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

# ROUND 5, ITEM 6 — PLAY PROTECT SIGNAL EXPERIMENT: obfuscation OFF, minify
# (shrinking + optimization) and shrinkResources stay ON. Class/member names
# remain human-readable throughout the APK. Rationale: Google's own riskware
# guidance lists obfuscation as a signal; this is the one build-side
# differentiator with documentary support. EXPERIMENT — not a promised fix.
-dontobfuscate
