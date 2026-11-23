import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wifi_auto_reset/models/models.dart';
import 'package:wifi_auto_reset/services/native_bridge.dart';
import 'package:wifi_auto_reset/utils/format.dart';
import 'package:wifi_auto_reset/widgets/glass.dart';

void main() {
  group('formatSpeed', () {
    test('零值与小数值', () {
      expect(formatSpeed(0), '0 B/s');
      expect(formatSpeed(-1), '0 B/s');
      expect(formatSpeed(512), '512 B/s');
    });

    test('KB 与 MB 换算', () {
      expect(formatSpeed(2048), '2.0 KB/s');
      expect(formatSpeed(2 * 1024 * 1024), '2.00 MB/s');
    });
  });

  group('nextRunAt', () {
    test('60 分钟对齐到整点后 5 秒', () {
      final now = DateTime(2026, 11, 22, 10, 34, 12);
      final next = nextRunAt(now, 60);
      expect(next.hour, 11);
      expect(next.minute, 0);
      expect(next.second, 5);
    });

    test('自定义周期按纪元对齐', () {
      final now = DateTime(2026, 11, 22, 10, 7, 0);
      final next = nextRunAt(now, 15);
      expect(next.isAfter(now), isTrue);
      expect(next.minute % 15, 0);
      expect(next.second, 5);
      expect(next.difference(now).inMinutes, lessThan(15));
    });

    test('跨天边界仍然正确', () {
      final now = DateTime(2026, 11, 22, 23, 50, 0);
      final next = nextRunAt(now, 60);
      expect(next.day, 23);
      expect(next.hour, 0);
      expect(next.minute, 0);
      expect(next.second, 5);
    });
  });

  group('localizeCarrier', () {
    test('国内三大运营商映射为中文', () {
      expect(localizeCarrier('CHN UNICOM'), '中国联通');
      expect(localizeCarrier('CHINA MOBILE'), '中国移动');
      expect(localizeCarrier('CHN TELECOM'), '中国电信');
      expect(localizeCarrier('  chn  unicom  '), '中国联通');
    });

    test('已是中文或未知运营商不改写', () {
      expect(localizeCarrier('中国联通'), '中国联通');
      expect(localizeCarrier('Some ISP'), 'Some ISP');
      expect(localizeCarrier(''), '--');
    });
  });

  group('decodeUtf16Hex', () {
    test('解码固件返回的 SSID', () {
      expect(
        decodeUtf16Hex('004D006900460069002D0032003800310046'),
        'MiFi-281F',
      );
    });

    test('空字符串与非十六进制容错', () {
      expect(decodeUtf16Hex(''), '');
      expect(decodeUtf16Hex('zzzz'), '');
    });
  });

  group('NativeBridge 类型转换', () {
    test('asMap 处理原生 Map 与 null', () {
      expect(NativeBridge.asMap({'a': 1}), {'a': 1});
      expect(NativeBridge.asMap(null), isEmpty);
    });

    test('asMapList 转换日志列表', () {
      final list = NativeBridge.asMapList([
        {'result': '已发送', 'action': '恢复出厂设置'},
      ]);
      expect(list.length, 1);
      expect(LogEntry.fromMap(list.first).isSuccess, isTrue);
    });
  });

  group('模型解析', () {
    test('LogEntry 失败标记', () {
      final entry = LogEntry.fromMap({
        'time': '2026-11-22 16:00:00',
        'action': '重启设备',
        'mode': '定时',
        'result': '失败',
        'detail': 'timeout',
      });
      expect(entry.isFailure, isTrue);
      expect(entry.isSuccess, isFalse);
      expect(entry.detail, 'timeout');
    });

    test('LogEntry 成功标记', () {
      final entry = LogEntry.fromMap({
        'time': '2026-11-22 17:00:00',
        'action': '恢复出厂设置',
        'mode': '定时',
        'result': '已发送',
        'detail': 'ok',
      });
      expect(entry.isSuccess, isTrue);
      expect(entry.isFailure, isFalse);
    });

    test('ActionResult 不再包含演练字段', () {
      final result = ActionResult.fromMap({
        'ok': true,
        'detail': 'success',
      });
      expect(result.ok, isTrue);
      expect(result.detail, 'success');
    });

    test('ScheduleState 解析 enabled 与自定义周期', () {
      final state = ScheduleState.fromMap({'enabled': true});
      expect(state.enabled, isTrue);
      expect(state.intervalMinutes, 60);

      final custom = ScheduleState.fromMap({
        'enabled': true,
        'intervalMinutes': 15,
      });
      expect(custom.intervalMinutes, 15);
      expect(custom.copyWith(enabled: false).enabled, isFalse);
      expect(
        ScheduleState.fromMap({'enabled': false, 'intervalMinutes': 99999})
            .intervalMinutes,
        1440,
      );
      final off = ScheduleState.fromMap({'enabled': false});
      expect(off.enabled, isFalse);
    });

    test('DeviceStatus 解析固件状态字段', () {
      final status = DeviceStatus.fromMap({
        'battery_percent': '60',
        'battery_charge': '1',
        'battery_charging': '0',
        'signal_quality': '5',
        'total_signal_quality': '5',
        'rssi': '-77',
        'wifi_clients_num': '2',
        'run_seconds': '2482',
      });
      expect(status.batteryPercent, 60);
      expect(status.hasBattery, isTrue);
      expect(status.charging, isTrue);
      expect(status.fullyCharged, isFalse);
      expect(status.signalQuality, 5);
      expect(status.signalLabel, '极好');
      expect(status.rssi, -77);
      expect(status.wifiClients, 2);
      expect(status.uptimeLabel, '41 分钟');

      final empty = DeviceStatus.fromMap({'result': '1', 'msg': 'api not found'});
      expect(empty.hasBattery, isFalse);
      expect(empty.signalLabel, '未知');
      expect(empty.uptimeLabel, '--');
    });
  });

  group('玻璃主题令牌', () {
    test('关键令牌互不相同且圆角为正', () {
      expect(GlassTokens.accentCyan, isNot(GlassTokens.accentBlue));
      expect(GlassTokens.accentBlue, isNot(GlassTokens.accentPurple));
      expect(GlassTokens.accentCyan, isNot(GlassPalette.dark.textPrimary));
      expect(GlassTokens.radius, greaterThan(0));
    });

    test('渐变文字默认跟随主题，可显式覆盖', () {
      final widget = GradientText(
        '1.2 MB/s',
        style: const TextStyle(fontSize: 24),
      );
      expect(widget.text, '1.2 MB/s');
      expect(widget.colors, isNull);

      final custom = GradientText(
        '1.2 MB/s',
        style: const TextStyle(fontSize: 24),
        colors: const [GlassTokens.accentCyan, GlassTokens.accentBlue],
      );
      expect(custom.colors, hasLength(2));
      expect(custom.colors!.first, GlassTokens.accentCyan);
    });

    test('浅色 Dock 背景必须半透明才能呈现玻璃折射', () {
      expect(GlassPalette.light.dockTintTop.alpha, lessThan(255));
      expect(GlassPalette.light.dockTintBottom.alpha, lessThan(255));
      expect(GlassPalette.dark.dockTintTop.alpha, lessThan(255));
    });

    testWidgets('液态玻璃底栏可渲染并切换选中项', (tester) async {
      var index = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GlassTheme(
              palette: GlassPalette.light,
              child: StatefulBuilder(
                builder: (context, setState) => LiquidGlassNavBar(
                  currentIndex: index,
                  icons: const [
                    Icons.space_dashboard_outlined,
                    Icons.schedule_outlined,
                  ],
                  activeIcons: const [Icons.space_dashboard, Icons.schedule],
                  labels: const ['仪表盘', '自动化'],
                  onTap: (i) => setState(() => index = i),
                ),
              ),
            ),
          ),
        ),
      );
      expect(find.text('仪表盘'), findsOneWidget);
      await tester.tap(find.text('自动化'));
      await tester.pumpAndSettle();
      final selected = tester.widget<Semantics>(
        find.byWidgetPredicate(
          (widget) => widget is Semantics && widget.label == '自动化',
        ),
      );
      expect(selected.selected, isTrue);
    });

    test('明暗色板文字与背景对比方向正确', () {
      expect(GlassPalette.dark.isDark, isTrue);
      expect(GlassPalette.light.isDark, isFalse);
      expect(GlassPalette.dark.textPrimary.computeLuminance(),
          greaterThan(GlassPalette.dark.bgTop.computeLuminance()));
      expect(GlassPalette.light.textPrimary.computeLuminance(),
          lessThan(GlassPalette.light.bgTop.computeLuminance()));
      expect(GlassPalette.light.accentBlue, isNot(GlassTokens.accentBlue));
    });
  });
}
