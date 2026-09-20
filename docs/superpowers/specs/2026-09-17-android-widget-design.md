# Android 2x2 PacketStream Widget Design

## Goal

Add a compact Android home-screen widget that matches the PacketStream app and refreshes bandwidth and balance directly from the widget without opening the app.

## User-visible behavior

- The widget is an Android 2x2 AppWidget.
- The card uses the existing Material 3 visual language: rounded surface, restrained green accent, readable hierarchy, and light/dark resource variants.
- Content is limited to `Bandwidth Shared`, its formatted value, `Balance`, its currency value, and a small last-updated line.
- A refresh icon is placed in the upper-right corner.
- Tapping refresh performs a silent network refresh in the widget receiver and updates the widget in place. It must not launch the Flutter activity.
- Tapping the widget body opens the Flutter app. If the session has expired, the body opens the sign-in flow.
- With no cached summary, the widget shows `Sign in to update` and does not attempt a request.
- While refreshing, the widget shows a small progress/updated state without replacing the values with a full-screen loader.
- If the direct request fails, the widget keeps the last successful values and changes the small status line to `Offline` or `Session expired`.

## Data and security

The existing authenticated WebView remains the login authority. After a successful authenticated dashboard load, native Android code obtains only the PacketStream `app.packetstream.io` cookies through the platform CookieManager. The cookie header is stored in an Android Keystore-backed encrypted store shared by the widget receiver and the Flutter activity. Passwords, tokens in logs, unrelated cookies, and full dashboard HTML are never persisted.

The widget receiver performs a direct HTTPS `GET https://app.packetstream.io/dashboard` with the encrypted cookie header. It parses only the verified dashboard fields: the server-rendered balance and the inline `reportData.exitnode` records for UTC day offsets 0–13. The raw cookie header is never exposed to Dart, widget text, logs, or external intents. A 401/login redirect clears the native cookie copy and marks the widget session expired.

The existing Flutter secure summary cache remains the source for app startup. Every successful app refresh also writes the same summary and current cookie header to the native widget store. Sign out clears both stores and calls `AppWidgetManager` to render the signed-out state.

## Native components

- `PacketStreamWidgetProvider`: handles widget lifecycle, refresh `PendingIntent`, body click, and `goAsync()` network work.
- `PacketStreamWidgetStore`: Keystore-backed encrypted preferences for cookie header and the widget summary/status.
- `PacketStreamWidgetClient`: HTTPS request, redirect/status handling, strict origin/path check, HTML/script extraction, byte aggregation, and summary validation.
- `res/layout/packetstream_widget.xml`: compact two-metric `RemoteViews` layout.
- `res/drawable/packetstream_widget_background.xml` and color resources: rounded light/dark card and status colors.
- `res/xml/packetstream_widget_info.xml`: 2x2 sizing, preview metadata, and resize constraints.
- `AndroidManifest.xml`: receiver declaration and `INTERNET` permission.
- `MainActivity`/Flutter bridge: writes the authenticated cookie header and summary after WebView success, clears them on sign out, and handles widget body intents.

## Refresh and lifecycle

The refresh icon sends an explicit broadcast to `PacketStreamWidgetProvider`. The provider uses `goAsync()` with a bounded request timeout, prevents overlapping refreshes per widget instance, updates the widget with the new values, and releases the pending result. It does not schedule periodic background work or start a persistent notification/service.

The receiver uses the most recent encrypted cookie header. If Android has killed the Flutter process, refresh still works because the native cookie and summary store are independent of Dart memory. If the cookie has expired, the receiver stops retrying and displays `Session expired`; only the widget body click opens the app for re-authentication.

## Validation

- Dart tests cover widget bridge payload validation, precision-preserving summary serialization, and sign-out clearing.
- JVM tests cover cookie-header origin filtering, HTML extraction, 14-day byte aggregation, malformed responses, redirect/session expiry, and timeout behavior.
- Android build verifies the receiver, resources, manifest, and release APK.
- Device verification covers adding a 2x2 widget, light/dark appearance, direct refresh without activity launch, offline cached values, expired-session state, body-to-app navigation, and sign-out clearing.

## Scope limits

This is Android-only in this change. iOS will require a separate WidgetKit extension and a matching secure data bridge. The widget does not implement a background service, persistent notification, lifetime traffic history, charts, or any additional dashboard metrics.
