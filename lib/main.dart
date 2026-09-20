import 'dart:async';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'app/packetstream_surface.dart';
import 'app/theme.dart';
import 'core/summary_store.dart';
import 'features/dashboard/dashboard_controller.dart';
import 'features/dashboard/dashboard_page.dart';
import 'models/widget_schedule.dart';
import 'services/packetstream_web_service.dart';

void main() => runApp(const PacketStreamApp());

class PacketStreamApp extends StatefulWidget {
  const PacketStreamApp({super.key});
  @override
  State<PacketStreamApp> createState() => _PacketStreamAppState();
}

class _PacketStreamAppState extends State<PacketStreamApp>
    with WidgetsBindingObserver {
  late final PacketStreamWebService service;
  late final DashboardController controller;
  final store = SecureSummaryStore();
  ThemeMode themeMode = ThemeMode.system;
  DateTime? pausedAt;
  bool hadAccount = false;
  bool signingOut = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    service = PacketStreamWebService();
    controller = DashboardController(service, store);
    controller.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _bootstrap();
    });
    _restoreTheme();
  }

  Future<void> _bootstrap() async {
    final cached = await store.read();
    if (!mounted) return;
    if (cached != null) await controller.applyExternalSummary(cached);
    await controller.applyExternalSummary(
      await service.widgetBridge.readLatestSummary(),
    );
    if (controller.summary != null) setState(() => hadAccount = true);
    if (controller.summary == null) return;
    await controller.initialize();
  }

  Future<void> _restoreTheme() async {
    try {
      final saved = await store.storage.read(key: 'theme');
      if (!mounted) return;
      setState(
        () => themeMode = ThemeMode.values.firstWhere(
          (mode) => mode.name == saved,
          orElse: () => ThemeMode.system,
        ),
      );
    } catch (_) {
      /* System theme remains available if secure storage fails. */
    }
  }

  void _changed() {
    if (!mounted) return;
    setState(() {
      if (controller.summary != null) hadAccount = true;
    });
  }

  Future<void> _signIn() async {
    try {
      if (await service.signIn() && mounted) await controller.refresh();
    } catch (_) {
      service.authError.value =
          'Unable to open PacketStream. Please try again.';
    }
  }

  Future<void> _signOut() async {
    setState(() => signingOut = true);
    try {
      await controller.logout();
      if (mounted) setState(() => hadAccount = false);
    } finally {
      if (mounted) setState(() => signingOut = false);
    }
  }

  Future<void> _theme(BuildContext context) async {
    final selection = await showDialog<ThemeMode>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Theme'),
        children: ThemeMode.values
            .map(
              (mode) => SimpleDialogOption(
                onPressed: () => Navigator.pop(context, mode),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(switch (mode) {
                    ThemeMode.system => 'System',
                    ThemeMode.light => 'Light',
                    ThemeMode.dark => 'Dark',
                  }),
                ),
              ),
            )
            .toList(),
      ),
    );
    if (selection == null || !mounted) return;
    setState(() => themeMode = selection);
    try {
      await store.storage.write(key: 'theme', value: selection.name);
    } catch (_) {}
  }

  Future<void> _settings(BuildContext context) async {
    WidgetSchedule current;
    try {
      current = await service.widgetBridge.getBackgroundRefresh();
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to load widget settings.')),
      );
      return;
    }
    if (!context.mounted) return;
    final selection = await showDialog<WidgetSchedule>(
      context: context,
      builder: (dialogContext) {
        var enabled = current.enabled;
        var intervalMinutes = current.intervalMinutes;
        return StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('Settings'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Allow background refresh'),
                  value: enabled,
                  onChanged: (value) => setDialogState(() => enabled = value),
                ),
                if (!enabled)
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: () => Navigator.pop(
                        dialogContext,
                        WidgetSchedule(
                          enabled: true,
                          intervalMinutes: intervalMinutes,
                        ),
                      ),
                      icon: const Icon(Icons.schedule),
                      label: const Text('Enable background refresh now'),
                    ),
                  ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  initialValue: intervalMinutes,
                  decoration: const InputDecoration(
                    labelText: 'Refresh frequency',
                  ),
                  items: WidgetSchedule.allowedIntervals
                      .map(
                        (minutes) => DropdownMenuItem(
                          value: minutes,
                          child: Text(
                            WidgetSchedule(
                              enabled: true,
                              intervalMinutes: minutes,
                            ).intervalLabel,
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: enabled
                      ? (value) {
                          if (value != null) {
                            setDialogState(() => intervalMinutes = value);
                          }
                        }
                      : null,
                ),
                const SizedBox(height: 12),
                Text(
                  'Android may delay background refresh to save battery or wait for a network connection.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(
                  dialogContext,
                  WidgetSchedule(
                    enabled: enabled,
                    intervalMinutes: intervalMinutes,
                  ),
                ),
                child: const Text('Done'),
              ),
            ],
          ),
        );
      },
    );
    if (selection == null || !mounted) return;
    try {
      await service.widgetBridge.setBackgroundRefresh(
        enabled: selection.enabled,
        intervalMinutes: selection.intervalMinutes,
      );
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            selection.enabled
                ? 'Background refresh set to ${selection.intervalLabel}.'
                : 'Background refresh turned off.',
          ),
        ),
      );
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to save widget settings.')),
      );
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) pausedAt = DateTime.now();
    if (state == AppLifecycleState.resumed) {
      unawaited(_syncNativeSummary());
    }
    if (state == AppLifecycleState.resumed && pausedAt != null) {
      final elapsed = DateTime.now().difference(pausedAt!);
      pausedAt = null;
      if (elapsed >= const Duration(minutes: 1) &&
          !service.signingIn.value &&
          controller.status != DashboardStatus.authenticationExpired) {
        controller.refresh();
      }
    }
  }

  Future<void> _syncNativeSummary() async {
    await controller.applyExternalSummary(
      await service.widgetBridge.readLatestSummary(),
    );
    if (mounted && controller.summary != null) {
      setState(() => hadAccount = true);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.removeListener(_changed);
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'PacketStream',
    debugShowCheckedModeBanner: false,
    theme: appTheme(Brightness.light),
    darkTheme: appTheme(Brightness.dark),
    themeMode: themeMode,
    home: Builder(
      builder: (context) => ValueListenableBuilder<bool>(
        valueListenable: service.signingIn,
        builder: (context, signingIn, _) {
          final expired =
              controller.status == DashboardStatus.authenticationExpired;
          final busy =
              controller.status == DashboardStatus.loading ||
              controller.status == DashboardStatus.refreshing ||
              signingOut;
          final showLogin = !hadAccount || expired;
          final signInPrompt = Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    hadAccount ? 'Session expired' : 'PacketStream',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'View your shared bandwidth and balance.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 32),
                  FilledButton.icon(
                    onPressed: _signIn,
                    icon: const Icon(Icons.login),
                    label: Text(
                      hadAccount ? 'Sign in again' : 'Sign in to PacketStream',
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Authentication is handled securely by PacketStream.',
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          );
          return PopScope(
            canPop: !signingIn,
            onPopInvokedWithResult: (didPop, _) {
              if (!didPop && signingIn) service.cancelSignIn();
            },
            child: Scaffold(
              appBar: AppBar(
                title: Text(
                  signingIn ? 'Sign in to PacketStream' : 'PacketStream',
                ),
                leading: signingIn
                    ? IconButton(
                        onPressed: service.cancelSignIn,
                        icon: const Icon(Icons.close),
                        tooltip: 'Cancel sign in',
                      )
                    : null,
                actions: signingIn
                    ? []
                    : [
                        PopupMenuButton<String>(
                          onSelected: (value) {
                            switch (value) {
                              case 'refresh':
                                controller.refresh();
                              case 'theme':
                                _theme(context);
                              case 'settings':
                                _settings(context);
                              case 'signout':
                                _signOut();
                            }
                          },
                          itemBuilder: (_) => [
                            if (!expired)
                              PopupMenuItem(
                                value: 'refresh',
                                enabled: !busy,
                                child: const Text('Refresh'),
                              ),
                            const PopupMenuItem(
                              value: 'theme',
                              child: Text('Theme'),
                            ),
                            const PopupMenuItem(
                              value: 'settings',
                              child: Text('Settings'),
                            ),
                            if (!expired)
                              PopupMenuItem(
                                value: 'signout',
                                enabled: !busy,
                                child: const Text('Sign out'),
                              ),
                          ],
                        ),
                      ],
              ),
              body: SafeArea(
                child: PacketStreamSurface(
                  signingIn: signingIn,
                  login: Column(
                    children: [
                      Expanded(
                        child: WebViewWidget(controller: service.webView),
                      ),
                      ValueListenableBuilder<String?>(
                        valueListenable: service.authError,
                        builder: (context, error, _) => error == null
                            ? const SizedBox.shrink()
                            : Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(
                                  children: [
                                    Text(error),
                                    TextButton(
                                      onPressed: () => service.webView
                                          .loadRequest(Uri.parse(dashboardUrl)),
                                      child: const Text('Retry'),
                                    ),
                                  ],
                                ),
                              ),
                      ),
                    ],
                  ),
                  dashboard: showLogin
                      ? signInPrompt
                      : Center(
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 560),
                            child: DashboardPage(controller: controller),
                          ),
                        ),
                ),
              ),
            ),
          );
        },
      ),
    ),
  );
}
