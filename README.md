# WiFi 自动重置

面向小米手机（Android 16、64 位）定制的随身 WiFi 自动化控制 App。基于 Flutter + Kotlin 原生层，通过设备固件自身的 HTTP Digest 接口完成自动化，WebView 仅用于可视化查看管理页。

## 功能

- **实时网速**：每秒采样系统总收发字节差，显示下载 / 上传速率。
- **前台应用**：通过 `UsageStatsManager` 读取当前前台应用包名与名称，每 3 秒刷新。
- **WebView 管理页**：自动加载 `http://192.168.1.1` 并以 `admin / admin` 自动登录。
- **设备操作**：恢复出厂设置、重启设备、重置流量统计，均带二次确认。
- **定时自动化**：`WorkManager` 每小时第 0 分第 5 秒执行恢复出厂，执行后自动排下一次。
- **执行日志**：所有手动与定时操作写入本地 `execution_log.jsonl`，在「日志」页查看，最多保留 500 条。
- **演练模式**：默认开启，只记录日志不发送真实请求，确认无误后再关闭。

## 实测设备信息

从本机（192.168.1.200）访问网关 192.168.1.1 读取：

| 项目 | 值 |
|---|---|
| 固件类型 | LTE Wireless Router / mifi（Marvell 方案） |
| 设备型号 | LBF1A |
| 固件版本 | LBF1AKAS00873_N12_LED_MQTT_V001 |
| 运营商 | CHN UNICOM |
| WiFi 名称 | MiFi-281F（固件以 UTF-16 code unit 十六进制返回） |
| 认证方式 | HTTP Digest，realm `Highwmg`，`/login.cgi` 获取挑战 |
| 操作接口 | `/xml_action.cgi?method=get&module=duster&file=json_<名称><时间戳>` |

恢复出厂设置对应固件的 `json_device_restore_factory`，就是管理页「恢复出厂设置」按钮调用的同一接口；执行后设备会重启并清空配置。若只想清零流量计数而不改任何配置，请用「重置流量统计」（`json_statistics_set`）。

## 构建

GitHub Actions 在 push 到 `main` / `master` 或手动触发时自动构建，产物在 Actions 页面的 Artifacts 中下载 `wifi-auto-reset-release`，即 `app-release.apk`。

本地构建需要 Flutter 3.44.0、JDK 17、Android SDK 36：

```bash
flutter pub get
flutter test
flutter build apk --release
```

## 权限

- 网络访问、Wi-Fi 状态：安装即授予。
- **使用情况访问**（`PACKAGE_USAGE_STATS`）：需用户在系统设置中手动开启，否则无法读取前台包名；App 内有「前往授权」按钮。
- Android 16 上后台任务受系统省电策略限制，整点执行可能延迟数分钟，无法保证毫秒级准点。

## 风险提示

1. 真机模式下「恢复出厂设置」会清空随身 WiFi 的账号、SSID、WiFi 密码等全部配置并重启，手机会立即断网。请仅在自己拥有或明确授权的设备上使用。
2. 首次使用请先保持演练模式，确认日志内容符合预期后再关闭演练。
3. 定时任务在设备断网后会执行失败并写入日志，需要恢复连接后手动重试。

## 项目结构

```
lib/
  main.dart                    应用入口与底部导航
  models/models.dart           日志 / 操作结果 / 定时状态模型
  services/native_bridge.dart  MethodChannel / EventChannel 封装
  utils/format.dart            速率格式化与 SSID 解码
  screens/                     仪表盘、自动化、日志、WebView 四页
android/app/src/main/kotlin/com/kaixin233/wifiautoreset/
  MifiClient.kt                Digest 认证与 CGI 请求
  NativeBridge.kt              MethodChannel 分发与后台线程
  SpeedMonitor.kt              网速采样
  ForegroundAppDetector.kt     前台应用检测
  AutoResetWorker.kt           整点定时任务
  AppLog.kt                    日志持久化
test/native_bridge_test.dart   合约测试
```
