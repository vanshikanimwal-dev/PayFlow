import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';
import '../../core/theme/payflow_widgets.dart';
import '../wallet/home_screen.dart';
import 'statement.dart';
import 'statement_download.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  final _items = <TxSummary>[];
  final _scroll = ScrollController();
  String? _cursor;
  String? _type;
  String? _status;
  final _search = TextEditingController();
  final _min = TextEditingController();
  final _max = TextEditingController();
  bool _loading = false;
  bool _done = false;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (_scroll.position.pixels > _scroll.position.maxScrollExtent - 200) {
        _load();
      }
    });
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _search.dispose();
    _min.dispose();
    _max.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Activity'),
        actions: [
          IconButton(tooltip: 'Download statement', onPressed: _statement, icon: const Icon(Icons.picture_as_pdf_outlined)),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _search,
              decoration: const InputDecoration(labelText: 'Search type or amount'),
              onChanged: (_) => setState(() {}),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(child: TextField(controller: _min, decoration: const InputDecoration(labelText: 'Min ₹'), onChanged: (_) => setState(() {}))),
                const SizedBox(width: 8),
                Expanded(child: TextField(controller: _max, decoration: const InputDecoration(labelText: 'Max ₹'), onChanged: (_) => setState(() {}))),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(
                  child: _Filter(
                    label: 'Type',
                    value: _type,
                    values: const ['TRANSFER', 'TOPUP', 'PAYMENT', 'REFUND'],
                    onChanged: (value) {
                      _type = value;
                      _reload();
                    },
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _Filter(
                    label: 'Status',
                    value: _status,
                    values: const ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'],
                    onChanged: (value) {
                      _status = value;
                      _reload();
                    },
                  ),
                ),
              ],
            ),
          ),
          if (_filtered)
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(onPressed: _clearFilters, child: const Text('Clear filters')),
            ),
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              itemCount: _rows.length + 1,
              itemBuilder: (context, index) {
                if (index == _rows.length) {
                  return Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      _loading
                          ? 'Loading…'
                          : (_visible.isEmpty
                              ? activityEmptyLine(loadedAny: _items.isNotEmpty, filtered: _filtered)
                              : (_done ? 'End of history' : '')),
                    ),
                  );
                }
                final row = _rows[index];
                final header = row.label;
                if (header != null) {
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                    child: Text(header, style: const TextStyle(fontWeight: FontWeight.w700)),
                  );
                }
                final item = row.item!;
                final mine = ref.watch(walletProvider).value?.accountId;
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: TxRow(
                    type: item.type,
                    status: item.status,
                    when: DateFormat.MMMd().add_jm().format(item.createdAt.toLocal()),
                    amountMinor: item.amountMinor,
                    inbound: moneyComingIn(
                      type: item.type,
                      fromAccountId: item.fromAccountId,
                      toAccountId: item.toAccountId,
                      mine: mine,
                    ),
                    onTap: () => context.go('/history/${item.id}'),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  bool get _filtered {
    return _type != null || _status != null || _search.text.trim().isNotEmpty || _min.text.trim().isNotEmpty || _max.text.trim().isNotEmpty;
  }

  List<TxSummary> get _visible {
    final query = _search.text.trim().toLowerCase();
    final minMinor = Paise.parse(_min.text);
    final maxMinor = Paise.parse(_max.text);
    return _items.where((item) {
      if (query.isNotEmpty) {
        final haystack = '${item.type} ${item.status} ${Paise.format(item.amountMinor)}'.toLowerCase();
        if (!haystack.contains(query)) {
          return false;
        }
      }
      if (minMinor != null && item.amountMinor < minMinor) {
        return false;
      }
      if (maxMinor != null && item.amountMinor > maxMinor) {
        return false;
      }
      return true;
    }).toList();
  }

  List<ActivityRow> get _rows => activityRows(_visible, DateTime.now());

  void _statement() {
    final lines = [
      for (final item in _visible) '${item.createdAt.toLocal()}  ${item.type}  ${item.status}  ${Paise.format(item.amountMinor)}',
    ];
    final text = statementText(lines);
    downloadBytes('payflow-statement.pdf', statementPdf(text), 'application/pdf');
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Statement'),
        content: SelectableText(text.isEmpty ? 'No rows in this filter.' : text),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close'))],
      ),
    );
  }

  void _clearFilters() {
    _search.clear();
    _min.clear();
    _max.clear();
    _type = null;
    _status = null;
    _reload();
  }

  void _reload() {
    setState(() {
      _items.clear();
      _cursor = null;
      _done = false;
      _loading = false;
    });
    _load();
  }

  Future<void> _load() async {
    if (_loading || _done) {
      return;
    }
    setState(() => _loading = true);
    try {
      final page = await ref.read(payflowApiProvider).transactions(cursor: _cursor, type: _type, status: _status);
      if (_cursor == null) {
        await ref.read(localCacheProvider).saveTransactions(page.items);
      }
      setState(() {
        _items.addAll(page.items);
        _cursor = page.nextCursor;
        _done = page.nextCursor == null || page.items.isEmpty;
      });
    } catch (_) {
      if (_items.isEmpty) {
        final cached = await ref.read(localCacheProvider).readTransactions();
        setState(() {
          _items.addAll(cached);
          _done = true;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }
}

String activityEmptyLine({required bool loadedAny, required bool filtered}) {
  if (!loadedAny && !filtered) {
    return 'No payments yet. Add money from the wallet screen.';
  }
  return 'Nothing matches this filter.';
}

class ActivityRow {
  const ActivityRow.header(this.label) : item = null;
  const ActivityRow.item(this.item) : label = null;

  final String? label;
  final TxSummary? item;
}

String activityDayLabel(DateTime createdAt, DateTime now) {
  final day = DateTime(createdAt.year, createdAt.month, createdAt.day);
  final today = DateTime(now.year, now.month, now.day);
  final diff = today.difference(day).inDays;
  if (diff == 0) {
    return 'Today';
  }
  if (diff == 1) {
    return 'Yesterday';
  }
  return DateFormat.MMMd().format(createdAt);
}

List<ActivityRow> activityRows(List<TxSummary> items, DateTime now) {
  final rows = <ActivityRow>[];
  String? last;
  for (final item in items) {
    final label = activityDayLabel(item.createdAt.toLocal(), now);
    if (label != last) {
      rows.add(ActivityRow.header(label));
      last = label;
    }
    rows.add(ActivityRow.item(item));
  }
  return rows;
}

class _Filter extends StatelessWidget {
  const _Filter({required this.label, required this.value, required this.values, required this.onChanged});

  final String label;
  final String? value;
  final List<String> values;
  final void Function(String? value) onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
      key: ValueKey('$label-${value ?? ''}'),
      initialValue: value ?? '',
      decoration: InputDecoration(labelText: label),
      items: [
        const DropdownMenuItem(value: '', child: Text('Any')),
        ...values.map((item) => DropdownMenuItem(value: item, child: Text(item))),
      ],
      onChanged: (selected) => onChanged(selected == null || selected.isEmpty ? null : selected),
    );
  }
}

class DetailScreen extends ConsumerWidget {
  const DetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(_detailProvider(id));
    return Scaffold(
      appBar: AppBar(title: const Text('Transaction')),
      body: detail.when(
        data: (tx) => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            SurfaceCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  StatusChip(status: tx.status),
                  const SizedBox(height: 10),
                  Text(Paise.format(tx.amountMinor), style: Theme.of(context).textTheme.headlineMedium),
                  Text(tx.type),
                  Text('Refunded ${Paise.format(tx.refundedMinor)}'),
                ],
              ),
            ),
            if (_canRefund(ref, tx)) ...[
              const SizedBox(height: 12),
              _RefundButton(transaction: tx),
            ],
            const SizedBox(height: 8),
            _DisputeButton(transactionId: tx.id),
            const SizedBox(height: 16),
            Text('Ledger', style: Theme.of(context).textTheme.titleMedium),
            ...tx.entries.map((line) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${line.direction} ${Paise.format(line.amountMinor)}'),
                  subtitle: Text(line.accountId),
                  trailing: Text(Paise.format(line.balanceAfter)),
                )),
          ],
        ),
        loading: () => const LinearProgressIndicator(),
        error: (_, _) => const Center(child: Text('Could not load this transaction.')),
      ),
    );
  }
}

