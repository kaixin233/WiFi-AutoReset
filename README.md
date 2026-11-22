# WiFi 自动重置

面向小米手机（Android 16、64 位）定制的随身 WiFi 自动化控制 App。基于 Flutter + Kotlin 原生层，通过设备固件自身的 HTTP Digest 接口完成自动化，WebView 仅用于可视化查看管理页。UI 采用液体玻璃设计：毛玻璃卡片、折射高光描边、流动光晕背景，底栏为酷安风格液态玻璃 Dock。

## 功能

- **实时网速**：每秒采样系统总收发字节差，显示下载 / 上传速率。
- **设备状态卡**：每 10 秒读取固件只读接口 `json_status_info`，展示电量百分比与充电状态、蜂窝信号等级与 RSSI、运营商、在线设备数、运行时长。
- **前台应用**：通过 `UsageStatsManager` 读取当前前台应用包名与名称，每 3 秒刷新。
- **WebView 管理页**：自动加载 `http://192.168.1.1` 并以 `admin / admin` 自动登录。
- **设备操作**：恢复出厂设置、重启设备、重置流量统计，均带二次确认，点击即真实执行（演练模式已移除）。
- **定时自动化**：`WorkManager` 按自定义周期执行恢复出厂（5 ~ 1440 分钟，默认 60 分钟），执行后自动排下一次；界面提供快捷档位与自定义分钟输入，并显示与原生排程严格对齐的倒计时。
- **执行日志**：所有手动与定时操作写入本地 `execution_log.jsonl`，在「日志」页查看，最多保留 500 条。
- **浅色 / 深色跟随系统**：明暗两套色板，随系统外观自动切换，玻璃卡片与文字对比度均经过适配。
- **液态玻璃底栏**：强模糊毛玻璃 Dock、渐变高光描边、弹簧滑动胶囊指示器、选中图标缩放与点按涟漪。

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

状态卡使用只读接口 `json_status_info`，实测字段：

```json
{
  "battery_percent": "60",
  "battery_charge": "0",
  "battery_charging": "0",
  "signal_quality": "5",
  "total_signal_quality": "5",
  "rssi": "-77",
  "wifi_clients_num": "2",
  "run_seconds": "2482"
}
```

## 构建与覆盖安装

GitHub Actions 在 push 到 `main` / `master` 或手动触发时自动构建，产物在 Actions 页面的 Artifacts 中下载 `wifi-auto-reset-release`，即 `app-release.apk`。手动触发时同时发布 GitHub Release。

本地构建需要 Flutter 3.44.0、JDK 17、Android SDK 36：

```bash
flutter pub get
flutter test
flutter build apk --release --target-platform android-arm64
```

**覆盖安装条件**：包名 `com.kaixin233.wifiautoreset` 不变、签名证书不变、`versionCode` 递增（当前 `1.0.3+4`，versionCode = 4）。

从 v1.0.3 起，Release 使用仓库内固定的 `android/app/release.p12` 签名（PKCS12，别名 `wifiautoreset`），CI 每次构建都使用同一证书，之后的版本可直接覆盖安装且保留 SharedPreferences 与日志数据。

> 注意：v1.0.1 / v1.0.2 使用 CI 自动生成的 debug 密钥，两次构建证书不同，无法互相覆盖。从旧版升级到 v1.0.3 需要**先卸载一次**；这是 Android 签名机制的硬性限制，之后不再需要。

## 权限

- 网络访问、Wi-Fi 状态：安装即授予。
- **使用情况访问**（`PACKAGE_USAGE_STATS`）：需用户在系统设置中手动开启，否则无法读取前台包名；App 内有「前往授权」按钮。
- Android 16 上后台任务受系统省电策略限制，定时执行可能延迟数分钟，无法保证毫秒级准点。

## 风险提示

1. 「恢复出厂设置」会清空随身 WiFi 的账号、SSID、WiFi 密码等全部配置并重启，手机会立即断网。请仅在自己拥有或明确授权的设备上使用。
2. 演练模式已移除，点击操作按钮并二次确认后即真实执行，请谨慎操作。
3. 定时任务在设备断网后会执行失败并写入日志，需要恢复连接后手动重试。
4. 自定义周期最短 5 分钟；过短周期会频繁断网并加速固件写入，建议不低于 30 分钟。

## 项目结构

```
lib/
  main.dart                    应用入口、主题配置与液态玻璃底栏
  models/models.dart           日志 / 操作结果 / 定时状态 / 设备状态模型
  services/native_bridge.dart  MethodChannel / EventChannel 封装
  utils/format.dart            速率格式化与 SSID 解码
  widgets/glass.dart           双色板与液体玻璃组件库（光晕/卡片/高光/Dock）
  screens/                     仪表盘、自动化、日志、WebView 四页
android/app/src/main/kotlin/com/kaixin233/wifiautoreset/
  MifiClient.kt                Digest 认证与 CGI 请求（含只读 status_info）
  NativeBridge.kt              MethodChannel 分发与后台线程
  SpeedMonitor.kt              网速采样
  ForegroundAppDetector.kt     前台应用检测
  AutoResetWorker.kt           自定义周期定时任务
  AppLog.kt                    日志持久化
test/native_bridge_test.dart   合约测试
```
