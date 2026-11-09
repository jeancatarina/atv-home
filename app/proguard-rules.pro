# kotlinx.serialization keeps its generated serializers through its own consumer rules.
# Keep the TV input passthrough reflection-free; nothing else is reflective.
-dontwarn org.slf4j.**
