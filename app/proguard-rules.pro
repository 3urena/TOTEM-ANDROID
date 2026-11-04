-keepclassmembers class xyz.totemai.app.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class xyz.totemai.app.web.SessionBridge { *; }
-keep class xyz.totemai.app.TotemApp { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
