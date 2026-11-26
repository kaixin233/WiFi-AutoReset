# WiFi 自动重置

面向小米手机（Android 16、64 位）的随身 WiFi 自动化控制 App。**v2.2.0 起为零依赖纯原生实现**：无 Flutter、无 AppCompat / Material / Fragment / WorkManager，仅用系统原生控件与 `AlarmManager`；标题栏、底栏手写，明暗外观跟随系统，适配 Android 16 强制全屏。管理页交给系统浏览器，App 内不嵌 WebView。

## 功能

- **实时网速**：读取系统总收发字节差，显示上下行速率。仅在仪表盘可见时采样，后台零开销。
- **设备操作**：恢复出厂设置、重启设备，均带二次确认，点击即真实执行。
- **打开管理页面**：唤起系统浏览器访问 `http://192.168.1.1`（需输入 `admin / admin`）。
- **定时自动重置**：按自定义周期（5 ~ 1440 分钟）自动恢复出厂。倒计时从**最近一次恢复出厂的时间**开始；手动重置后自动重新计时。开机由 `BootReceiver` 恢复任务，执行结果发系统通知。
- **后台保活**：启用定时任务时启动前台守护服务（低优先级静默通知），防止 MIUI/HyperOS 划掉卡片时清除闹钟；自动化页提供「开启自启动」「设置省电策略」一键跳转。服务无轮询，几乎不耗电。
- **执行日志**：手动与定时操作写入本地 `execution_log.jsonl`，每条只显示**时间 · 行为 · 结果**（成功绿 / 失败红），不展示底层响应细节，可关闭记录、查看和清空。

## 定时规则

- 下次执行时间 = 最近一次重置时间 + 周期。周期档位：15 分 / 30 分 / 1 小时 / 2 小时，或自定义分钟数。
- 每次重置（手动或定时）成功后刷新计时起点，形成链式周期。
- 首次启用、修改周期或基准过期时，从当前时刻重新计时，不会立刻触发重置。
- 系统省电策略可能让任务延迟数分钟。

## 后台开销与保活

- 网速仅仪表盘可见时采样；前台应用检测、设备状态轮询均已移除。
- 定时任务由系统 `AlarmManager` 调度，到点由 `AlarmReceiver` 短暂唤醒执行。
- 启用定时任务时会启动**前台守护服务**：一条低优先级静默通知把进程维持在前台优先级。这是对抗 MIUI/HyperOS「划卡即 force-stop 并清除闹钟」的关键——服务本身无任何轮询/定时器，闲置时几乎零耗电。任务关闭时服务自动停止，通知消失。

> 若任务仍被系统清理：进入自动化页 →「后台保活设置」，开启**自启动**（含关联启动）并将**省电策略设为无限制**。也可在最近任务界面把应用卡片下拉锁定，锁定后不会被划掉。

## 实测设备信息

| 项目 | 值 |
|---|---|
| 固件 | LTE Wireless Router / mifi（Marvell），型号 LBF1A |
| 认证 | HTTP Digest，realm `Highwmg`，`/login.cgi` 取挑战 |
| 操作接口 | `/xml_action.cgi?method=get&module=duster&file=json_<名称><时间戳>` |

**重要**：防缓存时间戳必须拼在 `file` 参数值末尾（如 `file=json_status_info1763...`）。独立的 `&_t=` 会被固件当成未知 action，返回 `api not found`（HTTP 200）——这是早期版本设备功能失效的根因。恢复出厂对应 `json_device_restore_factory`。

## 构建与覆盖安装

GitHub Actions 在 push 到 `main` / `master` 或手动触发时构建；手动触发同时发布 Release。

本地构建需 JDK 17 + Android SDK 36：

```bash
gradle -p android :app:assembleRelease
```

产物：`android/app/build/outputs/apk/release/app-release.apk`

**覆盖安装**：包名 `com.kaixin233.wifiautoreset` 与签名证书不变，`versionCode` 递增（当前 **2.6.0+16**）。v1.0.3 及以后版本使用仓库内固定签名，可直接覆盖升级，设置与日志保留；v1.0.1 / v1.0.2 证书不同，需先卸载一次。

## 权限

- 网络访问、WakeLock、前台服务（`FOREGROUND_SERVICE`）：安装即授予。
- **精确闹钟**（`SCHEDULE_EXACT_ALARM`）：Android 12+ 用于精确定时；未授予时降级为普通闹钟（可能延迟数分钟），不会闪退。
- **通知**（`POST_NOTIFICATIONS`）：Android 13+ 首次进入申请，用于执行结果提示。前台守护通知不受该权限影响，始终显示在通知栏/任务管理器。
- 自启动、省电豁免需在系统设置中手动开启（自动化页提供跳转入口），无法由 App 自行授予。
- 前台应用 / 无障碍权限已移除，不再需要。

## 风险提示

1. 恢复出厂会清空 SSID、WiFi 密码和管理账号并重启，手机立刻断网。请仅在自己拥有或明确授权的设备上使用。
2. 点击操作按钮并二次确认后即真实执行，无演练模式。
3. 定时任务在设备断网后会失败并写入日志，需恢复连接后手动重试（或等待下一周期）。
4. 周期最短 5 分钟，建议不低于 30 分钟。设备实测约 90 分钟后再次被云控，可据此设定周期。
5. 管理页由系统浏览器打开，首次需自行输入 `admin / admin` 登录。

## 项目结构

```
android/app/src/main/kotlin/com/kaixin233/wifiautoreset/
  MainActivity.kt       原生 Activity、手写标题栏/底栏与页面切换
  PageController.kt     页面控制器契约
  DashboardPage.kt      仪表盘（实时网速 / 设备操作）
  AutomationPage.kt     定时自动化（周期 / 开关 / 倒计时）
  LogsPage.kt           执行日志（卡片式展示）
  AlarmScheduler.kt     AlarmManager 周期调度（以重置时间为基准 + 权限降级）
  AlarmReceiver.kt      定时执行（WakeLock 保底）
  KeepAliveService.kt   前台守护服务（维持进程优先级，防止划卡清闹钟）
  BootGuard.kt          后台保活引导（跳转自启动 / 省电设置）
  Notifier.kt           执行结果通知 + 前台守护静默通知
  BootReceiver.kt       开机恢复定时任务与守护服务
  Utils.kt              线程 / 格式化工具
  MifiClient.kt         Digest 认证与 CGI 请求
  SpeedMonitor.kt       网速采样
  AppLog.kt             日志持久化
android/app/release.p12   固定发布签名密钥
```
