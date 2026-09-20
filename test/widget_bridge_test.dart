import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:packetstream_mobile/models/packetstream_summary.dart';
import 'package:packetstream_mobile/models/widget_schedule.dart';
import 'package:packetstream_mobile/services/widget_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('syncs the verified summary to the native widget channel', () async {
    final channel = const MethodChannel(WidgetBridge.channelName);
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return null;
        });

    final summary = PacketStreamSummary(
      482000000,
      '0.0048',
      DateTime.utc(2026, 9, 16, 12, 30),
    );
    final bridge = WidgetBridge(channel: channel);
    await bridge.syncSummary(summary);
    await bridge.clear();
    await bridge.setBackgroundRefresh(enabled: true, intervalMinutes: 30);

    expect(calls[0].method, 'syncWidgetSummary');
    expect(calls[0].arguments, {
      'bytes': 482000000,
      'balance': '0.0048',
      'fetchedAt': summary.fetchedAt.millisecondsSinceEpoch,
    });
    expect(calls[1].method, 'clearWidgetSession');
    expect(calls[2].method, 'setWidgetSchedule');
    expect(calls[2].arguments, {'enabled': true, 'intervalMinutes': 30});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test(
    'decodes the native schedule and keeps invalid intervals safe',
    () async {
      final channel = const MethodChannel(WidgetBridge.channelName);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'getWidgetSchedule') {
              return {'enabled': true, 'intervalMinutes': 180};
            }
            return null;
          });

      final bridge = WidgetBridge(channel: channel);
      expect(
        await bridge.getBackgroundRefresh(),
        const WidgetSchedule(enabled: true, intervalMinutes: 180),
      );
      expect(
        WidgetSchedule.fromMap({'enabled': true, 'intervalMinutes': 17}),
        const WidgetSchedule(enabled: true, intervalMinutes: 60),
      );
      expect(
        const WidgetSchedule(
          enabled: false,
          intervalMinutes: 60,
        ).copyWith(enabled: true),
        const WidgetSchedule(enabled: true, intervalMinutes: 60),
      );
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    },
  );
}
