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
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              itemCount: _visible.length + 1,
              itemBuilder: (context, index) {
                if (index == _visible.length) {
                  return Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(_loading ? 'Loading…' : (_visible.isEmpty ? 'Nothing matches this filter.' : (_done ? 'End of history' : ''))),
                  );
                }
                final item = _visible[index];
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: TxRow(
                    type: item.type,
                    status: item.status,
                    when: DateFormat.yMMMd().add_jm().format(item.createdAt.toLocal()),
                    amountMinor: item.amountMinor,
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

class _Filter extends StatelessWidget {
  const _Filter({required this.label, required this.value, required this.values, required this.onChanged});

  final String label;
  final String? value;
  final List<String> values;
  final void Function(String? value) onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
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
  final session = ref.watch(sessionProvider);
  final allowed = session?.isMerchant == true || session?.isAdmin == true;
  final open = tx.status == 'COMPLETED' && (tx.type == 'PAYMENT' || tx.type == 'TRANSFER');
  return allowed && open && tx.refundedMinor < tx.amountMinor;
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
          child: Text(_busy ? 'Refunding…' : 'Refund ${Paise.format(remaining)}'),
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

final _detailProvider = FutureProvider.family<TxDetail, String>((ref, id) {
  return ref.read(payflowApiProvider).transaction(id);
});
