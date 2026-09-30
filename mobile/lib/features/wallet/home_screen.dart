import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';
import '../../core/theme/app_theme.dart';
import '../../core/theme/payflow_widgets.dart';
import '../../core/theme/ui_prefs.dart';
import '../transfer/send_controller.dart';

final walletProvider = AsyncNotifierProvider<WalletController, WalletSnapshot?>(WalletController.new);

class WalletController extends AsyncNotifier<WalletSnapshot?> {
  @override
  Future<WalletSnapshot?> build() async {
    final cached = await ref.read(localCacheProvider).readWallet();
    try {
      final wallet = await ref.read(payflowApiProvider).wallet();
      await ref.read(localCacheProvider).saveWallet(wallet);
      return wallet;
    } on ApiException {
      return cached;
    } catch (_) {
      return cached;
    }
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final wallet = await ref.read(payflowApiProvider).wallet();
      await ref.read(localCacheProvider).saveWallet(wallet);
      return wallet;
    });
  }
}

final recentProvider = FutureProvider<List<TxSummary>>((ref) async {
  final cached = await ref.read(localCacheProvider).readTransactions();
  try {
    final page = await ref.read(payflowApiProvider).transactions();
    await ref.read(localCacheProvider).saveTransactions(page.items);
    return page.items;
  } catch (_) {
    return cached;
  }
});

final attemptsProvider = FutureProvider<List<AttemptRecord>>((ref) {
  return ref.read(localCacheProvider).readAttempts();
});

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wallet = ref.watch(walletProvider);
    final recent = ref.watch(recentProvider);
    final attempts = ref.watch(attemptsProvider);
    final online = ref.watch(onlineProvider);
    final session = ref.watch(sessionProvider);
    final prefs = ref.watch(uiPrefsProvider);
    final name = prefs.displayName.isNotEmpty ? prefs.displayName : (session?.email ?? 'there').split('@').first;
    return Scaffold(
      appBar: AppBar(
        title: Text(tr(prefs.hindi, 'wallet')),
        actions: [
          IconButton(
            tooltip: 'Hide balance',
            onPressed: () => ref.read(uiPrefsProvider.notifier).toggleBalance(),
            icon: Icon(prefs.hideBalance ? Icons.visibility_off_outlined : Icons.visibility_outlined),
          ),
          IconButton(onPressed: () => context.push('/inbox'), icon: const Icon(Icons.notifications_none)),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await ref.read(walletProvider.notifier).refresh();
          ref.invalidate(recentProvider);
          ref.invalidate(attemptsProvider);
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
          children: [
            Text('${tr(prefs.hindi, 'hello')}, $name', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 16),
            if (!online) const _OfflineBanner(),
            wallet.when(
              data: (value) => _BalanceCard(
                    balance: value?.balanceMinor ?? 0,
                    currency: value?.currency ?? 'INR',
                    hidden: prefs.hideBalance,
                  ),
              loading: () => const _Skeleton(height: 140),
              error: (error, _) => Text(friendlyError('NETWORK')),
            ),
            const SizedBox(height: 18),
            _Actions(online: online, session: session, hindi: prefs.hindi),
            if (prefs.favorites.isNotEmpty) _Favorites(emails: prefs.favorites),
            attempts.when(
              data: (items) => items.isEmpty ? const SizedBox.shrink() : _Pending(items: items),
              loading: () => const SizedBox.shrink(),
              error: (_, _) => const SizedBox.shrink(),
            ),
            const SizedBox(height: 22),
            Row(
              children: [
                Text(tr(prefs.hindi, 'activity'), style: Theme.of(context).textTheme.titleMedium),
                const Spacer(),
                TextButton(onPressed: () => context.go('/history'), child: const Text('See all')),
              ],
            ),
            recent.when(
              data: (items) => Column(
                children: [
                  if (items.length > 1) SizedBox(height: 120, child: _Chart(items: items)),
                  if (items.isEmpty)
                    SurfaceCard(
                      child: Column(
                        children: [
                          const Icon(Icons.receipt_long_outlined, size: 36, color: PayflowColors.gold),
                          const SizedBox(height: 8),
                          Text(tr(prefs.hindi, 'empty'), textAlign: TextAlign.center),
                        ],
                      ),
                    ),
                  ...items.take(5).map((item) {
                    final when = DateFormat.MMMd().add_jm().format(item.createdAt.toLocal());
                    return TxRow(
                      type: item.type,
                      status: item.status,
                      when: when,
                      amountMinor: item.amountMinor,
                      onTap: () => context.go('/history/${item.id}'),
                    );
                  }),
                ],
              ),
              loading: () => const _Skeleton(height: 72),
              error: (_, _) => const Text('History is unavailable.'),
            ),
          ],
        ),
      ),
    );
  }
}

