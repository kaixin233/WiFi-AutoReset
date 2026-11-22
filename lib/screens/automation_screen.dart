import 'dart:async';

import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';
import '../widgets/glass.dart';

class AutomationScreen extends StatefulWidget {
  const AutomationScreen({super.key});

  @override
  State<AutomationScreen> createState() => _AutomationScreenState();
}

class _AutomationScreenState extends State<AutomationScreen> {
  ScheduleState _state = const ScheduleState(enabled: false);
  bool _loading = true;
  Timer? _ticker;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _load();
    _ticker = Timer.periodic(
      const Duration(seconds: 1),
      (_) => mounted ? setState(() => _now = DateTime.now()) : null,
    );
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    final state = await NativeBridge.getSchedule();
    if (!mounted) return;
    setState(() {
      _state = state;
      _loading = false;
    });
  }

  Future<void> _save() async {
    final state = await NativeBridge.configureSchedule(
      enabled: _state.enabled,
    );
    if (!mounted) return;
    setState(() => _state = state);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          state.enabled ? '定时任务已开启，整点自动执行' : '定时任务已关闭',
        ),
      ),
    );
  }

  /// 下一个整点第 0 分 05 秒（与原生 Worker 的排程保持一致）。
  DateTime get _nextRun {
    final next = DateTime(
      _now.year,
      _now.month,
      _now.day,
      _now.hour + 1,
      0,
      5,
    );
    return next;
  }

  String get _countdown {
    final diff = _nextRun.difference(_now);
    if (diff.isNegative) return '即将执行';
    final hours = diff.inHours;
    final minutes = diff.inMinutes % 60;
    final seconds = diff.inSeconds % 60;
    return '${hours.toString().padLeft(2, '0')}:'
        '${minutes.toString().padLeft(2, '0')}:'
        '${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: const Text('自动化')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 110),
              children: [
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(
                            Icons.schedule,
                            size: 20,
                            color: GlassTokens.accentCyan,
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            '整点自动恢复出厂',
                            style: TextStyle(
                              fontSize: 16,
                              color: GlassTokens.textPrimary,
                            ),
                          ),
                          const Spacer(),
                          _buildStatusBadge(),
                        ],
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        '每小时第 0 分 05 秒执行一次',
                        style: TextStyle(
                          fontSize: 12,
                          color: GlassTokens.textMuted,
                        ),
                      ),
                      const SizedBox(height: 14),
                      _buildCountdown(),
                      const SizedBox(height: 14),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text(
                          '启用定时任务',
                          style: TextStyle(color: GlassTokens.textPrimary),
                        ),
                        subtitle: const Text(
                          '由 WorkManager 调度，执行后自动排下一次',
                          style: TextStyle(
                            fontSize: 11,
                            color: GlassTokens.textMuted,
                          ),
                        ),
                        value: _state.enabled,
                        activeThumbColor: GlassTokens.accentCyan,
                        onChanged: (value) =>
                            setState(() => _state = ScheduleState(enabled: value)),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Row(
                        children: [
                          Icon(
                            Icons.info_outline,
                            size: 20,
                            color: GlassTokens.accentBlue,
                          ),
                          SizedBox(width: 8),
                          Text(
                            '执行说明',
                            style: TextStyle(
                              fontSize: 16,
                              color: GlassTokens.textPrimary,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '• 每次执行都会真实调用固件接口 json_device_restore_factory，'
                        '设备清空配置并重启，手机会断网。\n'
                        '• 系统省电策略（Doze）可能让任务延迟数分钟，'
                        '无法保证毫秒级准点。\n'
                        '• 每次执行结果都会写入「日志」页，'
                        '成功与失败均可追溯。',
                        style: const TextStyle(
                          fontSize: 12.5,
                          height: 1.7,
                          color: GlassTokens.textMuted,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: GlassTokens.accentBlue,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                    ),
                    onPressed: _save,
                    icon: const Icon(Icons.save_outlined),
                    label: const Text('保存设置'),
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildStatusBadge() {
    final on = _state.enabled;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: (on ? GlassTokens.success : GlassTokens.textMuted)
            .withValues(alpha: 0.14),
        border: Border.all(
          color: (on ? GlassTokens.success : GlassTokens.textMuted)
              .withValues(alpha: 0.4),
        ),
      ),
      child: Text(
        on ? '运行中' : '已关闭',
        style: TextStyle(
          fontSize: 11,
          color: on ? GlassTokens.success : GlassTokens.textMuted,
        ),
      ),
    );
  }

  Widget _buildCountdown() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        color: Colors.white.withValues(alpha: 0.05),
        border: Border.all(color: Colors.white.withValues(alpha: 0.10)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          const Text(
            '距离下次执行',
            style: TextStyle(fontSize: 12, color: GlassTokens.textMuted),
          ),
          GradientText(
            _state.enabled ? _countdown : '--:--:--',
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w700,
              fontFeatures: [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}
