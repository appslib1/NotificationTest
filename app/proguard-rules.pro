# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable Play Console crash reports;
# rename the source file so the original name doesn't leak after obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager instancie WorkDatabase_Impl par réflexion via getDeclaredConstructor().
# R8 strippe le ctor par défaut sinon → NoSuchMethodException au boot (InitializationProvider).
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**