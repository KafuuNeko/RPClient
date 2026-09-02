# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# CoreViewModel discovers and invokes these methods through Kotlin reflection.
# Suspend functions also require their generic signature and Continuation type to
# remain recognizable to kotlin-reflect after R8 full-mode optimization.
-keepattributes RuntimeVisibleAnnotations,Signature
-keep class kotlin.coroutines.Continuation
-keep,allowoptimization,allowobfuscation class * extends me.kafuuneko.rpclient.libs.core.CoreViewModel
-keep @interface me.kafuuneko.rpclient.libs.core.UiIntentObserver
-keepclassmembers class * {
    @me.kafuuneko.rpclient.libs.core.UiIntentObserver <methods>;
}

# CoreViewModel also uses each intent's exact runtime KClass as the dispatch key.
# Keep distinct UiIntent classes so R8 cannot horizontally merge structurally
# identical data objects and make one event invoke multiple observers.
-keep,allowobfuscation class me.kafuuneko.rpclient.feature.**UiIntent*

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