bool _canRefund(WidgetRef ref, TxDetail tx) {
  if (tx.status != 'COMPLETED' || tx.refundedMinor >= tx.amountMinor) {
    return false;
  }
  if (tx.type == 'TOPUP') {
    return true;
  }
  final session = ref.watch(sessionProvider);
  final allowed = session?.isMerchant == true || session?.isAdmin == true;
  return allowed && (tx.type == 'PAYMENT' || tx.type == 'TRANSFER');
}

class _RefundButton extends ConsumerStatefulWidget {
  const _RefundButton({required this.transaction});

  final TxDetail transaction;

  @override
  ConsumerState<_RefundButton> createState() => _RefundButtonState();
}

class _RefundButtonState extends ConsumerState<_RefundButton> {
  String? _message;
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final remaining = widget.transaction.amountMinor - widget.transaction.refundedMinor;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        FilledButton.tonal(
          onPressed: _busy ? null : () => _refund(remaining),
          child: Text(_busy
              ? 'Working…'
              : widget.transaction.type == 'TOPUP'
                  ? 'Send ${Paise.format(remaining)} back to the card'
                  : 'Refund ${Paise.format(remaining)}'),
        ),
        if (_message != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_message!)),
      ],
    );
  }

  Future<void> _refund(int amount) async {
    setState(() => _busy = true);
    try {
      final result = await ref.read(payflowApiProvider).refund(
            idempotencyKey: const Uuid().v4(),
            transactionId: widget.transaction.id,
            amountMinor: amount,
          );
      ref.invalidate(_detailProvider(widget.transaction.id));
      setState(() => _message = 'Refund ${result.status}. Returned ${Paise.format(result.refundedMinor)}.');
    } on ApiException catch (error) {
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }
}

class _DisputeButton extends ConsumerStatefulWidget {
  const _DisputeButton({required this.transactionId});

  final String transactionId;

  @override
  ConsumerState<_DisputeButton> createState() => _DisputeButtonState();
}

class _DisputeButtonState extends ConsumerState<_DisputeButton> {
  final _note = TextEditingController();
  String? _message;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          controller: _note,
          decoration: const InputDecoration(hintText: 'Something wrong? Tell us in a sentence'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(onPressed: _send, child: const Text('Flag this payment')),
        if (_message != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_message!)),
      ],
    );
  }

  Future<void> _send() async {
    final note = _note.text.trim();
    if (note.length < 3) {
      setState(() => _message = 'Write a short note first.');
      return;
    }
    try {
      await ref.read(controlsApiProvider).dispute(transactionId: widget.transactionId, note: note);
      setState(() => _message = 'Flagged. An admin can see it.');
    } on ApiException catch (error) {
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    }
  }
}

final _detailProvider = FutureProvider.family<TxDetail, String>((ref, id) {
  return ref.read(payflowApiProvider).transaction(id);
});
