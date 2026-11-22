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

  bool get isDryRun => result == '演练';
  bool get isFailure => result == '失败';
}

class ActionResult {
  final bool ok;
  final bool dryRun;
  final String detail;

  const ActionResult({
    required this.ok,
    required this.dryRun,
    required this.detail,
  });

  factory ActionResult.fromMap(Map<String, dynamic> map) {
    return ActionResult(
      ok: map['ok'] == true,
      dryRun: map['dryRun'] == true,
      detail: map['detail']?.toString() ?? '',
    );
  }
}

class ScheduleState {
  final bool enabled;
  final bool dryRun;

  const ScheduleState({required this.enabled, required this.dryRun});

  factory ScheduleState.fromMap(Map<String, dynamic> map) {
    return ScheduleState(
      enabled: map['enabled'] == true,
      dryRun: map['dryRun'] == true,
    );
  }
}
