import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('widget refresh is wired to a private broadcast receiver', () {
    final provider = File(
      'android/app/src/main/kotlin/io/packetstream/mobile/widget/'
      'PacketStreamWidgetProvider.kt',
    ).readAsStringSync();

    expect(
      provider,
      contains('val pendingRefreshIntent = PendingIntent.getBroadcast('),
    );
    expect(
      provider,
      contains(
        'Intent(context, PacketStreamWidgetRefreshReceiver::class.java)',
      ),
    );
    expect(
      provider,
      isNot(contains('val pendingRefreshIntent = PendingIntent.getActivity(')),
    );
  });

  test('the manual refresh receiver is not exported to other apps', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();

    final receiverIndex = manifest.indexOf(
      '.widget.PacketStreamWidgetRefreshReceiver',
    );
    expect(receiverIndex, greaterThan(-1));
    final tagStart = manifest.lastIndexOf('<receiver', receiverIndex);
    final tagEnd = manifest.indexOf('>', receiverIndex);
    final tag = manifest.substring(tagStart, tagEnd);
    expect(tag, contains('android:exported="false"'));
  });

  test('widget layout keeps the full balance row inside the compact card', () {
    final layout = File(
      'android/app/src/main/res/layout/packetstream_widget.xml',
    ).readAsStringSync();

    expect(layout, contains('android:padding="10dp"'));
    expect(layout, contains('android:layout_height="36dp"'));
    expect(layout, contains('android:layout_marginTop="2dp"'));
  });

  test(
    'manual refresh is handed to WorkManager instead of a short broadcast task',
    () {
      final receiver = File(
        'android/app/src/main/kotlin/io/packetstream/mobile/widget/'
        'PacketStreamWidgetRefreshReceiver.kt',
      ).readAsStringSync();

      expect(
        receiver,
        contains('OneTimeWorkRequestBuilder<PacketStreamWidgetWorker>()'),
      );
      expect(receiver, contains('enqueueUniqueWork('));
      expect(receiver, contains('ExistingWorkPolicy.KEEP'));
      expect(receiver, isNot(contains('goAsync()')));
    },
  );

  test('a killed refresh can be recognized as stale', () {
    final store = File(
      'android/app/src/main/kotlin/io/packetstream/mobile/widget/'
      'PacketStreamWidgetStore.kt',
    ).readAsStringSync();

    expect(store, contains('REFRESH_STALE_MILLIS'));
    expect(store, contains('WidgetStatus.REFRESHING'));
    expect(store, contains('WidgetStatus.OFFLINE'));
  });

  test('settings exposes a one-tap background refresh action', () {
    final main = File('lib/main.dart').readAsStringSync();

    expect(main, contains('Enable background refresh now'));
  });
}
