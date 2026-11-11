# The generated bindings reach libspieltisch_core.so through JNA, which binds an
# interface's methods to native symbols by name and reads Structure fields
# reflectively. R8 sees none of that, so JNA and the bindings keep their names.
# JNA's desktop-only AWT helpers are absent on Android.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.spieltisch_core.** { *; }
-dontwarn java.awt.**
