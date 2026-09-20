import 'package:flutter/services.dart';

import '../models/packetstream_summary.dart';
import '../models/widget_schedule.dart';

class WidgetBridge {
  WidgetBridge({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName = 'io.packetstream.mobile/widget';
  static const _syncMethod = 'syncWidgetSummary';
  static const _clearMethod = 'clearWidgetSession';
  static const _getScheduleMethod = 'getWidgetSchedule';
  static const _getSummaryMethod = 'getWidgetSummary';
  static const _setScheduleMethod = 'setWidgetSchedule';

  final MethodChannel _channel;

  Future<void> syncSummary(PacketStreamSummary summary) async {
    try {
      await _channel.invokeMethod<void>(_syncMethod, {
        'bytes': summary.bandwidthBytes,
        'balance': summary.balance,
        'fetchedAt': summary.fetchedAt.toUtc().millisecondsSinceEpoch,
      });
    } on MissingPluginException {
      // Widgets are Android-only; other platforms simply keep the app flow.
    } catch (_) {
      // A widget sync failure must not make the dashboard refresh fail.
    }
  }

  Future<void> clear() async {
    try {
      await _channel.invokeMethod<void>(_clearMethod);
    } on MissingPluginException {
      // Widgets are Android-only; other platforms simply keep the app flow.
    } catch (_) {
      // A widget cleanup failure must not make logout fail.
    }
  }

  Future<PacketStreamSummary?> readLatestSummary() async {
    try {
      final value = await _channel.invokeMethod<Object?>(_getSummaryMethod);
      if (value is! Map) return null;
      return PacketStreamSummary.fromJson({
        'bytes': value['bytes'],
        'balance': value['balance'],
        'at': DateTime.fromMillisecondsSinceEpoch(
          (value['fetchedAt'] as num).toInt(),
          isUtc: true,
        ).toIso8601String(),
      });
    } on MissingPluginException {
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<WidgetSchedule> getBackgroundRefresh() async {
    try {
      final value = await _channel.invokeMethod<Object?>(_getScheduleMethod);
      return WidgetSchedule.fromMap(value);
    } on MissingPluginException {
      return const WidgetSchedule.defaults();
    }
  }

  Future<void> setBackgroundRefresh({
    required bool enabled,
    required int intervalMinutes,
  }) async {
    try {
      await _channel.invokeMethod<void>(_setScheduleMethod, {
        'enabled': enabled,
        'intervalMinutes': intervalMinutes,
      });
    } on MissingPluginException {
      // Widgets are Android-only; other platforms simply keep the app flow.
    }
  }
}
