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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep ML Kit Pose Detection classes
-keep class com.google.mlkit.vision.pose.** { *; }
-keep class com.google.mlkit.vision.pose.internal.** { *; }
-keep class com.google.mlkit.common.sdkinternal.** { *; }

# Keep MediaPipe framework
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep model classes
-keep class * implements com.google.mlkit.common.model.RemoteModel {
    *;
}
# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

# Keep model classes and their fields
-keepclasseswithmembers class * {
    @com.google.mlkit.common.model.* *;
}

# Keep the pose detection classes
-keep class com.google.mlkit.vision.pose.** { *; }
-keep class com.google.mlkit.vision.pose.internal.** { *; }

# Keep TensorFlow Lite operations
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**