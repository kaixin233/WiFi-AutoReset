import 'package:flutter/material.dart';

/// 液体玻璃设计令牌：深空底色 + 冷色高光。
class GlassTokens {
  static const Color bgTop = Color(0xFF0B1024);
  static const Color bgMid = Color(0xFF141C3C);
  static const Color bgBottom = Color(0xFF090D1E);

  static const Color textPrimary = Color(0xFFF3F6FF);
  static const Color textMuted = Color(0xAAA9B8D6);

  static const Color accentCyan = Color(0xFF54E0E8);
  static const Color accentBlue = Color(0xFF6C9BFF);
  static const Color accentPurple = Color(0xFFB18CFF);

  static const Color success = Color(0xFF62E3AF);
  static const Color danger = Color(0xFFFF7B7B);
  static const Color warn = Color(0xFFFFC86B);

  static const double radius = 22;
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
    return Stack(
      children: [
        const Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  GlassTokens.bgTop,
                  GlassTokens.bgMid,
                  GlassTokens.bgBottom,
                ],
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
                    color: GlassTokens.accentCyan,
                    opacity: 0.26,
                  ),
                  _orb(
                    top: 90 - t * 40,
                    right: -70 + t * 22,
                    size: 300,
                    color: GlassTokens.accentPurple,
                    opacity: 0.24,
                  ),
                  _orb(
                    bottom: -90 + t * 44,
                    left: 40 - t * 30,
                    size: 320,
                    color: GlassTokens.accentBlue,
                    opacity: 0.20,
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
    this.tintOpacity = 0.11,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final double radius;
  final double blur;
  final double tintOpacity;

  @override
  Widget build(BuildContext context) {
    final border = BorderRadius.all(Radius.circular(radius));
    return Container(
      margin: margin,
      decoration: BoxDecoration(
        borderRadius: border,
        boxShadow: [
          BoxShadow(
            color: const Color(0x66000000),
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
            painter: _GlassEdgePainter(radius: radius),
            child: Container(
              padding: padding,
              decoration: BoxDecoration(
                borderRadius: border,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Colors.white.withValues(alpha: tintOpacity + 0.05),
                    Colors.white.withValues(alpha: tintOpacity),
                  ],
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
  _GlassEdgePainter({required this.radius});

  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final rrect = RRect.fromRectAndRadius(rect, Radius.circular(radius));

    // 渐变描边：左上最亮、右下渐隐，模拟玻璃折射边缘。
    final borderShader = const LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [
        Color(0x99FFFFFF),
        Color(0x14FFFFFF),
        Color(0x4DFFFFFF),
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
    final highlightShader = const LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.center,
      colors: [Color(0x2EFFFFFF), Color(0x00FFFFFF)],
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
      oldDelegate.radius != radius;
}

/// 渐变文字，用于强调数值。
class GradientText extends StatelessWidget {
  const GradientText(
    this.text, {
    super.key,
    required this.style,
    this.colors = const [GlassTokens.accentCyan, GlassTokens.accentBlue],
  });

  final String text;
  final TextStyle style;
  final List<Color> colors;

  @override
  Widget build(BuildContext context) {
    return ShaderMask(
      shaderCallback: (bounds) =>
          LinearGradient(colors: colors).createShader(bounds),
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
    this.color = GlassTokens.textPrimary,
  });

  final IconData icon;
  final VoidCallback onTap;
  final String? tooltip;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip ?? '',
      child: Material(
        color: Colors.white.withValues(alpha: 0.10),
        shape: const CircleBorder(
          side: BorderSide(color: Color(0x3DFFFFFF), width: 1),
        ),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(
            width: 38,
            height: 38,
            child: Icon(icon, size: 19, color: color),
          ),
        ),
      ),
    );
  }
}
