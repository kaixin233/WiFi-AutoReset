# WiFi 自动重置

面向小米手机（Android 16、64 位）定制的随身 WiFi 自动化控制 App。

**v2.2.0 起为完全零依赖的纯原生 Android 应用**：无 Flutter、无 AppCompat / Material / Fragment / WorkManager，全部使用系统原生控件（`Button`、`Switch`、`RadioButton`）与 `AlarmManager`；标题栏与底栏手写实现，明暗外观跟随系统，适配 Android 16 强制全屏（edge-to-edge）。管理页交给系统浏览器打开，App 内不再内嵌 WebView。

## 功能

- **实时网速**：每秒读取系统总收发字节差，显示下载 / 上传速率。仅读本地计数器、不走网络；且仅在仪表盘可见时采样，离开页面立即停止，后台零开销。
- **设备操作**：恢复出厂设置、重启设备，均带二次确认，点击即真实执行（无演练模式）。
- **打开管理页面**：仪表盘按钮唤起系统浏览器访问 `http://192.168.1.1`（账号密码 `admin / admin` 需在浏览器中输入）。
- **智能定时自动化**：系统 `AlarmManager` 按自定义周期执行恢复出厂（5 ~ 1440 分钟，默认 60 分钟），提供快捷档位与自定义输入，倒计时、下次执行绝对时间与排程严格对齐；开机后由 `BootReceiver` 自动恢复任务。
  - **智能调度（默认开启）**：到点不采样、不 sleep，仅读系统流量计数器，计算「自上次重置以来的周期平均网速」——低于 **500 KB/s** 视为空闲/疑似被云控，立即重置；达到 500 KB/s 视为繁忙，每 5 分钟延缓一次，最多 6 次（30 分钟）后强制执行。基于实测「设备约 90 分钟后再次被云控」，用周期平均判断已足够，无需精确到秒，开销几乎为零。可在自动化页面关闭恢复「到点准时执行」。
  - **执行结果通知**：定时执行成功 / 失败 / 强制执行时发送系统通知（Android 13+ 需授予通知权限）；延缓仅在日志记录，避免频繁打扰。
- **执行日志**：所有手动与定时操作写入本地 `execution_log.jsonl`，可随时关闭记录、查看和清空；智能调度的每次判断/延缓决策也会单独记录。

> **历史精简记录**
> - v2.4.0：深色模式实时跟随系统；修复后台广播 goAsync 超时隐患（改用 WakeLock 保底）；智能调度改为零开销的周期平均网速判断（去掉 2 秒 sleep 采样）；新增执行结果通知与下次执行绝对时间；P3 代码质量清理。
> - v2.3.0：移除「前台应用」检测与无障碍服务（系统常驻服务耗电、权限敏感），后台开销进一步降低；修复「保存设置」在 Android 12+ 因缺少精确闹钟权限导致的闪退；新增智能调度策略。
> - v2.2.0：移除会持续发网络请求的设备状态轮询（原设备状态 / 设备信息框、网关检测）与重置流量统计按钮。这些信息仍可在系统浏览器的管理页查看。

## 后台开销说明

本 App 在后台几乎零开销：

| 组件 | 运行方式 | 后台开销 |
|---|---|---|
| 网速采样 | `SpeedMonitor` 仅仪表盘 `onShow` 时启动、`onHide` 停止 | 后台为 0 |
| 前台应用检测 | v2.3.0 已删除（原无障碍常驻服务） | 已移除 |
| 设备状态轮询 | v2.2.0 已删除 | 已移除 |
| 定时任务 | `AlarmManager` 系统级调度，进程不常驻 | 仅到点短暂唤醒 |
| 智能检测 | 仅读系统累计流量计数器算周期均值，无 sleep、无网络请求 | 仅到点两次纳秒级读数 |

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

**重要**：防缓存时间戳必须直接拼在 `file` 参数值末尾（如 `file=json_status_info1763...`）。固件会把独立的 `&_t=` 查询参数当成未知 action，返回 `{"result":"1","msg":"api not found"}`（HTTP 200），这也是 v2.0.1 ~ v2.1.1 设备功能失效的根因。

