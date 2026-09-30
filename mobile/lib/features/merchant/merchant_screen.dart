import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';

class MerchantScreen extends ConsumerStatefulWidget {
  const MerchantScreen({super.key});

  @override
  ConsumerState<MerchantScreen> createState() => _MerchantScreenState();
}

class _MerchantScreenState extends ConsumerState<MerchantScreen> {
  final _amount = TextEditingController();
  final _description = TextEditingController();
  PaymentRequestView? _request;
  String? _message;

  @override
  void dispose() {
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final request = _request;
    return Scaffold(
      appBar: AppBar(title: const Text('Payment request')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          TextField(controller: _amount, decoration: const InputDecoration(labelText: 'Amount (INR)')),
          const SizedBox(height: 12),
          TextField(controller: _description, decoration: const InputDecoration(labelText: 'Description')),
          const SizedBox(height: 16),
          FilledButton(onPressed: ref.watch(onlineProvider) ? _create : null, child: const Text('Create QR')),
          if (_message != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_message!)),
          if (request != null) ...[
            const SizedBox(height: 24),
            Center(child: QrImageView(data: request.qrPayload, size: 220)),
            const SizedBox(height: 8),
            Text(Paise.format(request.amountMinor)),
            Text(request.status),
            SelectableText(request.qrPayload),
          ],
        ],
      ),
    );
  }

  Future<void> _create() async {
    final minor = Paise.parse(_amount.text);
    if (minor == null) {
      setState(() => _message = 'Enter a rupee amount.');
      return;
    }
    try {
      final created = await ref.read(payflowApiProvider).createPaymentRequest(
            amountMinor: minor,
            description: _description.text.trim(),
          );
      setState(() {
        _request = created;
        _message = 'Waiting for a customer.';
      });
      await _watch(created.id);
    } on ApiException catch (error) {
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    }
  }

  Future<void> _watch(String id) async {
    while (mounted) {
      await Future<void>.delayed(const Duration(seconds: 2));
      try {
        final current = await ref.read(payflowApiProvider).paymentRequest(id);
        if (!mounted) {
          return;
        }
        setState(() => _request = current);
        if (current.status != 'OPEN') {
          setState(() => _message = 'Request is ${current.status}.');
          return;
        }
      } catch (_) {
        return;
      }
    }
  }
}
