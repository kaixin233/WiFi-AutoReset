import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../services/native_bridge.dart';
import '../widgets/glass.dart';

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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('恢复出厂设置'),
        content: const Text(
          '此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？',
        ),
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
            child: const Text('确认'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final result = await NativeBridge.factoryReset();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(result.detail)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        title: const Text('管理页'),
        actions: [
          GlassIconButton(
            icon: Icons.refresh,
            tooltip: '刷新',
            onTap: () => _controller.reload(),
          ),
          const SizedBox(width: 10),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                WebViewWidget(controller: _controller),
                if (_loading)
                  const LinearProgressIndicator(
                    color: GlassTokens.accentCyan,
                    backgroundColor: Colors.transparent,
                  ),
              ],
            ),
          ),
          _buildToolbar(),
        ],
      ),
    );
  }

  Widget _buildToolbar() {
    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 96),
          decoration: BoxDecoration(
            color: GlassTokens.bgBottom.withValues(alpha: 0.55),
            border: const Border(
              top: BorderSide(color: Color(0x2EFFFFFF), width: 1),
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: GlassTokens.accentCyan,
                    side: BorderSide(
                      color: GlassTokens.accentCyan.withValues(alpha: 0.5),
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  onPressed: _autoLogin,
                  icon: const Icon(Icons.login, size: 18),
                  label: const Text('重新登录'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: GlassTokens.danger,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  onPressed: _resetFromApp,
                  icon: const Icon(Icons.restore, size: 18),
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
