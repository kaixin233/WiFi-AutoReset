import 'package:flutter/material.dart';

import 'screens/automation_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/logs_screen.dart';
import 'screens/webview_screen.dart';
import 'widgets/glass.dart';

void main() => runApp(const WifiAutoResetApp());

class WifiAutoResetApp extends StatelessWidget {
  const WifiAutoResetApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WiFi 自动重置',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: GlassTokens.accentBlue,
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: GlassTokens.bgBottom,
        useMaterial3: true,
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0x1A0B1024),
          surfaceTintColor: Colors.transparent,
          titleTextStyle: TextStyle(
            color: GlassTokens.textPrimary,
            fontSize: 19,
            fontWeight: FontWeight.w600,
          ),
          iconTheme: IconThemeData(color: GlassTokens.textPrimary),
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        dialogTheme: DialogThemeData(
          backgroundColor: const Color(0xFF161E3E),
          surfaceTintColor: Colors.transparent,
          titleTextStyle: const TextStyle(
            color: GlassTokens.textPrimary,
            fontSize: 19,
            fontWeight: FontWeight.w600,
          ),
          contentTextStyle: const TextStyle(
            color: GlassTokens.textMuted,
            fontSize: 14,
            height: 1.5,
          ),
        ),
        snackBarTheme: SnackBarThemeData(
          backgroundColor: const Color(0xFF202A52),
          contentTextStyle: const TextStyle(color: GlassTokens.textPrimary),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
        textTheme: const TextTheme(
          bodyLarge: TextStyle(color: GlassTokens.textPrimary),
          bodyMedium: TextStyle(color: GlassTokens.textPrimary),
        ),
      ),
      home: const MainShell(),
    );
  }
}

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _index = 0;

  static const List<Widget> _pages = [
    DashboardScreen(),
    AutomationScreen(),
    LogsScreen(),
    WebViewScreen(),
  ];

  static const List<IconData> _icons = [
    Icons.space_dashboard_outlined,
    Icons.schedule_outlined,
    Icons.history_outlined,
    Icons.public_outlined,
  ];

  static const List<IconData> _activeIcons = [
    Icons.space_dashboard,
    Icons.schedule,
    Icons.history,
    Icons.public,
  ];

  static const List<String> _labels = ['仪表盘', '自动化', '日志', '管理页'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true,
      body: LiquidBackdrop(
        child: IndexedStack(index: _index, children: _pages),
      ),
      bottomNavigationBar: _buildNavBar(),
    );
  }

  Widget _buildNavBar() {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 7, horizontal: 5),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(24),
                color: Colors.white.withValues(alpha: 0.09),
                border: Border.all(
                  color: const Color(0x3DFFFFFF),
                  width: 1,
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: List.generate(_labels.length, _buildNavItem),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(int i) {
    final selected = i == _index;
    return Expanded(
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: () => setState(() => _index = i),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 220),
            padding: const EdgeInsets.symmetric(vertical: 8),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              color: selected
                  ? GlassTokens.accentBlue.withValues(alpha: 0.24)
                  : Colors.transparent,
              border: selected
                  ? Border.all(
                      color: GlassTokens.accentCyan.withValues(alpha: 0.55),
                      width: 1,
                    )
                  : null,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  selected ? _activeIcons[i] : _icons[i],
                  size: 21,
                  color: selected
                      ? GlassTokens.accentCyan
                      : GlassTokens.textMuted,
                ),
                const SizedBox(height: 3),
                Text(
                  _labels[i],
                  style: TextStyle(
                    fontSize: 10.5,
                    color: selected
                        ? GlassTokens.textPrimary
                        : GlassTokens.textMuted,
                    fontWeight:
                        selected ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
