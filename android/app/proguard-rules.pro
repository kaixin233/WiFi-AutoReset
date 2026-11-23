# BroadcastReceiver / Service / Activity 由系统在 Manifest 中按类名实例化，必须保留。
-keep class com.kaixin233.wifiautoreset.AlarmReceiver { *; }
-keep class com.kaixin233.wifiautoreset.BootReceiver { *; }
-keep class com.kaixin233.wifiautoreset.ForegroundAccessibilityService { *; }
-keep class com.kaixin233.wifiautoreset.MainActivity { *; }

# 保留行号便于崩溃日志定位。
-keepattributes SourceFile,LineNumberTable
