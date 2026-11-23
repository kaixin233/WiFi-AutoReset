# WiFi 自动重置

面向小米手机（Android 16、64 位）定制的随身 WiFi 自动化控制 App。

**v2.1.0 起为完全零依赖的纯原生 Android 应用**：无 Flutter、无 AppCompat / Material / Fragment / WorkManager，全部使用系统原生控件（`Button`、`Switch`、`RadioButton`、`WebView`）与 `AlarmManager`；标题栏与底栏手写实现，明暗外观跟随系统，适配 Android 16 强制全屏（edge-to-edge）。APK 体积相比 Material 版再次大幅下降。

## 功能

- **实时网速**：每秒采样系统总收发字节差，显示下载 / 上传速率。
- **设备状态**：每 10 秒读取固件只读接口 `json_status_info`，展示电量、充电状态、蜂窝信号与 RSSI、运营商、在线设备数、运行时长。
- **运营商中文化**：`CHN UNICOM` 等自动显示为「中国联通 / 中国移动 / 中国电信」。
- **前台应用**：通过无障碍服务监听窗口事件实时获取前台包名与名称，每 3 秒刷新；侧载的未知应用也可在系统设置中手动开启。
- **WebView 管理页**：自动加载 `http://192.168.1.1` 并以 `admin / admin` 自动登录。
- **设备操作**：恢复出厂设置、重启设备、重置流量统计，均带二次确认，点击即真实执行（无演练模式）。
- **定时自动化**：系统 `AlarmManager`（`setExactAndAllowWhileIdle`）按自定义周期执行恢复出厂（5 ~ 1440 分钟，默认 60 分钟），提供快捷档位与自定义输入，倒计时与排程严格对齐；开机后由 `BootReceiver` 自动恢复任务。
- **执行日志**：所有手动与定时操作写入本地 `execution_log.jsonl`，可随时关闭记录、查看和清空。

## 实测设备信息

从本机（192.168.1.200）访问网关 192.168.1.1 读取：

| 项目 | 值 |
|---|---|
| 固件类型 | LTE Wireless Router / mifi（Marvell 方案） |
| 设备型号 | LBF1A |
| 固件版本 | LBF1AKAS00873_N12_LED_MQTT_V001 |
| 运营商 | CHN UNICOM（显示为「中国联通」） |
| WiFi 名称 | MiFi-281F（固件以 UTF-16 code unit 十六进制返回） |
| 认证方式 | HTTP Digest，realm `Highwmg`，`/login.cgi` 获取挑战 |
| 操作接口 | `/xml_action.cgi?method=get&module=duster&file=json_<名称>&_t=<时间戳>`（时间戳为独立参数） |

恢复出厂设置对应固件的 `json_device_restore_factory`，执行后设备会重启并清空配置。若只想清零流量计数，请用「重置流量统计」（`json_statistics_set`）。

## 构建与覆盖安装

GitHub Actions 在 push 到 `main` / `master` 或手动触发时构建，产物在 Actions 的 Artifacts 中下载；手动触发时同时发布 GitHub Release。

本地构建需要 JDK 17、Android SDK 36：

```bash
gradle -p android :app:assembleRelease
```

产物：`android/app/build/outputs/apk/release/app-release.apk`

**覆盖安装条件**：包名 `com.kaixin233.wifiautoreset` 不变、签名证书不变、`versionCode` 递增（当前 **2.1.0+9**，versionCode = 9）。

从 v1.0.3 起 Release 使用仓库内固定的 `android/app/release.p12`。v1.0.3 及以后版本可直接覆盖升级到 v2.1.0，设置与日志数据保留。release 构建开启 R8 代码压缩与资源压缩，并只保留中文资源。v1.0.1 / v1.0.2 使用早期 CI 自动生成的密钥，证书不同，需先卸载一次。

## 权限

- 网络访问、Wi-Fi 状态：安装即授予。
- **无障碍服务**（`BIND_ACCESSIBILITY_SERVICE`）：在系统设置中手动开启「WiFi 自动重置」后才能读取前台包名；只使用窗口事件的 `packageName`，不读取窗口内容、不上传。
- Android 16 后台任务受省电策略限制，定时执行可能延迟数分钟。

## 风险提示

1. 「恢复出厂设置」会清空随身 WiFi 的账号、SSID、WiFi 密码等全部配置并重启，手机会立即断网。请仅在自己拥有或明确授权的设备上使用。
2. 演练模式已移除，点击操作按钮并二次确认后即真实执行。
3. 定时任务在设备断网后会失败并写入日志，需恢复连接后手动重试。
4. 周期最短 5 分钟，建议不低于 30 分钟。

## 项目结构

```
android/app/src/main/kotlin/com/kaixin233/wifiautoreset/
  MainActivity.kt                    原生 Activity、手写标题栏/底栏与页面切换
  PageController.kt                  页面控制器契约
  DashboardPage.kt                   仪表盘
  AutomationPage.kt                  定时自动化
  LogsPage.kt                        执行日志
  WebPage.kt                         WebView 管理页
  AlarmScheduler.kt                  AlarmManager 周期调度
  AlarmReceiver.kt                   定时执行恢复出厂
  BootReceiver.kt                    开机恢复定时任务
  Utils.kt                           线程/JSON/格式化工具
  MifiClient.kt                      Digest 认证与 CGI 请求
  SpeedMonitor.kt                    网速采样
  ForegroundAppDetector.kt           无障碍权限与前台包名缓存
  ForegroundAccessibilityService.kt  实时窗口事件监听
  AppLog.kt                          日志持久化
android/app/src/main/res/layout/     四个页面的原生布局 + activity_main
android/app/release.p12              固定发布签名密钥
```
