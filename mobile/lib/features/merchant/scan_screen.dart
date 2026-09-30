import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';

String? requestIdFrom(String raw) {
  final uri = Uri.tryParse(raw.trim());
  final fromQuery = uri?.queryParameters['requestId'];
  if (fromQuery != null && fromQuery.isNotEmpty) {
    return fromQuery;
  }
  final match = RegExp(r'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}').firstMatch(raw);
  return match?.group(0);
}

class ScanScreen extends ConsumerStatefulWidget {
  const ScanScreen({super.key});

  @override
  ConsumerState<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends ConsumerState<ScanScreen> {
  final _manual = TextEditingController();
  final _camera = MobileScannerController();
  PaymentRequestView? _request;
  String? _message;
  bool _paying = false;
  bool _handled = false;

  @override
  void dispose() {
    _manual.dispose();
    _camera.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final request = _request;
    return Scaffold(
      appBar: AppBar(title: const Text('Scan and pay')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (!kIsWeb)
            SizedBox(
              height: 280,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(20),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    MobileScanner(controller: _camera, onDetect: (capture) {
                      final raw = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
                      if (raw != null) {
                        _load(raw);
                      }
                    }),
                    IgnorePointer(
                      child: Center(
                        child: Container(
                          width: 180,
                          height: 180,
                          decoration: BoxDecoration(
                            border: Border.all(color: Colors.white, width: 3),
                            borderRadius: BorderRadius.circular(16),
                          ),
                        ),
                      ),
                    ),
                    Positioned(
                      right: 8,
                      bottom: 8,
                      child: IconButton.filled(
                        onPressed: () => _camera.toggleTorch(),
                        icon: const Icon(Icons.flashlight_on_outlined),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 12),
          TextField(controller: _manual, decoration: const InputDecoration(labelText: 'Paste payflow:// link or request id')),
          const SizedBox(height: 8),
          FilledButton(onPressed: () => _load(_manual.text), child: const Text('Look up')),
          if (_message != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_message!)),
          if (request != null) ...[
            const SizedBox(height: 16),
            Text(Paise.format(request.amountMinor), style: Theme.of(context).textTheme.headlineSmall),
            Text(request.description ?? request.status),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _paying || !ref.watch(onlineProvider) ? null : _pay,
              child: Text(_paying ? 'Paying…' : 'Pay'),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _load(String raw) async {
    if (_handled) {
      return;
    }
    final id = requestIdFrom(raw);
    if (id == null) {
      setState(() => _message = 'That is not a PayFlow payment link.');
      return;
    }
    _handled = true;
    try {
      final request = await ref.read(payflowApiProvider).paymentRequest(id);
      setState(() {
        _request = request;
        _message = null;
      });
    } on ApiException catch (error) {
      _handled = false;
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    }
  }

  Future<void> _pay() async {
    final request = _request;
    if (request == null) {
      return;
    }
    final key = const Uuid().v4();
    await ref.read(localCacheProvider).saveAttempt(AttemptRecord(
          idempotencyKey: key,
          kind: 'payment',
          bodyJson: '{"paymentRequestId":"${request.id}"}',
          createdAt: DateTime.now().toUtc(),
        ));
    setState(() => _paying = true);
    try {
      final result = await ref.read(payflowApiProvider).pay(idempotencyKey: key, paymentRequestId: request.id);
      await ref.read(localCacheProvider).deleteAttempt(key);
      setState(() => _message = 'Paid. ${result.status}. Balance ${Paise.format(result.balanceAfterMinor)}.');
    } on PayflowTimeout {
      setState(() => _message = 'Checking whether the payment completed…');
    } on ApiException catch (error) {
      setState(() => _message = error.message.isEmpty ? friendlyError(error.code) : error.message);
    } finally {
      if (mounted) {
        setState(() => _paying = false);
      }
    }
  }
}
