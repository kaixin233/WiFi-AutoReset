import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';

class LogsScreen extends StatefulWidget {
  const LogsScreen({super.key});

  @override
  State<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends State<LogsScreen> {
  List<LogEntry> _logs = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final logs = await NativeBridge.getLogs();
    if (!mounted) return;
    setState(() {
      _logs = logs;
      _loading = false;
    });
  }

  Future<void> _clear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清空日志'),
        content: const Text('确定要清空全部执行记录吗？此操作不可撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('清空'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await NativeBridge.clearLogs();
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('执行日志'),
        actions: [
          IconButton(
            onPressed: _clear,
            icon: const Icon(Icons.delete_outline),
            tooltip: '清空',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: _logs.isEmpty
                  ? ListView(
                      children: const [
                        SizedBox(height: 120),
                        Center(
                          child: Text(
                            '暂无执行记录',
                            style: TextStyle(color: Colors.grey),
                          ),
                        ),
                      ],
                    )
                  : ListView.builder(
                      itemCount: _logs.length,
                      itemBuilder: (context, index) {
                        final entry = _logs[index];
                        final color = entry.isFailure
                            ? Colors.red.shade100
                            : entry.isDryRun
                                ? Colors.grey.shade200
                                : Colors.green.shade100;
                        return ListTile(
                          leading: CircleAvatar(
                            backgroundColor: color,
                            child: Icon(
                              entry.isFailure
                                  ? Icons.error_outline
                                  : entry.isDryRun
                                      ? Icons.visibility_off_outlined
                                      : Icons.check,
                              size: 18,
                            ),
                          ),
                          title: Text('${entry.action} · ${entry.result}'),
                          subtitle: Text(
                            '${entry.time}  [${entry.mode}]\n${entry.detail}',
                          ),
                          isThreeLine: true,
                        );
                      },
                    ),
            ),
    );
  }
}
