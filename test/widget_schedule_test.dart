import 'package:flutter_test/flutter_test.dart';
import 'package:packetstream_mobile/models/widget_schedule.dart';

void main() {
  test('accepts the supported background refresh frequencies', () {
    expect(WidgetSchedule.allowedIntervals, [15, 30, 60, 180, 360, 720]);
    expect(
      const WidgetSchedule(enabled: true, intervalMinutes: 15).intervalLabel,
      '15 minutes',
    );
    expect(
      const WidgetSchedule(enabled: true, intervalMinutes: 720).intervalLabel,
      '12 hours',
    );
  });

  test('invalid native values fall back to one hour', () {
    expect(
      WidgetSchedule.fromMap({'enabled': true, 'intervalMinutes': 19}),
      const WidgetSchedule(enabled: true, intervalMinutes: 60),
    );
    expect(WidgetSchedule.fromMap(null), const WidgetSchedule.defaults());
  });

  test('copyWith preserves fields that were not changed', () {
    const original = WidgetSchedule(enabled: false, intervalMinutes: 60);
    expect(
      original.copyWith(enabled: true),
      const WidgetSchedule(enabled: true, intervalMinutes: 60),
    );
  });
}
