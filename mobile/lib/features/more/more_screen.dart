import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_scope.dart';
import '../../core/auth/transaction_pin.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/theme/app_theme.dart';
import '../../core/theme/payflow_widgets.dart';

class MoreScreen extends ConsumerStatefulWidget {
  const MoreScreen({super.key});

  @override
  ConsumerState<MoreScreen> createState() => _MoreScreenState();
}

class _MoreScreenState extends ConsumerState<MoreScreen> {
  final _pin = TextEditingController();
  final _moveAmount = TextEditingController();
  final _email = TextEditingController();
  final _amount = TextEditingController();
  final _day = TextEditingController();
  final _note = TextEditingController();
  final _code = TextEditingController();
  String? _message;
  Map<String, dynamic>? _profile;
  List<dynamic> _sessions = [];
  List<dynamic> _schedules = [];
  List<dynamic> _requests = [];
  String? _secret;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _pin.dispose();
    _moveAmount.dispose();
    _email.dispose();
    _amount.dispose();
    _day.dispose();
    _note.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final api = ref.read(controlsApiProvider);
    try {
      final profile = await api.me();
      final sessions = await api.sessions();
      final schedules = await api.schedules();
      final requests = await api.requests();
      if (mounted) {
        setState(() {
          _profile = profile;
          _sessions = sessions;
          _schedules = schedules;
          _requests = requests;
        });
      }
    } on ApiException catch (error) {
      if (mounted) setState(() => _message = error.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final profile = _profile;
    final savings = profile?['savingsMinor'];
    final bottom = MediaQuery.paddingOf(context).bottom;
    return Scaffold(
      appBar: AppBar(title: const Text('Money tools')),
      body: ListView(
        padding: EdgeInsets.fromLTRB(20, 8, 20, 28 + bottom),
        children: [
          if (_message != null) ...[
            Text(_message!, style: TextStyle(color: Theme.of(context).colorScheme.error, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
          ],
          _Section(
            title: 'Transaction PIN',
            child: Column(
              children: [
                TextField(
                  controller: _pin,
                  keyboardType: TextInputType.number,
                  obscureText: true,
                  maxLength: 6,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: const InputDecoration(hintText: '4–6 digits', counterText: ''),
                ),
                const SizedBox(height: 12),
                FilledButton(onPressed: _savePin, child: const Text('Save PIN')),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SurfaceCard(
            child: Row(
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Lock my wallet', style: TextStyle(fontWeight: FontWeight.w700)),
                      SizedBox(height: 4),
                      Text('Stops send, pay, and top-up.'),
                    ],
                  ),
                ),
                Switch(
                  value: profile?['walletLocked'] == true,
                  onChanged: (value) async {
                    await ref.read(controlsApiProvider).lockWallet(value);
                    await _load();
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Section(
            title: 'Savings',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  savings == null ? 'Not opened yet' : Paise.format((savings as num).toInt()),
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700, color: PayflowColors.greenDeep),
                ),
                const SizedBox(height: 12),
                if (savings == null)
                  OutlinedButton(onPressed: _openSavings, child: const Text('Open savings wallet'))
                else ...[
                  TextField(
                    controller: _moveAmount,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(hintText: 'Amount in rupees'),
                  ),
                  const SizedBox(height: 12),
                  FilledButton(onPressed: () => _move(true), child: const Text('Move into savings')),
                  const SizedBox(height: 4),
                  Center(child: TextButton(onPressed: () => _move(false), child: const Text('Move back to spending'))),
                ],
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Section(
            title: 'Monthly transfer',
            child: Column(
              children: [
                TextField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(hintText: 'Recipient email'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _amount,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(hintText: 'Amount in rupees'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _day,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(2)],
                  decoration: const InputDecoration(hintText: 'Day of month, 1–28'),
                ),
                const SizedBox(height: 12),
                TextField(controller: _note, decoration: const InputDecoration(hintText: 'Note')),
                const SizedBox(height: 12),
                FilledButton(onPressed: _schedule, child: const Text('Schedule')),
                for (final row in _schedules)
                  _MoneyLine(
                    title: '${(row as Map)['toEmail']}',
                    detail: '${Paise.format(((row)['amountMinor'] as num).toInt())} · day ${row['dayOfMonth']}',
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Section(
            title: 'Request money',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Uses the email and amount in Monthly transfer.'),
                const SizedBox(height: 12),
                FilledButton(onPressed: _request, child: const Text('Request this amount')),
                for (final row in _requests)
                  _MoneyLine(
                    title: '${(row as Map)['payerEmail']} · ${Paise.format(((row)['amountMinor'] as num).toInt())}',
                    detail: '${row['status']}${row['note'] == null || row['note'] == '' ? '' : ' · ${row['note']}'}',
                    trailing: row['incoming'] == true && row['status'] == 'OPEN'
                        ? TextButton(onPressed: () => _pay(row['id'].toString()), child: const Text('Pay'))
                        : null,
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Section(
            title: 'Authenticator',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_secret != null) ...[
                  SelectableText(_secret!),
                  const SizedBox(height: 12),
                ],
                OutlinedButton(onPressed: _setupTotp, child: const Text('Show setup secret')),
                const SizedBox(height: 12),
                TextField(
                  controller: _code,
                  keyboardType: TextInputType.number,
                  maxLength: 6,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: const InputDecoration(hintText: '6-digit code', counterText: ''),
                ),
                const SizedBox(height: 4),
                Center(child: TextButton(onPressed: _confirmTotp, child: const Text('Turn on 2FA'))),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Section(
            title: 'Devices',
            child: Column(
              children: [
                for (final row in _sessions)
                  _MoneyLine(
                    title: (row as Map)['deviceLabel']?.toString() ?? 'browser',
                    detail: row['revoked'] == true ? 'Signed out' : 'Active',
                    trailing: row['revoked'] == true
                        ? null
                        : TextButton(onPressed: () => _revoke(row['id'].toString()), child: const Text('Sign out')),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
      if (mounted) setState(() => _message = 'Saved');
      await _load();
    } on ApiException catch (error) {
      if (mounted) setState(() => _message = error.message);
    }
  }

  Future<void> _savePin() async {
    final pin = _pin.text.trim();
    await _run(() async {
      await ref.read(controlsApiProvider).setPin(pin);
      TransactionPin.value = pin;
    });
  }

  Future<void> _openSavings() => _run(() => ref.read(controlsApiProvider).openSavings());

  Future<void> _move(bool toSavings) async {
    final amount = Paise.parse(_moveAmount.text);
    if (amount == null) {
      setState(() => _message = 'Enter an amount in rupees');
      return;
    }
    await _run(() => ref.read(controlsApiProvider).moveSavings(
          idempotencyKey: const Uuid().v4(),
          toSavings: toSavings,
          amountMinor: amount,
        ));
  }

  Future<void> _schedule() async {
    final amount = Paise.parse(_amount.text);
    final day = int.tryParse(_day.text.trim());
    if (amount == null || day == null) {
      setState(() => _message = 'Amount and day are required');
      return;
    }
    await _run(() => ref.read(controlsApiProvider).schedule(
          toEmail: _email.text.trim(),
          amountMinor: amount,
          note: _note.text.trim(),
          dayOfMonth: day,
        ));
  }

  Future<void> _request() async {
    final amount = Paise.parse(_amount.text);
    if (amount == null) {
      setState(() => _message = 'Enter an amount in rupees');
      return;
    }
    await _run(() async {
      await ref.read(controlsApiProvider).createRequests(
        note: _note.text.trim(),
        shares: [
          {'email': _email.text.trim(), 'amountMinor': amount},
        ],
      );
    });
  }

  Future<void> _pay(String id) => _run(() => ref.read(controlsApiProvider).payRequest(id: id, idempotencyKey: const Uuid().v4()));

  Future<void> _setupTotp() async {
    try {
      final setup = await ref.read(controlsApiProvider).setupTotp();
      HapticFeedback.selectionClick();
      setState(() => _secret = setup['otpauth']?.toString() ?? setup['secret']?.toString());
    } on ApiException catch (error) {
      setState(() => _message = error.message);
    }
  }

  Future<void> _confirmTotp() => _run(() => ref.read(controlsApiProvider).confirmTotp(_code.text.trim()));

  Future<void> _revoke(String id) => _run(() => ref.read(controlsApiProvider).revokeSession(id));
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

class _MoneyLine extends StatelessWidget {
  const _MoneyLine({required this.title, required this.detail, this.trailing});

  final String title;
  final String detail;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
                Text(detail),
              ],
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}
