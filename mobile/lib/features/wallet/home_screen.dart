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
                    savings: value?.savingsMinor ?? 0,
                    spentMonth: value?.spentMonthMinor ?? 0,
                    monthlyLimit: value?.monthlyLimitMinor ?? 0,
                    currency: value?.currency ?? 'INR',
                    hidden: prefs.hideBalance,
                  ),
              loading: () => const _Skeleton(height: 140),
              error: (error, _) => Text(friendlyError('NETWORK')),
            ),
            const SizedBox(height: 18),
            wallet.when(
              data: (value) => (value?.balanceMinor ?? 0) == 0
                  ? const _AddMoney()
                  : const SizedBox.shrink(),
              loading: () => const SizedBox.shrink(),
              error: (_, _) => const SizedBox.shrink(),
            ),
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
              data: (items) {
                final mine = wallet.value?.accountId;
                return Column(
                children: [
                  if (items.length > 1) SizedBox(height: 120, child: _Chart(items: items)),
                  if (items.isNotEmpty) _Flow(items: items.take(5).toList(), mine: mine),
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
                    final inbound = moneyComingIn(
                      type: item.type,
                      fromAccountId: item.fromAccountId,
                      toAccountId: item.toAccountId,
                      mine: mine,
                    );
                    return TxRow(
                      type: item.type,
                      status: item.status,
                      when: when,
                      amountMinor: item.amountMinor,
                      inbound: inbound,
                      onTap: () => context.go('/history/${item.id}'),
                    );
                  }),
                ],
              );
              },
              loading: () => const _Skeleton(height: 72),
              error: (_, _) => const Text('History is unavailable.'),
            ),
          ],
        ),
      ),
    );
  }
}

class _AddMoney extends StatelessWidget {
  const _AddMoney();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: SurfaceCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('This wallet is empty', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            const Text('Add fake rupees first. Then you can send them to another PayFlow account.'),
            const SizedBox(height: 12),
            FilledButton(onPressed: () => context.go('/topup'), child: const Text('Add money')),
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
    final actions = <Widget>[
      _Action(icon: Icons.north_east, label: tr(hindi, 'send'), onTap: online ? () => context.go('/send') : null),
      _Action(icon: Icons.add, label: tr(hindi, 'topup'), onTap: online ? () => context.go('/topup') : null),
      _Action(icon: Icons.qr_code_scanner, label: tr(hindi, 'scan'), onTap: online ? () => context.go('/scan') : null),
      _Action(icon: Icons.tune, label: 'Tools', onTap: online ? () => context.push('/more') : null),
      if (session?.isMerchant == true) _Action(icon: Icons.qr_code_2, label: 'Request', onTap: () => context.go('/merchant')),
      if (session?.isAdmin == true) _Action(icon: Icons.admin_panel_settings_outlined, label: 'Admin', onTap: () => context.go('/admin')),
    ];
    return SizedBox(
      height: 96,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: actions.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (_, index) => actions[index],
      ),
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
    return SizedBox(
      width: 76,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Column(
            children: [
              CircleAvatar(
                radius: 26,
                backgroundColor: onTap == null ? const Color(0xFF2E4038) : const Color(0xFF146B54),
                child: Icon(icon, color: Colors.white, size: 20),
              ),
              const SizedBox(height: 6),
              Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
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
  const _BalanceCard({
    required this.balance,
    required this.savings,
    required this.spentMonth,
    required this.monthlyLimit,
    required this.currency,
    required this.hidden,
  });

  final int balance;
  final int savings;
  final int spentMonth;
  final int monthlyLimit;
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
          if (savings > 0) ...[
            const SizedBox(height: 8),
            Text(hidden ? 'Savings hidden' : 'Savings ${Paise.format(savings)}', style: const TextStyle(color: Color(0xFFD7E8E2))),
          ],
          if (monthlyLimit > 0) ...[
            const SizedBox(height: 14),
            ClipRRect(
              borderRadius: BorderRadius.circular(99),
              child: LinearProgressIndicator(
                minHeight: 6,
                value: (spentMonth / monthlyLimit).clamp(0, 1).toDouble(),
                backgroundColor: const Color(0x33FFFFFF),
                color: spentMonth > monthlyLimit ? const Color(0xFFFFB4A8) : Colors.white,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              hidden ? 'Monthly limit hidden' : 'Sent this month ${Paise.format(spentMonth)} of ${Paise.format(monthlyLimit)}',
              style: const TextStyle(color: Color(0xFFD7E8E2), fontSize: 13),
            ),
          ],
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

class _Flow extends StatelessWidget {
  const _Flow({required this.items, required this.mine});

  final List<TxSummary> items;
  final String? mine;

  @override
  Widget build(BuildContext context) {
    var incoming = 0;
    var outgoing = 0;
    for (final item in items) {
      if (item.status != 'COMPLETED') {
        continue;
      }
      final coming = moneyComingIn(
        type: item.type,
        fromAccountId: item.fromAccountId,
        toAccountId: item.toAccountId,
        mine: mine,
      );
      if (coming == true) {
        incoming += item.amountMinor;
      } else if (coming == false) {
        outgoing += item.amountMinor;
      }
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Expanded(child: _FlowChip(label: 'In', amountMinor: incoming, inbound: true)),
          const SizedBox(width: 8),
          Expanded(child: _FlowChip(label: 'Out', amountMinor: outgoing, inbound: false)),
        ],
      ),
    );
  }
}

class _FlowChip extends StatelessWidget {
  const _FlowChip({required this.label, required this.amountMinor, required this.inbound});

  final String label;
  final int amountMinor;
  final bool inbound;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final color = inbound ? (dark ? const Color(0xFF3DDC97) : PayflowColors.green) : (dark ? const Color(0xFFFFB4A8) : PayflowColors.danger);
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          Text(Paise.format(amountMinor), style: TextStyle(fontWeight: FontWeight.w700, color: color)),
        ],
      ),
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
            BarChartRodData(
              toY: bars[i].amountMinor / 100,
              width: 14,
              borderRadius: BorderRadius.circular(6),
              color: bars[i].type == 'TOPUP' || bars[i].type == 'REFUND' ? const Color(0xFF3DDC97) : const Color(0xFFC4A574),
            ),
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
      child: SurfaceCard(
        padding: EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.cloud_off_outlined, color: PayflowColors.gold),
            SizedBox(width: 10),
            Expanded(child: Text('You are offline. The last balance is still here. Sending money is paused.')),
          ],
        ),
      ),
    );
  }
}
