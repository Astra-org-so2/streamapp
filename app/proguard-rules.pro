# Keep Kotlin Serialization models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.json.** { *; }

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
