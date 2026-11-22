import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';

class AutomationScreen extends StatefulWidget {
  const AutomationScreen({super.key});

  @override
  State<AutomationScreen> createState() => _AutomationScreenState();
}

class _AutomationScreenState extends State<AutomationScreen> {
  ScheduleState _state = const ScheduleState(enabled: false, dryRun: true);
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
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
      dryRun: _state.dryRun,
    );
    if (!mounted) return;
    setState(() => _state = state);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          state.enabled
              ? '定时任务已开启，下一个整点执行（${state.dryRun ? "演练" : "真机"}）'
              : '定时任务已关闭',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        appBar: AppBar(title: Text('自动化')),
        body: Center(child: CircularProgressIndicator()),
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('自动化')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Column(
                children: [
                  SwitchListTile(
                    title: const Text('启用整点自动恢复出厂'),
                    subtitle: const Text('每小时第 0 分第 5 秒执行一次'),
                    value: _state.enabled,
                    onChanged: (value) =>
                        setState(() => _state = ScheduleState(
                              enabled: value,
                              dryRun: _state.dryRun,
                            )),
                  ),
                  const Divider(height: 1),
                  SwitchListTile(
                    title: const Text('演练模式'),
                    subtitle: const Text('只写日志，不向设备发送重置请求'),
                    value: _state.dryRun,
                    onChanged: (value) =>
                        setState(() => _state = ScheduleState(
                              enabled: _state.enabled,
                              dryRun: value,
                            )),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('执行说明', style: TextStyle(fontSize: 16)),
                    const SizedBox(height: 8),
                    Text(
                      '• 使用 Android WorkManager 调度，执行后自动排下一次。\n'
                      '• 系统省电策略（Doze）可能让任务延迟数分钟，无法保证毫秒级准点。\n'
                      '• 真机模式会调用固件接口 json_device_restore_factory，设备将清空配置并重启，手机会断网。\n'
                      '• 每次执行（含演练）都会写入「日志」页。',
                      style: TextStyle(
                        fontSize: 13,
                        height: 1.6,
                        color: Colors.grey.shade700,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _save,
              child: const Text('保存设置'),
            ),
          ],
        ),
      ),
    );
  }
}
