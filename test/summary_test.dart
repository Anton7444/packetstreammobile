import 'package:flutter_test/flutter_test.dart';
import 'package:packetstream_mobile/core/formatting.dart';
import 'package:packetstream_mobile/models/packetstream_summary.dart';

void main() {
  test('formats bandwidth using website conversion and units', () {
    expect(formatBytes(823), '823 Bytes');
    expect(formatBytes(481000), '469.7 KB');
    expect(formatBytes(482000000), '459.7 MB');
    expect(formatBytes(1420000000), '1.322 GB');
  });
  test('keeps fractional cents supplied by the source', () {
    expect(formatBalance('0.0048'), '\$0.0048');
    expect(formatBalance('0.0482'), '\$0.0482');
    expect(formatBalance('1.2400'), '\$1.24');
    expect(formatBalance('0'), '\$0.00');
  });
  test('malformed summaries cannot silently become zero', () {
    expect(
      () => PacketStreamSummary.fromJson({'bytes': -1}),
      throwsFormatException,
    );
    expect(() => formatBalance('NaN'), throwsFormatException);
  });
  test('cache round trip preserves precision and timestamp', () {
    final value = PacketStreamSummary(
      131000000,
      '0.004812',
      DateTime.utc(2026, 9, 16),
    );
    final restored = PacketStreamSummary.fromJson(value.toJson());
    expect(restored.balance, value.balance);
    expect(restored.bandwidthBytes, value.bandwidthBytes);
    expect(restored.fetchedAt, value.fetchedAt);
  });
}
