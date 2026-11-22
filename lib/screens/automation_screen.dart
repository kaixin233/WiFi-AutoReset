import 'dart:async';

import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';
import '../utils/format.dart';
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

  static const List<int> _presets = [15, 30, 60, 120, 360, 720];

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
      intervalMinutes: _state.intervalMinutes,
    );
    if (!mounted) return;
    setState(() => _state = state);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          state.enabled
              ? '定时任务已开启：$_intervalLabel'
              : '定时任务已关闭',
        ),
      ),
    );
  }

  String get _intervalLabel {
    final minutes = _state.intervalMinutes;
    if (minutes == 1440) return '每天';
    if (minutes >= 60 && minutes % 60 == 0) return '每 ${minutes ~/ 60} 小时';
    return '每 $minutes 分钟';
  }

  /// 与原生 AutoResetWorker.nextRunMillis 完全一致：
  /// 按 Unix 纪元对齐到周期起点，再加 5 秒。
  DateTime get _nextRun => nextRunAt(_now, _state.intervalMinutes);

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

  Future<void> _pickCustomInterval() async {
    final palette = context.glass;
    final controller = TextEditingController(
      text: _state.intervalMinutes.toString(),
    );
    final value = await showDialog<int>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('自定义执行周期'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: '间隔（分钟）',
            hintText: '5 ~ 1440',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: palette.accentBlue,
              foregroundColor: Colors.white,
            ),
            onPressed: () {
              final parsed = int.tryParse(controller.text.trim());
              if (parsed == null || parsed < 5 || parsed > 1440) {
                Navigator.pop(dialogContext);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('请输入 5 ~ 1440 之间的分钟数')),
                );
                return;
              }
              Navigator.pop(dialogContext, parsed);
            },
            child: const Text('确定'),
          ),
        ],
      ),
    );
    if (value == null || !mounted) return;
    setState(() => _state = _state.copyWith(intervalMinutes: value));
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
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
                          Icon(Icons.schedule,
                              size: 20, color: palette.accentCyan),
                          const SizedBox(width: 8),
                          Text(
                            '定时自动恢复出厂',
                            style: TextStyle(
                              fontSize: 16,
                              color: palette.textPrimary,
                            ),
                          ),
                          const Spacer(),
                          _buildStatusBadge(),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '$_intervalLabel · 周期起点后 5 秒执行',
                        style: TextStyle(
                          fontSize: 12,
                          color: palette.textMuted,
                        ),
                      ),
                      const SizedBox(height: 14),
                      _buildCountdown(),
                      const SizedBox(height: 10),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          '启用定时任务',
                          style: TextStyle(color: palette.textPrimary),
                        ),
                        subtitle: Text(
                          '由 WorkManager 调度，执行后自动排下一次',
                          style: TextStyle(
                            fontSize: 11,
                            color: palette.textMuted,
                          ),
                        ),
                        value: _state.enabled,
                        activeThumbColor: palette.accentCyan,
                        onChanged: (value) => setState(
                          () => _state = _state.copyWith(enabled: value),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.timelapse,
                              size: 20, color: palette.accentPurple),
                          const SizedBox(width: 8),
                          Text(
                            '执行周期',
                            style: TextStyle(
                              fontSize: 16,
                              color: palette.textPrimary,
                            ),
                          ),
                          const Spacer(),
                          GradientText(
                            _intervalLabel,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          ..._presets.map(_buildPresetChip),
                          _buildCustomChip(),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '周期为 60 分钟时按整点执行；修改后需点击「保存设置」生效。',
                        style: TextStyle(
                          fontSize: 11,
                          color: palette.textMuted,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.info_outline,
                              size: 20, color: palette.accentBlue),
                          const SizedBox(width: 8),
                          Text(
                            '执行说明',
                            style: TextStyle(
                              fontSize: 16,
                              color: palette.textPrimary,
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
                        style: TextStyle(
                          fontSize: 12.5,
                          height: 1.7,
                          color: palette.textMuted,
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
                      backgroundColor: palette.accentBlue,
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

  Widget _buildPresetChip(int minutes) {
    final palette = context.glass;
    final selected = _state.intervalMinutes == minutes;
    return ChoiceChip(
      label: Text(
        minutes >= 60 ? '${minutes ~/ 60} 小时' : '$minutes 分',
        style: TextStyle(
          fontSize: 12,
          color: selected ? Colors.white : palette.textMuted,
        ),
      ),
      selected: selected,
      showCheckmark: false,
      onSelected: (_) =>
          setState(() => _state = _state.copyWith(intervalMinutes: minutes)),
      selectedColor: palette.accentBlue,
      backgroundColor: palette.surfaceFill,
      side: BorderSide(color: palette.divider),
    );
  }

  Widget _buildCustomChip() {
    final palette = context.glass;
    final isPreset = _presets.contains(_state.intervalMinutes);
    return ChoiceChip(
      avatar: Icon(Icons.tune, size: 14, color: palette.textMuted),
      label: Text(
        isPreset ? '自定义' : '${_state.intervalMinutes} 分',
        style: TextStyle(
          fontSize: 12,
          color: palette.textMuted,
        ),
      ),
      selected: !isPreset,
      showCheckmark: false,
      onSelected: (_) => _pickCustomInterval(),
      backgroundColor: palette.surfaceFill,
      side: BorderSide(color: palette.divider),
    );
  }

  Widget _buildStatusBadge() {
    final palette = context.glass;
    final on = _state.enabled;
    final color = on ? palette.success : palette.textMuted;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: color.withValues(alpha: 0.14),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        on ? '运行中' : '已关闭',
        style: TextStyle(fontSize: 11, color: color),
      ),
    );
  }

  Widget _buildCountdown() {
    final palette = context.glass;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        color: palette.surfaceFill,
        border: Border.all(color: palette.divider),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            '距离下次执行',
            style: TextStyle(fontSize: 12, color: palette.textMuted),
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
