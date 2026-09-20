import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:packetstream_mobile/core/summary_store.dart';
import 'package:packetstream_mobile/features/dashboard/dashboard_controller.dart';
import 'package:packetstream_mobile/features/dashboard/dashboard_page.dart';
import 'package:packetstream_mobile/models/packetstream_summary.dart';
import 'package:packetstream_mobile/services/packetstream_repository.dart';
import 'package:packetstream_mobile/app/packetstream_surface.dart';

class MemoryStore implements SummaryStore {
  PacketStreamSummary? value;
  @override
  Future<PacketStreamSummary?> read() async => value;
  @override
  Future<void> write(PacketStreamSummary summary) async {
    value = summary;
  }

  @override
  Future<void> clear() async {
    value = null;
  }
}

class Repository implements PacketStreamRepository {
  final pending = Completer<PacketStreamSummary>();
  int calls = 0;
  @override
  Future<PacketStreamSummary> getSummary() {
    calls++;
    return pending.future;
  }

  @override
  Future<bool> isAuthenticated() async => true;
  @override
  Future<void> logout() async {}
}

PacketStreamSummary sample() =>
    PacketStreamSummary(482000000, '0.0048', DateTime.utc(2026, 9, 16));
void main() {
  test('concurrent refreshes share one request', () async {
    final repository = Repository();
    final controller = DashboardController(repository, MemoryStore());
    final a = controller.refresh();
    final b = controller.refresh();
    expect(repository.calls, 1);
    repository.pending.complete(sample());
    await Future.wait([a, b]);
    expect(controller.status, DashboardStatus.loaded);
  });
  test('offline launch retains the last successful summary', () async {
    final repository = Repository();
    final store = MemoryStore()..value = sample();
    final controller = DashboardController(repository, store);
    final request = controller.initialize();
    await Future<void>.delayed(Duration.zero);
    repository.pending.completeError(DashboardUnavailable());
    await request;
    expect(controller.status, DashboardStatus.offline);
    expect(controller.summary?.balance, '0.0048');
  });
  test('session expiry clears previous account summary', () async {
    final repository = Repository();
    final store = MemoryStore()..value = sample();
    final controller = DashboardController(repository, store);
    final request = controller.initialize();
    await Future<void>.delayed(Duration.zero);
    repository.pending.completeError(SessionExpired());
    await request;
    expect(controller.status, DashboardStatus.authenticationExpired);
    expect(controller.summary, isNull);
    expect(store.value, isNull);
  });
  testWidgets('refresh completion updates the visible metric cards', (
    tester,
  ) async {
    final repository = Repository();
    final controller = DashboardController(repository, MemoryStore());
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: DashboardPage(controller: controller)),
      ),
    );
    final request = controller.refresh();
    await tester.pump();
    repository.pending.complete(sample());
    await request;
    await tester.pumpAndSettle();
    expect(find.text('493.7 MB'), findsOneWidget);
    expect(find.text('\$0.0048'), findsOneWidget);
    expect(find.text('Connected'), findsOneWidget);
  });

  testWidgets(
    'dashboard surface does not mount login content when signed out',
    (tester) async {
      final loginKey = GlobalKey();
      final dashboardKey = GlobalKey();
      await tester.pumpWidget(
        PacketStreamSurface(
          signingIn: false,
          login: SizedBox(key: loginKey),
          dashboard: SizedBox(key: dashboardKey),
        ),
      );
      expect(find.byKey(loginKey), findsNothing);
      expect(find.byKey(dashboardKey), findsOneWidget);
    },
  );
}
