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
  bool _gatewayOk = false;
  bool _hasUsageAccess = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) async {
      if (_hasUsageAccess) await _refreshForeground();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    final access = await NativeBridge.hasUsageAccess();
    if (!mounted) return;
    setState(() => _hasUsageAccess = access);
    if (access) await _refreshForeground();
    await _refreshDevice();
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

  Future<void> _execute(
    String title,
    String warning,
    Future<ActionResult> Function() action,
  ) async {
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
              backgroundColor: GlassTokens.danger,
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
        content: Text(
          '${result.ok ? "已执行" : "执行失败"}：${result.detail}',
        ),
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
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.speed, size: 20, color: GlassTokens.accentCyan),
              const SizedBox(width: 8),
              const Text(
                '实时网速',
                style: TextStyle(fontSize: 16, color: GlassTokens.textPrimary),
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
              return Row(
                children: [
                  Expanded(
                    child: _speedValue(
                      '下载',
                      formatSpeed(rx),
                      const [
                        GlassTokens.accentCyan,
                        GlassTokens.accentBlue,
                      ],
                      Icons.arrow_downward,
                    ),
                  ),
                  Container(
                    width: 1,
                    height: 46,
                    color: Colors.white.withValues(alpha: 0.14),
                  ),
                  Expanded(
                    child: _speedValue(
                      '上传',
                      formatSpeed(tx),
                      const [
                        GlassTokens.accentPurple,
                        GlassTokens.accentBlue,
                      ],
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
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: (_gatewayOk ? GlassTokens.success : GlassTokens.danger)
            .withValues(alpha: 0.14),
        border: Border.all(
          color: (_gatewayOk ? GlassTokens.success : GlassTokens.danger)
              .withValues(alpha: 0.45),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            _gatewayOk ? Icons.check_circle : Icons.error_outline,
            size: 14,
            color: _gatewayOk ? GlassTokens.success : GlassTokens.danger,
          ),
          const SizedBox(width: 5),
          Text(
            _gatewayOk ? '网关在线' : '网关不可达',
            style: TextStyle(
              fontSize: 11,
              color: _gatewayOk ? GlassTokens.success : GlassTokens.danger,
            ),
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(arrow, size: 13, color: GlassTokens.textMuted),
            const SizedBox(width: 4),
            Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: GlassTokens.textMuted,
              ),
            ),
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

  Widget _buildForegroundCard() {
    final pkg = _foreground['package']?.toString() ?? '';
    final label = _foreground['label']?.toString() ?? '未知';
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.apps, size: 20, color: GlassTokens.accentPurple),
              SizedBox(width: 8),
              Text(
                '前台应用',
                style: TextStyle(fontSize: 16, color: GlassTokens.textPrimary),
              ),
            ],
          ),
          const SizedBox(height: 14),
          if (!_hasUsageAccess)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '需要「使用情况访问」权限才能读取前台包名。',
                  style: TextStyle(color: GlassTokens.textMuted, fontSize: 13),
                ),
                const SizedBox(height: 12),
                FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: GlassTokens.accentBlue,
                    foregroundColor: Colors.white,
                  ),
                  onPressed: () async {
                    await NativeBridge.requestUsageAccess();
                    await _refresh();
                  },
                  child: const Text('前往授权'),
                ),
              ],
            )
          else
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 18,
                    color: GlassTokens.textPrimary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 5),
                SelectableText(
                  pkg.isEmpty ? '暂无前台应用' : pkg,
                  style: const TextStyle(
                    fontSize: 12,
                    color: GlassTokens.textMuted,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  '更新于 ${_foreground['time'] ?? '--'}',
                  style: TextStyle(
                    fontSize: 11,
                    color: GlassTokens.textMuted.withValues(alpha: 0.7),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildDeviceCard(String ssid) {
    final rows = <List<String>>[
      ['设备型号', _device['device_name']?.toString() ?? '--'],
      ['固件版本', _device['sw_version']?.toString() ?? '--'],
      ['Wi-Fi 名称', ssid.isEmpty ? '--' : ssid],
      ['运营商', _device['network_name']?.toString() ?? '--'],
      ['IMEI', _device['imei']?.toString() ?? '--'],
      ['局域网 IP', _device['lan_ip']?.toString() ?? '--'],
      ['广域网 IP', _device['wan_ip']?.toString() ?? '--'],
    ];
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.router, size: 20, color: GlassTokens.accentBlue),
              SizedBox(width: 8),
              Text(
                '设备信息',
                style: TextStyle(fontSize: 16, color: GlassTokens.textPrimary),
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
                      style: const TextStyle(
                        fontSize: 12,
                        color: GlassTokens.textMuted,
                      ),
                    ),
                  ),
                  Expanded(
                    child: SelectableText(
                      row[1],
                      style: const TextStyle(
                        fontSize: 13,
                        color: GlassTokens.textPrimary,
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
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.bolt, size: 20, color: GlassTokens.warn),
              SizedBox(width: 8),
              Text(
                '设备操作',
                style: TextStyle(fontSize: 16, color: GlassTokens.textPrimary),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _actionTile(
            icon: Icons.restore,
            color: GlassTokens.danger,
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
            color: GlassTokens.warn,
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
            color: GlassTokens.accentCyan,
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
        color: Colors.white.withValues(alpha: 0.10),
      );

  Widget _actionTile({
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
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
                    style: const TextStyle(
                      fontSize: 14,
                      color: GlassTokens.textPrimary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 11,
                      color: GlassTokens.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: GlassTokens.textMuted, size: 18),
          ],
        ),
      ),
    );
  }
}
