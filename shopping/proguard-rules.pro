# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep shopping module classes
-keep class com.susking.ephone_s.shopping.** { *; }

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}