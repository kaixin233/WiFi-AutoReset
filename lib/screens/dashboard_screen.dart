import 'dart:async';

import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';
import '../utils/format.dart';

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
  bool _dryRun = true;
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
    final schedule = await NativeBridge.getSchedule();
    final reachable = await NativeBridge.gatewayReachable();
    if (!mounted) return;
    setState(() {
      _dryRun = schedule.dryRun;
      _gatewayOk = reachable;
    });
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
              backgroundColor: Colors.red.shade700,
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
          result.dryRun
              ? '演练模式：${result.detail}'
              : '${result.ok ? "已执行" : "执行失败"}：${result.detail}',
        ),
      ),
    );
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final ssid = decodeUtf16Hex(_device['ssid']?.toString() ?? '');
    return Scaffold(
      appBar: AppBar(
        title: const Text('仪表盘'),
        actions: [
          IconButton(
            onPressed: _refresh,
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
          ),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refresh,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _buildSpeedCard(),
              const SizedBox(height: 12),
              _buildForegroundCard(),
              const SizedBox(height: 12),
              _buildDeviceCard(ssid),
              const SizedBox(height: 12),
              _buildActionsCard(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSpeedCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.speed, size: 20),
                const SizedBox(width: 8),
                const Text('实时网速', style: TextStyle(fontSize: 16)),
                const Spacer(),
                Chip(
                  label: Text(_gatewayOk ? '网关在线' : '网关不可达'),
                  avatar: Icon(
                    _gatewayOk ? Icons.check_circle : Icons.error_outline,
                    size: 16,
                    color: _gatewayOk ? Colors.green : Colors.red,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            StreamBuilder<Map<String, dynamic>>(
              stream: NativeBridge.speedStream,
              builder: (context, snapshot) {
                final data = snapshot.data ?? const <String, dynamic>{};
                final rx = data['rx'] is int ? data['rx'] as int : 0;
                final tx = data['tx'] is int ? data['tx'] as int : 0;
                return Row(
                  children: [
                    Expanded(
                      child: _speedValue('下载', formatSpeed(rx), Colors.green),
                    ),
                    Expanded(
                      child: _speedValue('上传', formatSpeed(tx), Colors.blue),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _speedValue(String label, String value, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: color),
        ),
      ],
    );
  }

  Widget _buildForegroundCard() {
    final pkg = _foreground['package']?.toString() ?? '';
    final label = _foreground['label']?.toString() ?? '未知';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.apps, size: 20),
                SizedBox(width: 8),
                Text('前台应用', style: TextStyle(fontSize: 16)),
              ],
            ),
            const SizedBox(height: 12),
            if (!_hasUsageAccess)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '需要「使用情况访问」权限才能读取前台包名。',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                  const SizedBox(height: 8),
                  FilledButton(
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
                  Text(label, style: const TextStyle(fontSize: 18)),
                  const SizedBox(height: 4),
                  SelectableText(
                    pkg.isEmpty ? '暂无前台应用' : pkg,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '更新于 ${_foreground['time'] ?? '--'}',
                    style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
                  ),
                ],
              ),
          ],
        ),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.router, size: 20),
                SizedBox(width: 8),
                Text('设备信息', style: TextStyle(fontSize: 16)),
              ],
            ),
            const SizedBox(height: 8),
            ...rows.map(
              (row) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    SizedBox(
                      width: 80,
                      child: Text(
                        row[0],
                        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      ),
                    ),
                    Expanded(
                      child: SelectableText(row[1], style: const TextStyle(fontSize: 13)),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.build_circle, size: 20),
                const SizedBox(width: 8),
                const Text('设备操作', style: TextStyle(fontSize: 16)),
                const Spacer(),
                FilterChip(
                  label: Text(_dryRun ? '演练中' : '真机模式'),
                  selected: !_dryRun,
                  onSelected: (value) async {
                    final state = await NativeBridge.configureSchedule(
                      enabled: (await NativeBridge.getSchedule()).enabled,
                      dryRun: !value,
                    );
                    if (!mounted) return;
                    setState(() => _dryRun = state.dryRun);
                  },
                ),
              ],
            ),
            const SizedBox(height: 8),
            ListTile(
              leading: const Icon(Icons.restore, color: Colors.red),
              title: const Text('恢复出厂设置'),
              subtitle: const Text('清空全部配置并重启，会改变 WiFi 名称与密码'),
              onTap: () => _execute(
                '恢复出厂设置',
                '此操作会清空随身 WiFi 的全部配置（含管理账号、SSID 与 WiFi 密码），设备将重启，当前手机会断网。确认继续？',
                () => NativeBridge.factoryReset(dryRun: _dryRun),
              ),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.restart_alt, color: Colors.orange),
              title: const Text('重启设备'),
              subtitle: const Text('保留配置，重新拨号联网'),
              onTap: () => _execute(
                '重启设备',
                '设备会短暂断网后重新上线，配置不会丢失。确认继续？',
                () => NativeBridge.reboot(dryRun: _dryRun),
              ),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.data_usage, color: Colors.blue),
              title: const Text('重置流量统计'),
              subtitle: const Text('只清零流量计数，不改变任何配置'),
              onTap: () => _execute(
                '重置流量统计',
                '仅清零设备上的流量统计计数，不会修改账号、WiFi 名称或密码。确认继续？',
                () => NativeBridge.resetTraffic(dryRun: _dryRun),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
