import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/native_bridge.dart';
import '../widgets/glass.dart';

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
            style: FilledButton.styleFrom(
              backgroundColor: GlassTokens.danger,
              foregroundColor: Colors.white,
            ),
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
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        title: const Text('执行日志'),
        actions: [
          GlassIconButton(
            icon: Icons.delete_outline,
            tooltip: '清空',
            onTap: _clear,
          ),
          const SizedBox(width: 10),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: _logs.isEmpty
                  ? ListView(
                      children: [
                        const SizedBox(height: 140),
                        Icon(
                          Icons.history,
                          size: 52,
                          color: GlassTokens.textMuted.withValues(alpha: 0.5),
                        ),
                        const SizedBox(height: 16),
                        const Center(
                          child: Text(
                            '暂无执行记录',
                            style: TextStyle(color: GlassTokens.textMuted),
                          ),
                        ),
                      ],
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 4, 16, 110),
                      itemCount: _logs.length,
                      itemBuilder: (context, index) =>
                          _buildLogCard(_logs[index]),
                    ),
            ),
    );
  }

  Widget _buildLogCard(LogEntry entry) {
    final color = entry.isFailure ? GlassTokens.danger : GlassTokens.success;
    final icon = entry.isFailure ? Icons.error_outline : Icons.check_circle;
    return GlassCard(
      padding: const EdgeInsets.all(14),
      margin: const EdgeInsets.only(bottom: 10),
      radius: 16,
      blur: 10,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(11),
              color: color.withValues(alpha: 0.15),
            ),
            child: Icon(icon, color: color, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '${entry.action} · ${entry.result}',
                        style: const TextStyle(
                          fontSize: 14,
                          color: GlassTokens.textPrimary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(6),
                        color: Colors.white.withValues(alpha: 0.08),
                      ),
                      child: Text(
                        entry.mode,
                        style: const TextStyle(
                          fontSize: 10,
                          color: GlassTokens.textMuted,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  entry.time,
                  style: const TextStyle(
                    fontSize: 11,
                    color: GlassTokens.textMuted,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  entry.detail,
                  style: const TextStyle(
                    fontSize: 11.5,
                    color: GlassTokens.textMuted,
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
