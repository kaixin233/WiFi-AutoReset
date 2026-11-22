import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';

/// 与主题无关的常量：强调色与圆角。
/// 浅色模式下更深的强调色由 [GlassPalette] 提供。
class GlassTokens {
  static const Color accentCyan = Color(0xFF54E0E8);
  static const Color accentBlue = Color(0xFF6C9BFF);
  static const Color accentPurple = Color(0xFFB18CFF);

  static const Color success = Color(0xFF62E3AF);
  static const Color danger = Color(0xFFFF7B7B);
  static const Color warn = Color(0xFFFFC86B);

  static const double radius = 22;
}

/// 明暗两套玻璃色板。通过 [GlassTheme] 下发，页面用 `context.glass` 读取。
class GlassPalette {
  final bool isDark;
  final Color bgTop;
  final Color bgMid;
  final Color bgBottom;
  final Color textPrimary;
  final Color textMuted;
  final Color accentCyan;
  final Color accentBlue;
  final Color accentPurple;
  final Color success;
  final Color danger;
  final Color warn;
  final Color cardTintTop;
  final Color cardTintBottom;
  final Color cardBorder;
  final Color cardShadow;
  final Color divider;
  final Color surfaceFill;
  final Color dockTintTop;
  final Color dockTintBottom;
  final Color dockBorder;
  final Color pillColor;
  final Color glowColor;
  final double orbOpacity;

  const GlassPalette({
    required this.isDark,
    required this.bgTop,
    required this.bgMid,
    required this.bgBottom,
    required this.textPrimary,
    required this.textMuted,
    required this.accentCyan,
    required this.accentBlue,
    required this.accentPurple,
    required this.success,
    required this.danger,
    required this.warn,
    required this.cardTintTop,
    required this.cardTintBottom,
    required this.cardBorder,
    required this.cardShadow,
    required this.divider,
    required this.surfaceFill,
    required this.dockTintTop,
    required this.dockTintBottom,
    required this.dockBorder,
    required this.pillColor,
    required this.glowColor,
    required this.orbOpacity,
  });

  static const dark = GlassPalette(
    isDark: true,
    bgTop: Color(0xFF0B1024),
    bgMid: Color(0xFF141C3C),
    bgBottom: Color(0xFF090D1E),
    textPrimary: Color(0xFFF3F6FF),
    textMuted: Color(0xAAA9B8D6),
    accentCyan: GlassTokens.accentCyan,
    accentBlue: GlassTokens.accentBlue,
    accentPurple: GlassTokens.accentPurple,
    success: GlassTokens.success,
    danger: GlassTokens.danger,
    warn: GlassTokens.warn,
    cardTintTop: Color(0x2EFFFFFF),
    cardTintBottom: Color(0x14FFFFFF),
    cardBorder: Color(0x59FFFFFF),
    cardShadow: Color(0x66000000),
    divider: Color(0x1AFFFFFF),
    surfaceFill: Color(0x0DFFFFFF),
    dockTintTop: Color(0x2BFFFFFF),
    dockTintBottom: Color(0x14FFFFFF),
    dockBorder: Color(0x59FFFFFF),
    pillColor: Color(0x4D6C9BFF),
    glowColor: Color(0x4054E0E8),
    orbOpacity: 0.26,
  );

  static const light = GlassPalette(
    isDark: false,
    bgTop: Color(0xFFEFF4FF),
    bgMid: Color(0xFFE3ECFB),
    bgBottom: Color(0xFFF7F9FF),
    textPrimary: Color(0xFF16213E),
    textMuted: Color(0xFF5C6885),
    accentCyan: Color(0xFF0E8E9B),
    accentBlue: Color(0xFF2F6FE4),
    accentPurple: Color(0xFF7B4FD1),
    success: Color(0xFF148C68),
    danger: Color(0xFFD43B4B),
    warn: Color(0xFFB8740A),
    cardTintTop: Color(0xE6FFFFFF),
    cardTintBottom: Color(0xA8FFFFFF),
    cardBorder: Color(0xB3FFFFFF),
    cardShadow: Color(0x1A1F3A75),
    divider: Color(0x1A16213E),
    surfaceFill: Color(0x66FFFFFF),
    dockTintTop: Color(0xE8FFFFFF),
    dockTintBottom: Color(0xB3FFFFFF),
    dockBorder: Color(0xCCFFFFFF),
    pillColor: Color(0x332F6FE4),
    glowColor: Color(0x2E2F6FE4),
    orbOpacity: 0.14,
  );
}