恢复出厂设置对应固件的 `json_device_restore_factory`，执行后设备会重启并清空配置。

## 构建与覆盖安装

GitHub Actions 在 push 到 `main` / `master` 或手动触发时构建，产物在 Actions 的 Artifacts 中下载；手动触发时同时发布 GitHub Release。

本地构建需要 JDK 17、Android SDK 36：

```bash
gradle -p android :app:assembleRelease
```

产物：`android/app/build/outputs/apk/release/app-release.apk`

**覆盖安装条件**：包名 `com.kaixin233.wifiautoreset` 不变、签名证书不变、`versionCode` 递增（当前 **2.4.0+14**，versionCode = 14）。

从 v1.0.3 起 Release 使用仓库内固定的 `android/app/release.p12`。v1.0.3 及以后版本可直接覆盖升级到 v2.4.0，设置与日志数据保留。release 构建开启 R8 代码压缩与资源压缩，并只保留中文资源。v1.0.1 / v1.0.2 使用早期 CI 自动生成的密钥，证书不同，需先卸载一次。

## 权限

- 网络访问、Wi-Fi 状态、WakeLock：安装即授予（WakeLock 仅用于保证后台定时任务执行完整）。
- **精确闹钟**（`SCHEDULE_EXACT_ALARM`）：Android 12+ 用于精确定时；未授予时自动降级为普通闹钟（可能延迟数分钟），不影响功能、不会闪退。
- **通知**（`POST_NOTIFICATIONS`）：Android 13+ 首次进入时申请，用于执行结果提示；拒绝后仅写日志、不影响功能。
- 前台应用 / 无障碍权限已于 v2.3.0 移除，不再需要。
- Android 16 后台任务受省电策略限制，定时执行可能延迟数分钟。

## 风险提示

1. 「恢复出厂设置」会清空随身 WiFi 的账号、SSID、WiFi 密码等全部配置并重启，手机会立即断网。请仅在自己拥有或明确授权的设备上使用。
2. 演练模式已移除，点击操作按钮并二次确认后即真实执行。
3. 定时任务在设备断网后会失败并写入日志，需恢复连接后手动重试。
4. 周期最短 5 分钟，建议不低于 30 分钟。
5. 智能调度以 500 KB/s 为忙闲分界、用整周期平均网速判断：若使用环境长期高于该速率，任务会频繁延缓（最多 30 分钟后强制执行）；设备实测约 90 分钟后再次被云控，周期建议设为 90 分钟左右可在「及时解控」与「少打断」间取得平衡。可在自动化页面关闭智能调度。
6. 管理页由系统浏览器打开，首次需自行输入 `admin / admin` 登录；浏览器与 App 的网络权限相互独立，请确保已连接该随身 WiFi。

## 项目结构

```
android/app/src/main/kotlin/com/kaixin233/wifiautoreset/
  MainActivity.kt                    原生 Activity、手写标题栏/底栏与页面切换
  PageController.kt                  页面控制器契约
  DashboardPage.kt                   仪表盘（实时网速 / 设备操作）
  AutomationPage.kt                  定时自动化 + 智能调度开关
  LogsPage.kt                        执行日志
  AlarmScheduler.kt                  AlarmManager 周期调度（含权限降级）
  AlarmReceiver.kt                   定时执行（WakeLock 保底 + 智能判断 + 延缓重试）
  ResetSnapshot.kt                   重置基准快照（累计流量 + 时间戳）
  IdleDetector.kt                    零开销忙闲判断（周期均值 + 屏幕状态）
  Notifier.kt                        执行结果系统通知
  BootReceiver.kt                    开机恢复定时任务
  Utils.kt                           线程/格式化工具
  MifiClient.kt                      Digest 认证与 CGI 请求
  SpeedMonitor.kt                    网速采样
  AppLog.kt                          日志持久化
android/app/src/main/res/layout/     三个页面的原生布局 + activity_main
android/app/release.p12              固定发布签名密钥
```
