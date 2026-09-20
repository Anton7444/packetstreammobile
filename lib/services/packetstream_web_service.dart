import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../models/packetstream_summary.dart';
import 'packetstream_repository.dart';
import 'widget_bridge.dart';
import 'web_resource_error_policy.dart';

const dashboardUrl = 'https://app.packetstream.io/dashboard';

class PacketStreamWebService implements PacketStreamRepository {
  late final WebViewController webView;
  final signingIn = ValueNotifier(false);
  final authError = ValueNotifier<String?>(null);
  final WidgetBridge widgetBridge;
  Completer<PacketStreamSummary>? _pending;
  Completer<bool>? _login;
  String? _mainFrameUrl;
  PacketStreamWebService({WidgetBridge? widgetBridge})
    : widgetBridge = widgetBridge ?? WidgetBridge() {
    webView = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (request.url == 'about:blank' ||
                (uri?.scheme == 'https' &&
                    uri?.host == 'app.packetstream.io')) {
              _mainFrameUrl = request.url;
              return NavigationDecision.navigate;
            }
            authError.value = 'This link cannot open inside sign-in.';
            return NavigationDecision.prevent;
          },
          onPageFinished: _pageFinished,
          onWebResourceError: (error) {
            if (error.isForMainFrame == true) {
              _fail(DashboardUnavailable());
              if (signingIn.value) {
                authError.value =
                    'Unable to load PacketStream. Check your connection and retry.';
              }
            }
          },
          onHttpError: (error) {
            final failure = dashboardHttpError(
              error.response?.statusCode,
              requestUrl: error.request?.uri.toString(),
              currentUrl: _mainFrameUrl,
            );
            if (failure == DashboardHttpFailure.sessionExpired) {
              _fail(SessionExpired());
            } else if (failure == DashboardHttpFailure.unavailable) {
              _fail(DashboardUnavailable());
            }
          },
        ),
      );
  }
  void _fail(Object error) {
    final pending = _pending;
    if (pending != null && !pending.isCompleted) pending.completeError(error);
  }

  Future<void> _pageFinished(String url) async {
    final uri = Uri.tryParse(url);
    if (uri?.scheme != 'https' || uri?.host != 'app.packetstream.io') return;
    _mainFrameUrl = url;
    if (uri!.path == '/login') {
      if (!signingIn.value) _fail(SessionExpired());
      return;
    }
    if (uri.path != '/dashboard' && uri.path != '/dashboard/') return;
    try {
      var raw = await webView.runJavaScriptReturningResult(
        extractSummaryScript,
      );
      if (raw is String) raw = jsonDecode(raw);
      if (raw is String) raw = jsonDecode(raw);
      if (raw is! Map || raw['ok'] != true) throw DashboardChanged();
      final summary = PacketStreamSummary.fromJson({
        'bytes': raw['bytes'],
        'balance': raw['balance'],
        'at': DateTime.now().toUtc().toIso8601String(),
      });
      unawaited(widgetBridge.syncSummary(summary));
      final pending = _pending;
      if (pending != null && !pending.isCompleted) pending.complete(summary);
      if (_login != null && !_login!.isCompleted) _login!.complete(true);
    } catch (_) {
      _fail(DashboardChanged());
      if (signingIn.value) {
        authError.value =
            'Signed-in page could not be read. PacketStream may have changed its dashboard.';
      }
    }
  }

  Future<bool> signIn() async {
    if (_login != null) return _login!.future;
    authError.value = null;
    _login = Completer<bool>();
    signingIn.value = true;
    try {
      await webView.loadRequest(Uri.parse(dashboardUrl));
      return await _login!.future;
    } finally {
      _login = null;
      signingIn.value = false;
    }
  }

  void cancelSignIn() {
    if (_login != null && !_login!.isCompleted) _login!.complete(false);
  }

  @override
  Future<PacketStreamSummary> getSummary() async {
    if (_pending != null) return _pending!.future;
    final completer = Completer<PacketStreamSummary>();
    _pending = completer;
    try {
      await webView.loadRequest(Uri.parse(dashboardUrl));
      return await completer.future.timeout(const Duration(seconds: 25));
    } finally {
      _pending = null;
    }
  }

  @override
  Future<bool> isAuthenticated() async {
    try {
      await getSummary();
      return true;
    } on SessionExpired {
      return false;
    }
  }

  @override
  Future<void> logout() async {
    cancelSignIn();
    _fail(SessionExpired());
    await WebViewCookieManager().clearCookies();
    await webView.clearLocalStorage();
    await webView.clearCache();
    await webView.loadRequest(Uri.parse('about:blank'));
    await widgetBridge.clear();
  }
}

// Runs only on the verified dashboard origin. Returns two aggregates only.
const extractSummaryScript = r'''
(() => {
  if (location.origin !== 'https://app.packetstream.io' || !/^\/dashboard\/?$/.test(location.pathname)) return JSON.stringify({ok:false});
  const card = document.querySelector('.metric-card-balance');
  const label = card?.querySelector('.metric-title')?.textContent.trim();
  const balanceText = card?.querySelector('h2.default-font')?.textContent.trim();
  const sold = document.querySelector('.metric-card-sold .card-subtitle')?.textContent.trim();
  if (label !== 'Balance' || sold !== 'Last 14 Days' || !/^\$\d[\d,]*(\.\d+)?$/.test(balanceText || '')) return JSON.stringify({ok:false});
  const records = window.reportData?.exitnode;
  if (!Array.isArray(records)) return JSON.stringify({ok:false});
  const now = new Date();
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  let bytes = 0;
  for (const record of records) {
    const date = new Date(record.timestamp);
    if (!Number.isFinite(date.getTime())) return JSON.stringify({ok:false});
    const day = Math.floor((today-Date.UTC(date.getUTCFullYear(),date.getUTCMonth(),date.getUTCDate()))/86400000);
    const up = record.bandwidth?.up, down = record.bandwidth?.down;
    if (!Number.isSafeInteger(up) || !Number.isSafeInteger(down) || up < 0 || down < 0) return JSON.stringify({ok:false});
    if (day >= 0 && day < 14) bytes += up + down;
  }
  if (!Number.isSafeInteger(bytes)) return JSON.stringify({ok:false});
  return JSON.stringify({ok:true,bytes,balance:balanceText.slice(1).replace(/,/g,'')});
})()
''';
