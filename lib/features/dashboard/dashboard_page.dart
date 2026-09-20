import 'package:flutter/material.dart';
import '../../core/formatting.dart';
import 'dashboard_controller.dart';

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key, required this.controller});
  final DashboardController controller;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) => _content(context),
  );
  Widget _content(BuildContext context) {
    final summary = controller.summary;
    final busy =
        controller.status == DashboardStatus.loading ||
        controller.status == DashboardStatus.refreshing;
    final status = switch (controller.status) {
      DashboardStatus.loaded => 'Connected',
      DashboardStatus.loading ||
      DashboardStatus.refreshing ||
      DashboardStatus.initial => 'Refreshing',
      DashboardStatus.offline => 'Offline',
      DashboardStatus.authenticationExpired => 'Session expired',
      DashboardStatus.error => 'Unable to update',
    };
    final scheme = Theme.of(context).colorScheme;
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
        children: [
          Row(
            children: [
              Icon(
                Icons.circle,
                size: 9,
                color: controller.status == DashboardStatus.loaded
                    ? const Color(0xff36825c)
                    : scheme.onSurfaceVariant,
              ),
              const SizedBox(width: 9),
              Text(status, style: Theme.of(context).textTheme.labelLarge),
            ],
          ),
          if (controller.status == DashboardStatus.error)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: Text(
                'PacketStream’s dashboard could not be read. Try again later.',
              ),
            ),
          if (controller.status == DashboardStatus.offline && summary == null)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: Text('Connect to the internet to load your account.'),
            ),
          const SizedBox(height: 28),
          MetricCard(
            title: 'Bandwidth Shared',
            subtitle: 'Last 14 days',
            icon: Icons.swap_vert_rounded,
            value: summary == null ? null : formatBytes(summary.bandwidthBytes),
            loading: busy,
          ),
          const SizedBox(height: 16),
          MetricCard(
            title: 'Balance',
            icon: Icons.account_balance_wallet_outlined,
            value: summary == null ? null : formatBalance(summary.balance),
            loading: busy,
          ),
          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: busy ? null : controller.refresh,
            icon: busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh),
            label: Text(busy ? 'Refreshing' : 'Refresh'),
          ),
          const SizedBox(height: 16),
          Text(
            summary == null
                ? 'Not updated yet'
                : 'Last updated: ${MaterialLocalizations.of(context).formatTimeOfDay(TimeOfDay.fromDateTime(summary.fetchedAt.toLocal()))} · ${MaterialLocalizations.of(context).formatShortDate(summary.fetchedAt.toLocal())}',
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class MetricCard extends StatelessWidget {
  const MetricCard({
    super.key,
    required this.title,
    required this.icon,
    required this.value,
    this.subtitle,
    this.loading = false,
  });
  final String title;
  final String? subtitle;
  final String? value;
  final IconData icon;
  final bool loading;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                icon,
                size: 22,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          if (value == null && loading)
            Semantics(
              label: 'Loading $title',
              child: Container(
                height: 44,
                width: 170,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            )
          else
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 160),
              child: Text(
                value ?? '—',
                key: ValueKey(value),
                style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          if (subtitle != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                subtitle!,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
        ],
      ),
    ),
  );
}
