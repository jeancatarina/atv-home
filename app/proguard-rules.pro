# kotlinx.serialization keeps its generated serializers through its own consumer rules.
# Keep the TV input passthrough reflection-free; nothing else is reflective.
-dontwarn org.slf4j.**

# NanoHTTPD and the YouTube player's JS bridge are reached by reflection/WebView
-keep class fi.iki.elonen.** { *; }
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
