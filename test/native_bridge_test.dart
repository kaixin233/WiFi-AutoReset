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

    test('ScheduleState 仅解析 enabled', () {
      final state = ScheduleState.fromMap({'enabled': true});
      expect(state.enabled, isTrue);
      final off = ScheduleState.fromMap({'enabled': false});
      expect(off.enabled, isFalse);
    });
  });

  group('玻璃主题令牌', () {
    test('关键令牌互不相同且圆角为正', () {
      expect(GlassTokens.accentCyan, isNot(GlassTokens.accentBlue));
      expect(GlassTokens.accentBlue, isNot(GlassTokens.accentPurple));
      expect(GlassTokens.accentCyan, isNot(GlassTokens.textPrimary));
      expect(GlassTokens.radius, greaterThan(0));
    });

    test('渐变文字使用冷色令牌', () {
      final widget = GradientText(
        '1.2 MB/s',
        style: const TextStyle(fontSize: 24),
      );
      expect(widget.text, '1.2 MB/s');
      expect(widget.colors.length, 2);
      expect(widget.colors.first, GlassTokens.accentCyan);
    });
  });
}
