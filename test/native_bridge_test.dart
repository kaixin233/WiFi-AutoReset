import 'package:flutter_test/flutter_test.dart';
import 'package:wifi_auto_reset/models/models.dart';
import 'package:wifi_auto_reset/services/native_bridge.dart';
import 'package:wifi_auto_reset/utils/format.dart';

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
        {'result': '演练', 'action': '恢复出厂设置'},
      ]);
      expect(list.length, 1);
      expect(LogEntry.fromMap(list.first).isDryRun, isTrue);
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
      expect(entry.detail, 'timeout');
    });

    test('ActionResult 演练标记', () {
      final result = ActionResult.fromMap({
        'ok': true,
        'dryRun': true,
        'detail': '演练模式：未发送请求',
      });
      expect(result.ok, isTrue);
      expect(result.dryRun, isTrue);
    });

    test('ScheduleState 解析', () {
      final state = ScheduleState.fromMap({'enabled': true, 'dryRun': false});
      expect(state.enabled, isTrue);
      expect(state.dryRun, isFalse);
    });
  });
}
