import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../services/native_bridge.dart';

class WebViewScreen extends StatefulWidget {
  const WebViewScreen({super.key});

  @override
  State<WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<WebViewScreen> {
  late final WebViewController _controller;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.white)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) async {
            await Future<void>.delayed(const Duration(milliseconds: 600));
            await _autoLogin();
            if (mounted) setState(() => _loading = false);
          },
          onWebResourceError: (error) {
            if (mounted) setState(() => _loading = false);
          },
        ),
      )
      ..loadRequest(Uri.parse('http://192.168.1.1'));
  }

  Future<void> _autoLogin() async {
    await _controller.runJavaScript('''
(function(){
  try {
    var u = document.getElementById('tbarouter_username');
    var p = document.getElementById('tbarouter_password');
    if (u && p) {
      u.value = 'admin';
      p.value = 'admin';
      if (typeof Login === 'function') Login();
    }
  } catch (e) {}
})();
''');
  }

  Future<void> _resetFromApp() async {
    final schedule = await NativeBridge.getSchedule();
    if (!mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('恢复出厂设置'),
        content: Text(
          schedule.dryRun
              ? '当前为演练模式，只会记录日志，不会真的重置设备。确认继续？'
              : '此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red.shade700),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final result = await NativeBridge.factoryReset(dryRun: schedule.dryRun);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(result.detail)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('管理页'),
        actions: [
          IconButton(
            onPressed: () => _controller.reload(),
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
          ),
        ],
      ),
      body: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_loading) const LinearProgressIndicator(),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _autoLogin,
                  icon: const Icon(Icons.login),
                  label: const Text('重新登录'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.red.shade700,
                  ),
                  onPressed: _resetFromApp,
                  icon: const Icon(Icons.restore),
                  label: const Text('恢复出厂'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
