# Shizuku API & Provider
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class * implements rikka.shizuku.Shizuku$* { *; }

# AIDL Interfaces and UserService
-keep interface com.example.IXenoTimeService { *; }
-keep class com.example.IXenoTimeService$* { *; }
-keep class com.example.service.XenoTimeUserService { *; }
-keep public class * extends com.example.IXenoTimeService$Stub {
    public <init>(...);
    public <init>();
}