class _Actions extends StatelessWidget {
  const _Actions({required this.online, required this.session, required this.hindi});

  final bool online;
  final Session? session;
  final bool hindi;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _Action(icon: Icons.north_east, label: tr(hindi, 'send'), onTap: online ? () => context.go('/send') : null),
        _Action(icon: Icons.add, label: tr(hindi, 'topup'), onTap: online ? () => context.go('/topup') : null),
        _Action(icon: Icons.qr_code_scanner, label: tr(hindi, 'scan'), onTap: online ? () => context.go('/scan') : null),
        _Action(icon: Icons.group_outlined, label: 'Split', onTap: online ? () => context.push('/more') : null),
        if (session?.isMerchant == true) _Action(icon: Icons.qr_code_2, label: 'Request', onTap: () => context.go('/merchant')),
        if (session?.isAdmin == true) _Action(icon: Icons.admin_panel_settings_outlined, label: 'Admin', onTap: () => context.go('/admin')),
      ],
    );
  }
}

class _Action extends StatelessWidget {
  const _Action({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Column(
            children: [
              CircleAvatar(
                radius: 24,
                backgroundColor: onTap == null ? const Color(0xFF2E4038) : const Color(0xFF146B54),
                child: Icon(icon, color: Colors.white, size: 20),
              ),
              const SizedBox(height: 6),
              Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
            ],
          ),
        ),
      ),
    );
  }
}

class _Pending extends ConsumerWidget {
  const _Pending({required this.items});

  final List<AttemptRecord> items;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: SurfaceCard(
        child: Row(
          children: [
            const Icon(Icons.hourglass_top, color: PayflowColors.green),
            const SizedBox(width: 12),
            const Expanded(child: Text('A payment is still being checked.')),
            TextButton(
              onPressed: () async {
                await ref.read(sendControllerProvider.notifier).resume(items.first.idempotencyKey);
                if (context.mounted) {
                  context.go('/send');
                }
              },
              child: const Text('Check'),
            ),
          ],
        ),
      ),
    );
  }
}

class _BalanceCard extends StatelessWidget {
  const _BalanceCard({required this.balance, required this.currency, required this.hidden});

  final int balance;
  final String currency;
  final bool hidden;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: const LinearGradient(colors: [PayflowColors.greenDeep, Color(0xFF146B54)], begin: Alignment.topLeft, end: Alignment.bottomRight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Available balance', style: TextStyle(color: Color(0xFFD7E8E2))),
          const SizedBox(height: 8),
          Text(
            hidden ? '₹ ••••••' : Paise.format(balance),
            style: const TextStyle(color: Colors.white, fontSize: 36, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(currency, style: const TextStyle(color: PayflowColors.gold, letterSpacing: 1.2, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _Favorites extends StatelessWidget {
  const _Favorites({required this.emails});

  final List<String> emails;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Wrap(
        spacing: 8,
        children: [
          for (final email in emails)
            ActionChip(
              label: Text(email.split('@').first),
              onPressed: () => context.go('/send?to=${Uri.encodeComponent(email)}'),
            ),
        ],
      ),
    );
  }
}

class _Skeleton extends StatelessWidget {
  const _Skeleton({required this.height});

  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: BoxDecoration(color: const Color(0xFF1B2622), borderRadius: BorderRadius.circular(24)),
    );
  }
}

class _Chart extends StatelessWidget {
  const _Chart({required this.items});

  final List<TxSummary> items;

  @override
  Widget build(BuildContext context) {
    final bars = items.take(7).toList().reversed.toList();
    return BarChart(BarChartData(
      titlesData: const FlTitlesData(show: false),
      gridData: const FlGridData(show: false),
      borderData: FlBorderData(show: false),
      barGroups: [
        for (var i = 0; i < bars.length; i++)
          BarChartGroupData(x: i, barRods: [
            BarChartRodData(toY: bars[i].amountMinor / 100, width: 14, color: const Color(0xFF0F6E56)),
          ]),
      ],
    ));
  }
}

class _OfflineBanner extends StatelessWidget {
  const _OfflineBanner();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(bottom: 12),
      child: Text('Offline. You can look at the last balance, but sending money is paused.'),
    );
  }
}