/// 按系统明暗提供色板的 InheritedWidget。
class GlassTheme extends InheritedWidget {
  const GlassTheme({
    super.key,
    required this.palette,
    required super.child,
  });

  final GlassPalette palette;

  static GlassPalette of(BuildContext context) {
    final widget = context.dependOnInheritedWidgetOfExactType<GlassTheme>();
    assert(widget != null, 'GlassTheme 必须位于 MaterialApp.builder 内');
    return widget!.palette;
  }

  @override
  bool updateShouldNotify(GlassTheme oldWidget) => oldWidget.palette != palette;
}

extension GlassContext on BuildContext {
  GlassPalette get glass => GlassTheme.of(this);
}

/// 全屏流动光晕背景。光球缓慢漂移，营造玻璃折射的纵深。
class LiquidBackdrop extends StatefulWidget {
  const LiquidBackdrop({super.key, required this.child});

  final Widget child;

  @override
  State<LiquidBackdrop> createState() => _LiquidBackdropState();
}

class _LiquidBackdropState extends State<LiquidBackdrop>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 9),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
    return Stack(
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [palette.bgTop, palette.bgMid, palette.bgBottom],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: AnimatedBuilder(
            animation: _controller,
            builder: (context, _) {
              final t = _controller.value;
              return Stack(
                children: [
                  _orb(
                    top: -70 + t * 34,
                    left: -50 + t * 26,
                    size: 260,
                    color: palette.accentCyan,
                    opacity: palette.orbOpacity,
                  ),
                  _orb(
                    top: 90 - t * 40,
                    right: -70 + t * 22,
                    size: 300,
                    color: palette.accentPurple,
                    opacity: palette.orbOpacity * 0.9,
                  ),
                  _orb(
                    bottom: -90 + t * 44,
                    left: 40 - t * 30,
                    size: 320,
                    color: palette.accentBlue,
                    opacity: palette.orbOpacity * 0.8,
                  ),
                ],
              );
            },
          ),
        ),
        widget.child,
      ],
    );
  }

  Widget _orb({
    double? top,
    double? bottom,
    double? left,
    double? right,
    required double size,
    required Color color,
    required double opacity,
  }) {
    return Positioned(
      top: top,
      bottom: bottom,
      left: left,
      right: right,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            colors: [
              color.withValues(alpha: opacity),
              color.withValues(alpha: 0),
            ],
          ),
        ),
      ),
    );
  }
}

/// 玻璃卡片：背景模糊 + 折射高光描边 + 顶部反光 + 柔和投影。
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin = EdgeInsets.zero,
    this.radius = GlassTokens.radius,
    this.blur = 14,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final double radius;
  final double blur;

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
    final border = BorderRadius.all(Radius.circular(radius));
    return Container(
      margin: margin,
      decoration: BoxDecoration(
        borderRadius: border,
        boxShadow: [
          BoxShadow(
            color: palette.cardShadow,
            blurRadius: 26,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: border,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: CustomPaint(
            painter: _GlassEdgePainter(
              radius: radius,
              borderColor: palette.cardBorder,
              highlightColor: palette.isDark
                  ? const Color(0xFFFFFFFF)
                  : const Color(0xFF2F6FE4),
            ),
            child: Container(
              padding: padding,
              decoration: BoxDecoration(
                borderRadius: border,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [palette.cardTintTop, palette.cardTintBottom],
                ),
              ),
              child: child,
            ),
          ),
        ),
      ),
    );
  }
}

