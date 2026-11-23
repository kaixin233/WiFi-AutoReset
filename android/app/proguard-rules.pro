# WorkManager 通过反射实例化 Worker，必须保留。
-keep class com.kaixin233.wifiautoreset.AutoResetWorker { *; }

# 无障碍服务由系统按 Manifest 声明创建，显式保留避免 R8 误删。
-keep class com.kaixin233.wifiautoreset.ForegroundAccessibilityService { *; }

# 保留行号便于崩溃日志定位。
-keepattributes SourceFile,LineNumberTable
