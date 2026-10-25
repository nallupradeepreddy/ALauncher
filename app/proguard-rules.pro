# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/pradeepreddynallu/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# Keep Room generated implementations and entity classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Keep Timber
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }

# Print unused code report during R8 release builds
-printusage build/outputs/mapping/release/unused.txt

