# Keep RimeBridge native bindings
-keep class com.garaho.ime.rime.RimeBridge { *; }

# Keep InputAction enum (reflection-looked-up from JSON)
-keep class com.garaho.ime.keymap.InputAction { *; }
-keepclassmembers enum com.garaho.ime.keymap.InputAction {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep keymap config POJOs (parsed via reflection-style setters)
-keep class com.garaho.ime.keymap.** { *; }
