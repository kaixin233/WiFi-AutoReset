import 'package:flutter/material.dart';

import 'screens/automation_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/logs_screen.dart';
import 'screens/webview_screen.dart';
import 'widgets/glass.dart';

void main() => runApp(const WifiAutoResetApp());

ThemeData _buildTheme(Brightness brightness, GlassPalette palette) {
  final isDark = brightness == Brightness.dark;
  return ThemeData(
    brightness: brightness,
    colorScheme: ColorScheme.fromSeed(
      seedColor: palette.accentBlue,
      brightness: brightness,
    ),
    scaffoldBackgroundColor: Colors.transparent,
    useMaterial3: true,
    dividerColor: palette.divider,
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      titleTextStyle: TextStyle(
        color: palette.textPrimary,
        fontSize: 19,
        fontWeight: FontWeight.w600,
      ),
      iconTheme: IconThemeData(color: palette.textPrimary),
      elevation: 0,
      scrolledUnderElevation: 0,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: isDark ? const Color(0xFF161E3E) : Colors.white,
      surfaceTintColor: Colors.transparent,
      titleTextStyle: TextStyle(
        color: palette.textPrimary,
        fontSize: 19,
        fontWeight: FontWeight.w600,
      ),
      contentTextStyle: TextStyle(
        color: palette.textMuted,
        fontSize: 14,
        height: 1.5,
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: isDark ? const Color(0xFF202A52) : Colors.white,
      contentTextStyle: TextStyle(color: palette.textPrimary),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    textTheme: TextTheme(
      bodyLarge: TextStyle(color: palette.textPrimary),
      bodyMedium: TextStyle(color: palette.textPrimary),
    ),
  );
}

class WifiAutoResetApp extends StatelessWidget {
  const WifiAutoResetApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WiFi 自动重置',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.system,
      theme: _buildTheme(Brightness.light, GlassPalette.light),
      darkTheme: _buildTheme(Brightness.dark, GlassPalette.dark),
      builder: (context, child) {
        final brightness = Theme.of(context).brightness;
        return GlassTheme(
          palette: brightness == Brightness.dark
              ? GlassPalette.dark
              : GlassPalette.light,
          child: child ?? const SizedBox.shrink(),
        );
      },
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
      bottomNavigationBar: LiquidGlassNavBar(
        currentIndex: _index,
        icons: _icons,
        activeIcons: _activeIcons,
        labels: _labels,
        onTap: (index) => setState(() => _index = index),
      ),
    );
  }
}
