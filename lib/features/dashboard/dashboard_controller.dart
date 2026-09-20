import 'package:flutter/foundation.dart';
import '../../core/summary_store.dart';
import '../../models/packetstream_summary.dart';
import '../../services/packetstream_repository.dart';

enum DashboardStatus {
  initial,
  loading,
  loaded,
  refreshing,
  offline,
  authenticationExpired,
  error,
}

class DashboardController extends ChangeNotifier {
  DashboardController(this.repository, this.store);
  final PacketStreamRepository repository;
  final SummaryStore store;
  PacketStreamSummary? summary;
  DashboardStatus status = DashboardStatus.initial;
  Future<void>? _request;
  bool _disposed = false;
  int _generation = 0;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> initialize() async {
    summary = await store.read();
    await refresh();
  }

  Future<bool> applyExternalSummary(PacketStreamSummary? external) async {
    if (external == null ||
        (summary != null && !external.fetchedAt.isAfter(summary!.fetchedAt))) {
      return false;
    }
    summary = external;
    status = DashboardStatus.loaded;
    try {
      await store.write(external);
    } catch (_) {
      /* In-memory data remains usable. */
    }
    _notify();
    return true;
  }

  Future<void> refresh() =>
      _request ??= _refresh().whenComplete(() => _request = null);
  Future<void> _refresh() async {
    final generation = _generation;
    status = summary == null
        ? DashboardStatus.loading
        : DashboardStatus.refreshing;
    _notify();
    try {
      final result = await repository.getSummary();
      if (generation != _generation) return;
      summary = result;
      status = DashboardStatus.loaded;
      try {
        await store.write(result);
      } catch (_) {
        /* In-memory data remains usable. */
      }
    } on SessionExpired {
      if (generation != _generation) return;
      summary = null;
      status = DashboardStatus.authenticationExpired;
      await store.clear();
    } on DashboardChanged {
      if (generation != _generation) return;
      status = DashboardStatus.error;
    } catch (_) {
      if (generation != _generation) return;
      status = DashboardStatus.offline;
    }
    _notify();
  }

  Future<void> logout() async {
    _generation++;
    await repository.logout();
    await store.clear();
    summary = null;
    status = DashboardStatus.authenticationExpired;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
