import 'dart:async';

import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';
import '../utils/format.dart';
import '../widgets/glass.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Map<String, dynamic> _foreground = const <String, dynamic>{};
  Map<String, dynamic> _device = const <String, dynamic>{};
  DeviceStatus _status = const DeviceStatus();
  bool _gatewayOk = false;
  bool _hasAccessibilityAccess = false;
  Timer? _timer;
  Timer? _statusTimer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) async {
      if (_hasAccessibilityAccess) await _refreshForeground();
    });
    _statusTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) => _refreshStatus(),
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    _statusTimer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    final access = await NativeBridge.hasAccessibilityAccess();
    if (!mounted) return;
    setState(() => _hasAccessibilityAccess = access);
    if (access) await _refreshForeground();
    await Future.wait([_refreshDevice(), _refreshStatus()]);
    final reachable = await NativeBridge.gatewayReachable();
    if (!mounted) return;
    setState(() => _gatewayOk = reachable);
  }

  Future<void> _refreshForeground() async {
    final map = await NativeBridge.foregroundApp();
    if (mounted) setState(() => _foreground = map);
  }

  Future<void> _refreshDevice() async {
    final map = await NativeBridge.deviceInfo();
    if (mounted) setState(() => _device = map);
  }

  Future<void> _refreshStatus() async {
    final status = await NativeBridge.statusInfo();
    if (mounted) setState(() => _status = status);
  }

  Future<void> _execute(
    String title,
    String warning,
    Future<ActionResult> Function() action,
  ) async {
    final palette = context.glass;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: Text(warning),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: palette.danger,
              foregroundColor: Colors.white,
            ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认执行'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final result = await action();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('${result.ok ? "已执行" : "执行失败"}：${result.detail}'),
      ),
    );
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final ssid = decodeUtf16Hex(_device['ssid']?.toString() ?? '');
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        title: const Text('仪表盘'),
        actions: [
          GlassIconButton(
            icon: Icons.refresh,
            tooltip: '刷新',
            onTap: _refresh,
          ),
          const SizedBox(width: 10),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 110),
          children: [
            _buildSpeedCard(),
            const SizedBox(height: 14),
            _buildStatusCard(),
            const SizedBox(height: 14),
            _buildForegroundCard(),
            const SizedBox(height: 14),
            _buildDeviceCard(ssid),
            const SizedBox(height: 14),
            _buildActionsCard(),
          ],
        ),
      ),
    );
  }

  Widget _buildSpeedCard() {
    final palette = context.glass;
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.speed, size: 20, color: palette.accentCyan),
              const SizedBox(width: 8),
              Text(
                '实时网速',
                style: TextStyle(fontSize: 16, color: palette.textPrimary),
              ),
              const Spacer(),
              _buildGatewayChip(),
            ],
          ),
          const SizedBox(height: 16),
          StreamBuilder<Map<String, dynamic>>(
            stream: NativeBridge.speedStream,
            builder: (context, snapshot) {
              final data = snapshot.data ?? const <String, dynamic>{};
              final rx = data['rx'] is int ? data['rx'] as int : 0;
              final tx = data['tx'] is int ? data['tx'] as int : 0;
              final localPalette = context.glass;
              return Row(
                children: [
                  Expanded(
                    child: _speedValue(
                      '下载',
                      formatSpeed(rx),
                      [localPalette.accentCyan, localPalette.accentBlue],
                      Icons.arrow_downward,
                    ),
                  ),
                  Container(
                    width: 1,
                    height: 46,
                    color: localPalette.divider,
                  ),
                  Expanded(
                    child: _speedValue(
                      '上传',
                      formatSpeed(tx),
                      [localPalette.accentPurple, localPalette.accentBlue],
                      Icons.arrow_upward,
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildGatewayChip() {
    final palette = context.glass;
    final okColor = _gatewayOk ? palette.success : palette.danger;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: okColor.withValues(alpha: 0.14),
        border: Border.all(color: okColor.withValues(alpha: 0.45)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            _gatewayOk ? Icons.check_circle : Icons.error_outline,
            size: 14,
            color: okColor,
          ),
          const SizedBox(width: 5),
          Text(
            _gatewayOk ? '网关在线' : '网关不可达',
            style: TextStyle(fontSize: 11, color: okColor),
          ),
        ],
      ),
    );
  }

  Widget _speedValue(
    String label,
    String value,
    List<Color> gradient,
    IconData arrow,
  ) {
    final palette = context.glass;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(arrow, size: 13, color: palette.textMuted),
            const SizedBox(width: 4),
            Text(label, style: TextStyle(fontSize: 12, color: palette.textMuted)),
          ],
        ),
        const SizedBox(height: 6),
        GradientText(
          value,
          colors: gradient,
          style: const TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.3,
          ),
        ),
      ],
    );
  }

  /// 电量 / 信号 / 运营商状态卡，数据来自固件 json_status_info（只读）。
  Widget _buildStatusCard() {
    final palette = context.glass;
    final carrier = localizeCarrier(_device['network_name']?.toString() ?? '');
    final chargingText = _status.fullyCharged
        ? '已充满'
        : _status.charging
            ? '充电中'
            : '未充电';

    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.battery_charging_full,
                  size: 20, color: palette.accentPurple),
              const SizedBox(width: 8),
              Text(
                '设备状态',
                style: TextStyle(fontSize: 16, color: palette.textPrimary),
              ),
              const Spacer(),
              Text(
                '实时',
                style: TextStyle(fontSize: 11, color: palette.textMuted),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: _batteryBlock(palette, chargingText),
              ),
              Container(width: 1, height: 58, color: palette.divider),
              Expanded(child: _signalBlock(palette)),
              Container(width: 1, height: 58, color: palette.divider),
              Expanded(
                child: _carrierBlock(palette, carrier),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              color: palette.surfaceFill,
              border: Border.all(color: palette.divider),
            ),
            child: Row(
              children: [
                Icon(Icons.devices_other, size: 14, color: palette.textMuted),
                const SizedBox(width: 6),
                Text(
                  '在线 ${_status.wifiClients} 台',
                  style: TextStyle(fontSize: 11.5, color: palette.textPrimary),
                ),
                const Spacer(),
                Icon(Icons.schedule, size: 14, color: palette.textMuted),
                const SizedBox(width: 6),
                Text(
                  '已运行 ${_status.uptimeLabel}',
                  style: TextStyle(fontSize: 11.5, color: palette.textPrimary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _batteryBlock(GlassPalette palette, String chargingText) {
    final percent = _status.hasBattery ? _status.batteryPercent : 0;
    final color = percent <= 15
        ? palette.danger
        : percent <= 40
            ? palette.warn
            : palette.success;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.battery_full, size: 13, color: palette.textMuted),
            const SizedBox(width: 4),
            Text(
              '电量',
              style: TextStyle(fontSize: 11, color: palette.textMuted),
            ),
          ],
        ),
        const SizedBox(height: 8),
        GradientText(
          _status.hasBattery ? '$percent%' : '--',
          colors: [color, palette.accentBlue],
          style: const TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w700,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(height: 7),
        Container(
          height: 5,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(3),
            color: palette.divider,
          ),
          child: FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: _status.hasBattery ? percent / 100 : 0,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(3),
                gradient: LinearGradient(colors: [color, palette.accentBlue]),
              ),
            ),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          chargingText,
          style: TextStyle(fontSize: 10.5, color: palette.textMuted),
        ),
      ],
    );
  }

  Widget _signalBlock(GlassPalette palette) {
    final level = _status.hasSignal
        ? _status.signalQuality.clamp(0, _status.totalSignalQuality)
        : 0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.signal_cellular_alt,
                size: 13, color: palette.textMuted),
            const SizedBox(width: 4),
            Text(
              '信号',
              style: TextStyle(fontSize: 11, color: palette.textMuted),
            ),
          ],
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 20,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: List.generate(5, (i) {
              final active = i < level;
              return Container(
                width: 4,
                height: 6.0 + i * 2.8,
                margin: const EdgeInsets.only(right: 3),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(2),
                  color: active
                      ? palette.accentCyan
                      : palette.textMuted.withValues(alpha: 0.28),
                ),
              );
            }),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          '${_status.signalLabel}${_status.rssi != 0 ? " · ${_status.rssi} dBm" : ""}',
          style: TextStyle(fontSize: 10.5, color: palette.textMuted),
        ),
      ],
    );
  }

  Widget _carrierBlock(GlassPalette palette, String carrier) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.cell_wifi, size: 13, color: palette.textMuted),
            const SizedBox(width: 4),
            Text(
              '运营商',
              style: TextStyle(fontSize: 11, color: palette.textMuted),
            ),
          ],
        ),
        const SizedBox(height: 13),
        Text(
          carrier.isEmpty ? '--' : carrier,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            color: palette.textPrimary,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          '蜂窝网络',
          style: TextStyle(fontSize: 10.5, color: palette.textMuted),
        ),
      ],
    );
  }

  Widget _buildForegroundCard() {
    final palette = context.glass;
    final pkg = _foreground['package']?.toString() ?? '';
    final label = _foreground['label']?.toString() ?? '未知';
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.apps, size: 20, color: palette.accentPurple),
              const SizedBox(width: 8),
              Text(
                '前台应用',
                style: TextStyle(fontSize: 16, color: palette.textPrimary),
              ),
            ],
          ),
          const SizedBox(height: 14),
          if (!_hasAccessibilityAccess)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '开启「无障碍服务」后即可实时识别前台应用；侧载安装也可在系统设置中手动开启。',
                  style: TextStyle(color: palette.textMuted, fontSize: 13),
                ),
                const SizedBox(height: 12),
                FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: palette.accentBlue,
                    foregroundColor: Colors.white,
                  ),
                  onPressed: () async {
                    await NativeBridge.requestAccessibilityAccess();
                    await _refresh();
                  },
                  child: const Text('前往开启'),
                ),
                const SizedBox(height: 8),
                Text(
                  '在设置中找到「WiFi 自动重置」并打开开关，返回本页即会自动检测。',
                  style: TextStyle(
                    fontSize: 11,
                    color: palette.textMuted.withValues(alpha: 0.8),
                  ),
                ),
              ],
            )
          else
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  pkg.isEmpty ? '等待窗口事件' : label,
                  style: TextStyle(
                    fontSize: 18,
                    color: palette.textPrimary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 5),
                SelectableText(
                  pkg.isEmpty ? '切换一次应用即可获取包名' : pkg,
                  style: TextStyle(fontSize: 12, color: palette.textMuted),
                ),
                const SizedBox(height: 5),
                Text(
                  '更新于 ${_foreground['time'] ?? '--'}',
                  style: TextStyle(
                    fontSize: 11,
                    color: palette.textMuted.withValues(alpha: 0.7),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildDeviceCard(String ssid) {
    final palette = context.glass;
    final rows = <List<String>>[
      ['设备型号', _device['device_name']?.toString() ?? '--'],
      ['固件版本', _device['sw_version']?.toString() ?? '--'],
      ['Wi-Fi 名称', ssid.isEmpty ? '--' : ssid],
      ['运营商', localizeCarrier(_device['network_name']?.toString() ?? '')],
      ['IMEI', _device['imei']?.toString() ?? '--'],
      ['局域网 IP', _device['lan_ip']?.toString() ?? '--'],
      ['广域网 IP', _device['wan_ip']?.toString() ?? '--'],
    ];
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.router, size: 20, color: palette.accentBlue),
              const SizedBox(width: 8),
              Text(
                '设备信息',
                style: TextStyle(fontSize: 16, color: palette.textPrimary),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ...rows.map(
            (row) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(
                children: [
                  SizedBox(
                    width: 78,
                    child: Text(
                      row[0],
                      style: TextStyle(fontSize: 12, color: palette.textMuted),
                    ),
                  ),
                  Expanded(
                    child: SelectableText(
                      row[1],
                      style: TextStyle(
                        fontSize: 13,
                        color: palette.textPrimary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionsCard() {
    final palette = context.glass;
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.bolt, size: 20, color: palette.warn),
              const SizedBox(width: 8),
              Text(
                '设备操作',
                style: TextStyle(fontSize: 16, color: palette.textPrimary),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _actionTile(
            icon: Icons.restore,
            color: palette.danger,
            title: '恢复出厂设置',
            subtitle: '清空全部配置并重启，会改变 WiFi 名称与密码',
            onTap: () => _execute(
              '恢复出厂设置',
              '此操作会清空随身 WiFi 的全部配置（含管理账号、SSID 与 WiFi 密码），设备将重启，当前手机会断网。确认继续？',
              NativeBridge.factoryReset,
            ),
          ),
          _divider(),
          _actionTile(
            icon: Icons.restart_alt,
            color: palette.warn,
            title: '重启设备',
            subtitle: '保留配置，重新拨号联网',
            onTap: () => _execute(
              '重启设备',
              '设备会短暂断网后重新上线，配置不会丢失。确认继续？',
              NativeBridge.reboot,
            ),
          ),
          _divider(),
          _actionTile(
            icon: Icons.data_usage,
            color: palette.accentCyan,
            title: '重置流量统计',
            subtitle: '只清零流量计数，不改变任何配置',
            onTap: () => _execute(
              '重置流量统计',
              '仅清零设备上的流量统计计数，不会修改账号、WiFi 名称或密码。确认继续？',
              NativeBridge.resetTraffic,
            ),
          ),
        ],
      ),
    );
  }

  Widget _divider() => Container(
        height: 1,
        color: context.glass.divider,
      );

  Widget _actionTile({
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    final palette = context.glass;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 2),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: color.withValues(alpha: 0.15),
              ),
              child: Icon(icon, color: color, size: 21),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: 14,
                      color: palette.textPrimary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    style: TextStyle(fontSize: 11, color: palette.textMuted),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right,
                color: palette.textMuted, size: 18),
          ],
        ),
      ),
    );
  }
}
