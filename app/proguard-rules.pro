# Hilt
-keep class dagger.hilt.android.internal.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Room { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter { *; }

# Coil
-keep class coil.** { *; }

# Sardine-Android
-keep class com.github.thegrizzlylabs.sardine.** { *; }

# Keep all classes that are part of the app package to prevent over-optimization
-keep class com.asinosoft.photogallery.** { *; }

# Fix for XmlPullParser dependency issue
-dontwarn org.xmlpull.v1.**
