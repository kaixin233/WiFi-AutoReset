String formatSpeed(int bytesPerSecond) {
  if (bytesPerSecond <= 0) return '0 B/s';
  if (bytesPerSecond < 1024) return '$bytesPerSecond B/s';
  final kb = bytesPerSecond / 1024;
  if (kb < 1024) return '${kb.toStringAsFixed(1)} KB/s';
  return '${(kb / 1024).toStringAsFixed(2)} MB/s';
}

/// 下一个执行时刻：按 Unix 纪元对齐到周期起点再加 5 秒。
/// 必须与 Kotlin AutoResetWorker.nextRunMillis 保持一致。
DateTime nextRunAt(DateTime now, int intervalMinutes) {
  final slotMs = intervalMinutes * 60 * 1000;
  final nowMs = now.millisecondsSinceEpoch;
  return DateTime.fromMillisecondsSinceEpoch(
    ((nowMs ~/ slotMs) + 1) * slotMs + 5000,
  );
}

/// 固件返回的运营商字段是英文，映射为国内常用中文名称。
/// 未命中映射时原样返回，避免把未知运营商改错。
String localizeCarrier(String value) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) return '--';
  // 已经是中文时直接返回。
  if (trimmed.codeUnits.any((unit) => unit >= 0x4E00 && unit <= 0x9FFF)) {
    return trimmed;
  }
  final normalized = trimmed.toUpperCase().replaceAll(RegExp(r'\s+'), ' ');
  const names = <String, String>{
    'CHN UNICOM': '中国联通',
    'CHINA UNICOM': '中国联通',
    'UNICOM': '中国联通',
    'CU': '中国联通',
    'CHN MOBILE': '中国移动',
    'CHINA MOBILE': '中国移动',
    'CHINA MOBILE COMMUNICATIONS': '中国移动',
    'CMCC': '中国移动',
    'MOBILE': '中国移动',
    'CHN TELECOM': '中国电信',
    'CHINA TELECOM': '中国电信',
    'TELECOM': '中国电信',
    'CT': '中国电信',
  };
  return names[normalized] ?? trimmed;
}

/// 固件把 SSID 以 UTF-16 code unit 的 4 位十六进制拼接返回，
/// 例如 004D00690046... 解码后是 MiFi-281F。
String decodeUtf16Hex(String hex) {
  if (hex.isEmpty) return '';
  final codes = <int>[];
  for (var i = 0; i + 3 < hex.length; i += 4) {
    final value = int.tryParse(hex.substring(i, i + 4), radix: 16);
    if (value != null) codes.add(value);
  }
  return String.fromCharCodes(codes);
}
