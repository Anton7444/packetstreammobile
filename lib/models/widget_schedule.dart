class WidgetSchedule {
  const WidgetSchedule({required this.enabled, required this.intervalMinutes});

  static const defaultIntervalMinutes = 60;
  static const allowedIntervals = <int>[15, 30, 60, 180, 360, 720];

  final bool enabled;
  final int intervalMinutes;

  factory WidgetSchedule.fromMap(Object? value) {
    if (value is! Map) return const WidgetSchedule.defaults();
    final enabled = value['enabled'] == true;
    final rawInterval = value['intervalMinutes'];
    final interval = rawInterval is num ? rawInterval.toInt() : null;
    return WidgetSchedule(
      enabled: enabled,
      intervalMinutes: allowedIntervals.contains(interval)
          ? interval!
          : defaultIntervalMinutes,
    );
  }

  const WidgetSchedule.defaults()
    : enabled = false,
      intervalMinutes = defaultIntervalMinutes;

  WidgetSchedule copyWith({bool? enabled, int? intervalMinutes}) {
    final nextInterval = intervalMinutes ?? this.intervalMinutes;
    return WidgetSchedule(
      enabled: enabled ?? this.enabled,
      intervalMinutes: allowedIntervals.contains(nextInterval)
          ? nextInterval
          : defaultIntervalMinutes,
    );
  }

  String get intervalLabel => switch (intervalMinutes) {
    15 => '15 minutes',
    30 => '30 minutes',
    60 => '1 hour',
    180 => '3 hours',
    360 => '6 hours',
    720 => '12 hours',
    _ => '1 hour',
  };

  @override
  bool operator ==(Object other) =>
      other is WidgetSchedule &&
      other.enabled == enabled &&
      other.intervalMinutes == intervalMinutes;

  @override
  int get hashCode => Object.hash(enabled, intervalMinutes);
}
