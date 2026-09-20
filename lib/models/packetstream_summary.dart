import '../core/formatting.dart';

class PacketStreamSummary {
  final int bandwidthBytes;
  // Decimal string avoids destroying monetary precision in binary floating point.
  final String balance;
  final DateTime fetchedAt;
  PacketStreamSummary(this.bandwidthBytes, this.balance, this.fetchedAt) {
    if (bandwidthBytes < 0) throw const FormatException('Invalid bandwidth');
    formatBalance(balance);
  }
  Map<String, dynamic> toJson() => {
    'bytes': bandwidthBytes,
    'balance': balance,
    'at': fetchedAt.toUtc().toIso8601String(),
  };
  factory PacketStreamSummary.fromJson(Map<String, dynamic> json) {
    final bytes = json['bytes'];
    final balance = json['balance'];
    final at = json['at'];
    if (bytes is! int ||
        balance is! String ||
        at is! String ||
        DateTime.tryParse(at) == null) {
      throw const FormatException('Invalid summary');
    }
    return PacketStreamSummary(bytes, balance, DateTime.parse(at));
  }
}
