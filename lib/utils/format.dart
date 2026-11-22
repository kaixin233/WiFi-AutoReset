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
