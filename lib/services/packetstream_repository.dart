import '../models/packetstream_summary.dart';

abstract class PacketStreamRepository {
  Future<PacketStreamSummary> getSummary();
  Future<bool> isAuthenticated();
  Future<void> logout();
}

class SessionExpired implements Exception {}

class DashboardUnavailable implements Exception {}

class DashboardChanged implements Exception {}