class _GlassEdgePainter extends CustomPainter {
  _GlassEdgePainter({
    required this.radius,
    required this.borderColor,
    required this.highlightColor,
  });

  final double radius;
  final Color borderColor;
  final Color highlightColor;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final rrect = RRect.fromRectAndRadius(rect, Radius.circular(radius));

    // 渐变描边：左上最亮、右下渐隐，模拟玻璃折射边缘。
    final borderShader = LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [
        borderColor.withValues(alpha: 0.95),
        borderColor.withValues(alpha: 0.22),
        borderColor.withValues(alpha: 0.6),
      ],
    ).createShader(rect);
    canvas.drawRRect(
      rrect.deflate(0.7),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.3
        ..shader = borderShader,
    );

    // 顶部柔和反光。
    final highlightShader = LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.center,
      colors: [
        highlightColor.withValues(alpha: 0.18),
        highlightColor.withValues(alpha: 0),
      ],
    ).createShader(rect);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(0, 0, size.width, size.height * 0.55),
        Radius.circular(radius),
      ),
      Paint()..shader = highlightShader,
    );
  }

  @override
  bool shouldRepaint(covariant _GlassEdgePainter oldDelegate) =>
      oldDelegate.radius != radius ||
      oldDelegate.borderColor != borderColor ||
      oldDelegate.highlightColor != highlightColor;
}

/// 渐变文字，用于强调数值。未指定 colors 时跟随当前主题。
class GradientText extends StatelessWidget {
  const GradientText(
    this.text, {
    super.key,
    required this.style,
    this.colors,
  });

  final String text;
  final TextStyle style;
  final List<Color>? colors;

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
    final effective = colors ?? [palette.accentCyan, palette.accentBlue];
    return ShaderMask(
      shaderCallback: (bounds) =>
          LinearGradient(colors: effective).createShader(bounds),
      blendMode: BlendMode.srcIn,
      child: Text(text, style: style),
    );
  }
}

/// 玻璃圆形图标按钮。
class GlassIconButton extends StatelessWidget {
  const GlassIconButton({
    super.key,
    required this.icon,
    required this.onTap,
    this.tooltip,
    this.color,
  });

  final IconData icon;
  final VoidCallback onTap;
  final String? tooltip;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
    return Tooltip(
      message: tooltip ?? '',
      child: Material(
        color: palette.surfaceFill,
        shape: CircleBorder(side: BorderSide(color: palette.cardBorder, width: 1)),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(
            width: 38,
            height: 38,
            child: Icon(icon, size: 19, color: color ?? palette.textPrimary),
          ),
        ),
      ),
    );
  }
}

/// 酷安风格液态玻璃底栏：
/// 强模糊毛玻璃 Dock + 渐变高光描边 + 弹簧滑动胶囊 + 图标缩放 + 点按涟漪。
class LiquidGlassNavBar extends StatefulWidget {
  const LiquidGlassNavBar({
    super.key,
    required this.currentIndex,
    required this.icons,
    required this.activeIcons,
    required this.labels,
    required this.onTap,
  });

  final int currentIndex;
  final List<IconData> icons;
  final List<IconData> activeIcons;
  final List<String> labels;
  final ValueChanged<int> onTap;

  @override
  State<LiquidGlassNavBar> createState() => _LiquidGlassNavBarState();
}

