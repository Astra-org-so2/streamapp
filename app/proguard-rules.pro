# KotlinX Serialization specific rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable class *;
}

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.Dao *;
}

# Keep WebRTC and MediaCodec classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Hilt / Dagger generated classes
-keep class * extends com.google.dagger.hilt.** { *; }
