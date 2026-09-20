import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../models/packetstream_summary.dart';

abstract class SummaryStore {
  Future<PacketStreamSummary?> read();
  Future<void> write(PacketStreamSummary value);
  Future<void> clear();
}

class SecureSummaryStore implements SummaryStore {
  final storage = const FlutterSecureStorage();
  @override
  Future<PacketStreamSummary?> read() async {
    try {
      final value = await storage.read(key: 'summary');
      return value == null
          ? null
          : PacketStreamSummary.fromJson(
              jsonDecode(value) as Map<String, dynamic>,
            );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> write(PacketStreamSummary value) =>
      storage.write(key: 'summary', value: jsonEncode(value.toJson()));
  @override
  Future<void> clear() => storage.delete(key: 'summary');
}
