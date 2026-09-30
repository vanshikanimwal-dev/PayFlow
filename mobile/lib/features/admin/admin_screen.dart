import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/app_scope.dart';
import '../../core/money/paise.dart';

class AdminScreen extends ConsumerStatefulWidget {
  const AdminScreen({super.key});

  @override
  ConsumerState<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends ConsumerState<AdminScreen> {
  String? _status;
  List<Map<String, dynamic>> _transactions = const [];
  List<Map<String, dynamic>> _runs = const [];
  List<Map<String, dynamic>> _items = const [];
  String? _note;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Admin')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Wrap(
            spacing: 8,
            children: [
              FilledButton(onPressed: _integrity, child: const Text('Integrity')),
              FilledButton.tonal(onPressed: _verify, child: const Text('Verify audit')),
              FilledButton.tonal(onPressed: _reconcile, child: const Text('Reconcile today')),
              FilledButton.tonal(onPressed: _fraud, child: const Text('Fraud flags')),
            ],
          ),
          if (_note != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_note!)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _status ?? '',
            decoration: const InputDecoration(labelText: 'Transactions'),
            items: const [
              DropdownMenuItem(value: '', child: Text('All')),
              DropdownMenuItem(value: 'PENDING', child: Text('Pending')),
              DropdownMenuItem(value: 'FAILED', child: Text('Failed')),
              DropdownMenuItem(value: 'PROCESSING', child: Text('Processing')),
            ],
            onChanged: (value) {
              setState(() => _status = value == null || value.isEmpty ? null : value);
              _load();
            },
          ),
          ..._transactions.take(20).map((tx) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text('${tx['type']} · ${tx['status']}'),
                subtitle: Text('${tx['id']}'),
                trailing: Text(Paise.format((tx['amountMinor'] as num?)?.toInt() ?? 0)),
              )),
          const SizedBox(height: 12),
          Text('Reconciliation', style: Theme.of(context).textTheme.titleMedium),
          ..._runs.map((run) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text('${run['runDate']} · ${run['status']}'),
                subtitle: Text('mismatched ${run['mismatched'] ?? 0}'),
                onTap: () => _itemsFor('${run['id']}'),
              )),
          ..._items.map((item) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text('${item['kind']} · ${item['resolution']}'),
                subtitle: Text('${item['note'] ?? item['gatewayRef'] ?? ''}'),
              )),
        ],
      ),
    );
  }

  Future<void> _load() async {
    final api = ref.read(payflowApiProvider);
    final transactions = await api.adminTransactions(status: _status);
    final runs = await api.reconciliationRuns();
    if (mounted) {
      setState(() {
        _transactions = transactions;
        _runs = runs;
      });
    }
  }

  Future<void> _itemsFor(String id) async {
    final items = await ref.read(payflowApiProvider).reconciliationItems(id);
    if (mounted) {
      setState(() => _items = items);
    }
  }

  Future<void> _integrity() async {
    final report = await ref.read(payflowApiProvider).integrity();
    setState(() => _note = report['valid'] == true ? 'Ledger checks passed.' : 'Violations: ${report['violations']}');
  }

  Future<void> _verify() async {
    final report = await ref.read(payflowApiProvider).verifyAudit();
    setState(() => _note = report['valid'] == true ? 'Audit chain matches.' : '${report['message']}');
  }

  Future<void> _reconcile() async {
    final run = await ref.read(payflowApiProvider).runReconciliation();
    setState(() => _note = 'Run ${run['status']} · mismatched ${run['mismatched'] ?? 0}');
    await _load();
  }

  Future<void> _fraud() async {
    final rows = await ref.read(controlsApiProvider).fraudFlags();
    setState(() => _note = rows.isEmpty ? 'No open fraud flags.' : rows.map((row) => '${row['kind']}: ${row['detail']}').join('\n'));
  }
}
