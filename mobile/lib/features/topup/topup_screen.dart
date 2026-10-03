import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_scope.dart';
import '../../core/network/dio_payflow_api.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';
import '../../core/theme/payflow_widgets.dart';

/// The mock checkout page is served on the API machine. A phone must not open localhost.
String checkoutUrl(String paymentUrl, String apiBase) {
  final pay = Uri.parse(paymentUrl);
  final api = Uri.parse(apiBase);
  if (pay.host == 'localhost' || pay.host == '127.0.0.1') {
    return pay.replace(scheme: api.scheme, host: api.host).toString();
  }
  return paymentUrl;
}

class TopUpScreen extends ConsumerStatefulWidget {
  const TopUpScreen({super.key});

  @override
  ConsumerState<TopUpScreen> createState() => _TopUpScreenState();
}

class _TopUpScreenState extends ConsumerState<TopUpScreen> {
  final _amount = TextEditingController();
  String _method = 'UPI';
  String? _message;
  bool _checking = false;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final online = ref.watch(onlineProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Top up')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('Add simulated rupees. The next screen is a mock checkout, not a real bank.'),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            children: [
              for (final preset in const ['100', '500', '1000', '2000'])
                ActionChip(label: Text('₹$preset'), onPressed: () => _amount.text = preset),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _amount,
            decoration: const InputDecoration(labelText: 'Amount (INR)'),
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _method,
            items: const [
              DropdownMenuItem(value: 'UPI', child: Text('UPI')),
              DropdownMenuItem(value: 'CARD', child: Text('Card')),
            ],
            onChanged: (value) => setState(() => _method = value ?? 'UPI'),
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: !online || _checking ? null : _start,
            child: Text(_checking ? 'Checking status…' : 'Continue'),
          ),
          if (_message != null) ...[
            const SizedBox(height: 16),
            SurfaceCard(child: Text(_message!)),
          ],
        ],
      ),
    );
  }

  Future<void> _start() async {
    final minor = Paise.parse(_amount.text);
    if (minor == null) {
      setState(() => _message = 'Enter a rupee amount such as 100.00');
      return;
    }
    final key = const Uuid().v4();
    await ref.read(localCacheProvider).saveAttempt(AttemptRecord(
          idempotencyKey: key,
          kind: 'topup',
          bodyJson: '{"amountMinor":$minor,"method":"$_method"}',
          createdAt: DateTime.now().toUtc(),
        ));
    setState(() {
      _checking = true;
      _message = null;
    });
    try {
      final started = await ref.read(payflowApiProvider).startTopUp(
            idempotencyKey: key,
            amountMinor: minor,
            method: _method,
          );
      final url = started.paymentUrl;
      if (url != null && url.isNotEmpty) {
        await launchUrl(Uri.parse(checkoutUrl(url, defaultApiBase)), mode: LaunchMode.externalApplication);
      }
      setState(() => _message = 'Finish the mock payment, then wait here.');
      await _poll(started.transactionId, key);
    } on PayflowTimeout {
      setState(() => _message = 'The gateway did not answer. Checking the top-up…');
    } on ApiException catch (error) {
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    } finally {
      if (mounted) {
        setState(() => _checking = false);
      }
    }
  }

  Future<void> _poll(String transactionId, String key) async {
    final deadline = DateTime.now().add(const Duration(seconds: 60));
    while (mounted && DateTime.now().isBefore(deadline)) {
      await Future<void>.delayed(const Duration(seconds: 2));
      try {
        final status = await ref.read(payflowApiProvider).topUpStatus(transactionId);
        if (status.status == 'COMPLETED' || status.status == 'FAILED') {
          await ref.read(localCacheProvider).deleteAttempt(key);
          setState(() => _message = 'Top-up ${status.status}.');
          return;
        }
        setState(() => _message = 'Status: ${status.status}');
      } catch (_) {
        // Keep the processing screen up while the next poll is due.
      }
    }
    if (mounted) {
      setState(() => _message = 'Still processing. You can leave this screen and check history.');
    }
  }
}
