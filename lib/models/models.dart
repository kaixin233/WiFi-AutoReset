class LogEntry {
  final String time;
  final String action;
  final String mode;
  final String result;
  final String detail;

  const LogEntry({
    required this.time,
    required this.action,
    required this.mode,
    required this.result,
    required this.detail,
  });

  factory LogEntry.fromMap(Map<String, dynamic> map) {
    return LogEntry(
      time: map['time']?.toString() ?? '',
      action: map['action']?.toString() ?? '',
      mode: map['mode']?.toString() ?? '',
      result: map['result']?.toString() ?? '',
      detail: map['detail']?.toString() ?? '',
    );
  }

  bool get isFailure => result == '失败';

  bool get isSuccess => !isFailure;
}

class ActionResult {
  final bool ok;
  final String detail;

  const ActionResult({
    required this.ok,
    required this.detail,
  });

  factory ActionResult.fromMap(Map<String, dynamic> map) {
    return ActionResult(
      ok: map['ok'] == true,
      detail: map['detail']?.toString() ?? '',
    );
  }
}

class ScheduleState {
  /// 是否启用定时任务。
  final bool enabled;

  /// 执行周期（分钟）。5 ~ 1440。
  final int intervalMinutes;

  const ScheduleState({
    required this.enabled,
    this.intervalMinutes = 60,
  });

  factory ScheduleState.fromMap(Map<String, dynamic> map) {
    final parsed = int.tryParse(map['intervalMinutes']?.toString() ?? '');
    final interval = parsed == null ? 60 : parsed.clamp(5, 1440).toInt();
    return ScheduleState(
      enabled: map['enabled'] == true,
      intervalMinutes: interval,
    );
  }

  ScheduleState copyWith({bool? enabled, int? intervalMinutes}) {
    return ScheduleState(
      enabled: enabled ?? this.enabled,
      intervalMinutes: intervalMinutes ?? this.intervalMinutes,
    );
  }
}

/// 固件 json_status_info 的只读状态快照。
class DeviceStatus {
  final int batteryPercent;
  final bool charging;
  final bool fullyCharged;
  final int signalQuality;
  final int totalSignalQuality;
  final int rssi;
  final int wifiClients;
  final int runSeconds;

  const DeviceStatus({
    this.batteryPercent = -1,
    this.charging = false,
    this.fullyCharged = false,
    this.signalQuality = 0,
    this.totalSignalQuality = 5,
    this.rssi = 0,
    this.wifiClients = 0,
    this.runSeconds = 0,
  });

  factory DeviceStatus.fromMap(Map<String, dynamic> map) {
    final charge = map['battery_charge']?.toString() ?? '';
    final chargeFlag = map['battery_charging']?.toString() ?? '';
    return DeviceStatus(
      batteryPercent: _toInt(map['battery_percent'], -1),
      charging: charge == '1' || chargeFlag == '1',
      fullyCharged: charge == '2' || charge == '3',
      signalQuality: _toInt(map['signal_quality'], 0),
      totalSignalQuality: _toInt(map['total_signal_quality'], 5),
      rssi: _toInt(map['rssi'], 0),
      wifiClients: _toInt(map['wifi_clients_num'], 0),
      runSeconds: _toInt(map['run_seconds'], 0),
    );
  }

  static int _toInt(Object? value, int fallback) =>
      int.tryParse(value?.toString() ?? '') ?? fallback;

  bool get hasBattery => batteryPercent >= 0 && batteryPercent <= 100;

  bool get hasSignal => totalSignalQuality > 0;

  /// 返回信号强度描述，RSSI 越接近 0 越好。
  String get signalLabel {
    if (!hasSignal) return '未知';
    if (signalQuality <= 0) return '无服务';
    final ratio = signalQuality / totalSignalQuality;
    if (ratio >= 0.8) return '极好';
    if (ratio >= 0.6) return '良好';
    if (ratio >= 0.4) return '一般';
    return '较弱';
  }

  /// 已运行时长，例如 "2 天 3 小时" / "45 分钟"。
  String get uptimeLabel {
    if (runSeconds <= 0) return '--';
    final days = runSeconds ~/ 86400;
    final hours = (runSeconds % 86400) ~/ 3600;
    final minutes = (runSeconds % 3600) ~/ 60;
    if (days > 0) return '$days 天 $hours 小时';
    if (hours > 0) return '$hours 小时 $minutes 分';
    return '$minutes 分钟';
  }
}
