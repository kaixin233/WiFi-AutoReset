import 'dart:convert';

import 'package:flutter/services.dart';

import '../models/models.dart';

class NativeBridge {
  static const MethodChannel _channel =
      MethodChannel('com.kaixin233.wifiautoreset/native');
  static const EventChannel _speedChannel =
      EventChannel('com.kaixin233.wifiautoreset/speed');

  static Map<String, dynamic> asMap(Object? value) {
    if (value is Map) {
      return value.map((k, v) => MapEntry<String, dynamic>(k.toString(), v));
    }
    return <String, dynamic>{};
  }

  static List<Map<String, dynamic>> asMapList(Object? value) {
    if (value is List) return value.map((e) => asMap(e)).toList();
    return <Map<String, dynamic>>[];
  }

  static Stream<Map<String, dynamic>> get speedStream =>
      _speedChannel.receiveBroadcastStream().map(asMap);

  static Future<bool> hasUsageAccess() async {
    final value = await _channel.invokeMethod<bool>('hasUsageAccess');
    return value ?? false;
  }

  static Future<void> requestUsageAccess() =>
      _channel.invokeMethod<void>('requestUsageAccess');

  static Future<Map<String, dynamic>> foregroundApp() async {
    return asMap(await _channel.invokeMethod('foregroundApp'));
  }

  static Future<Map<String, dynamic>> deviceInfo() async {
    final raw = await _channel.invokeMethod<String>('deviceInfo');
    if (raw == null || raw.isEmpty) return <String, dynamic>{};
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) return asMap(decoded);
    } catch (_) {
      // 设备可能返回非 JSON 文本，忽略即可。
    }
    return <String, dynamic>{};
  }

  static Future<bool> gatewayReachable() async {
    final value = await _channel.invokeMethod<bool>('gatewayReachable');
    return value ?? false;
  }

  static Future<ActionResult> factoryReset() async {
    final map = await _channel.invokeMethod('factoryReset');
    return ActionResult.fromMap(asMap(map));
  }

  static Future<ActionResult> reboot() async {
    final map = await _channel.invokeMethod('reboot');
    return ActionResult.fromMap(asMap(map));
  }

  static Future<ActionResult> resetTraffic() async {
    final map = await _channel.invokeMethod('resetTraffic');
    return ActionResult.fromMap(asMap(map));
  }

  static Future<ScheduleState> configureSchedule({
    required bool enabled,
  }) async {
    final map = await _channel.invokeMethod('configureSchedule', {
      'enabled': enabled,
    });
    return ScheduleState.fromMap(asMap(map));
  }

  static Future<ScheduleState> getSchedule() async {
    return ScheduleState.fromMap(
      asMap(await _channel.invokeMethod('getSchedule')),
    );
  }

  static Future<List<LogEntry>> getLogs() async {
    return asMapList(await _channel.invokeMethod('getLogs'))
        .map(LogEntry.fromMap)
        .toList();
  }

  static Future<void> clearLogs() => _channel.invokeMethod<void>('clearLogs');
}
