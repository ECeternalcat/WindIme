# Keep RimeBridge native bindings
# Keep the vendored com.osfans.trime.core JNI surface intact: the prebuilt
# librime_jni.so resolves these classes by exact name (FindClass) and calls
# their constructors / native methods by signature. Renaming or stripping
# any of them breaks System.loadLibrary / JNI_OnLoad.
-keep class com.osfans.trime.core.** { *; }

# Keep InputAction enum (reflection-looked-up from JSON)
-keep class com.garaho.ime.keymap.InputAction { *; }
-keepclassmembers enum com.garaho.ime.keymap.InputAction {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep keymap config POJOs (parsed via reflection-style setters)
-keep class com.garaho.ime.keymap.** { *; }