class _LiquidGlassNavBarState extends State<LiquidGlassNavBar>
    with TickerProviderStateMixin {
  late final AnimationController _ripple;
  int? _rippleIndex;

  @override
  void initState() {
    super.initState();
    _ripple = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 520),
    );
  }

  @override
  void dispose() {
    _ripple.dispose();
    super.dispose();
  }

  void _handleTap(int index) {
    setState(() => _rippleIndex = index);
    _ripple.forward(from: 0);
    widget.onTap(index);
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.glass;
    final count = widget.labels.length;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 0, 14, 12),
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(28),
            boxShadow: [
              BoxShadow(
                color: palette.glowColor,
                blurRadius: 24,
                spreadRadius: -6,
                offset: const Offset(0, 6),
              ),
              BoxShadow(
                color: palette.cardShadow,
                blurRadius: 18,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(28),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final itemWidth = constraints.maxWidth / count;
                  return Container(
                    height: 62,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(28),
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [palette.dockTintTop, palette.dockTintBottom],
                      ),
                      border: Border.all(color: palette.dockBorder, width: 1),
                    ),
                    child: Stack(
                      children: [
                        // 弹簧滑动的液态胶囊
                        AnimatedPositioned(
                          duration: const Duration(milliseconds: 430),
                          curve: Curves.easeOutBack,
                          left: widget.currentIndex * itemWidth + 7,
                          top: 5,
                          bottom: 5,
                          width: itemWidth - 14,
                          child: _buildPill(palette),
                        ),
                        // 顶部高光反射
                        Positioned(
                          left: 18,
                          right: 18,
                          top: 0,
                          child: Container(
                            height: 1.2,
                            decoration: BoxDecoration(
                              gradient: LinearGradient(
                                colors: [
                                  Colors.transparent,
                                  palette.cardBorder,
                                  Colors.transparent,
                                ],
                              ),
                            ),
                          ),
                        ),
                        // 点按涟漪
                        if (_rippleIndex != null)
                          AnimatedBuilder(
                            animation: _ripple,
                            builder: (context, _) {
                              final t = _ripple.value;
                              if (t >= 1) return const SizedBox.shrink();
                              return Positioned(
                                left: _rippleIndex! * itemWidth +
                                    itemWidth / 2 -
                                    34,
                                top: 31 - 34,
                                child: Transform.scale(
                                  scale: 0.35 + t * 2.1,
                                  child: Opacity(
                                    opacity: (1 - t) * 0.55,
                                    child: Container(
                                      width: 68,
                                      height: 68,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle,
                                        gradient: RadialGradient(
                                          colors: [
                                            palette.accentCyan.withValues(
                                                alpha: 0.7),
                                            palette.accentCyan.withValues(
                                                alpha: 0),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                        // 图标与文字
                        Positioned.fill(
                          child: Row(
                            children: List.generate(count, (i) {
                              return Expanded(
                                child: _buildItem(i, palette),
                              );
                            }),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPill(GlassPalette palette) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            palette.accentCyan.withValues(alpha: 0.42),
            palette.pillColor,
          ],
        ),
        border: Border.all(
          color: palette.accentCyan.withValues(alpha: 0.65),
          width: 1,
        ),
        boxShadow: [
          BoxShadow(
            color: palette.glowColor,
            blurRadius: 12,
            spreadRadius: -2,
          ),
          BoxShadow(
            color: palette.cardShadow,
            blurRadius: 8,
            offset: const Offset(0, 3),
          ),
        ],
      ),
    );
  }

  Widget _buildItem(int index, GlassPalette palette) {
    final selected = index == widget.currentIndex;
    return Semantics(
      selected: selected,
      button: true,
      label: widget.labels[index],
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => _handleTap(index),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            AnimatedScale(
              scale: selected ? 1.16 : 1.0,
              duration: const Duration(milliseconds: 380),
              curve: Curves.easeOutBack,
              child: Icon(
                selected ? widget.activeIcons[index] : widget.icons[index],
                size: 21,
                color: selected ? palette.textPrimary : palette.textMuted,
              ),
            ),
            const SizedBox(height: 3),
            AnimatedDefaultTextStyle(
              duration: const Duration(milliseconds: 220),
              style: TextStyle(
                fontSize: selected ? 10.5 : 10,
                height: 1,
                color: selected ? palette.textPrimary : palette.textMuted,
                fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
              ),
              child: Text(widget.labels[index]),
            ),
          ],
        ),
      ),
    );
  }
}
