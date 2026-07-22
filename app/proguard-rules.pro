-keepattributes *Annotation*

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.kaappi.studio.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# WebView JavaScript interface
-keepclassmembers class com.kaappi.studio.bridge.KaappiBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Chicory WASM runtime
-keep class com.dylibso.chicory.** { *; }
-keep class com.google.common.jimfs.** { *; }
