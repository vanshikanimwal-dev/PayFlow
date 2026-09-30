import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/app_scope.dart';
import '../../core/auth/transaction_pin.dart';
import '../../core/errors/api_exception.dart';
import '../../core/theme/ui_prefs.dart';

class WelcomeScreen extends ConsumerStatefulWidget {
  const WelcomeScreen({super.key});

  @override
  ConsumerState<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends ConsumerState<WelcomeScreen> {
  final _name = TextEditingController();
  final _pin = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _pin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Welcome to PayFlow')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('This is a practice wallet. The photo step is a stand-in for KYC and is not sent anywhere.'),
          const SizedBox(height: 16),
          const Icon(Icons.account_circle, size: 72),
          const SizedBox(height: 16),
          TextField(controller: _name, decoration: const InputDecoration(labelText: 'Your name')),
          TextField(controller: _pin, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Transaction PIN')),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!)),
          const SizedBox(height: 16),
          FilledButton(onPressed: _finish, child: const Text('Continue')),
        ],
      ),
    );
  }

  Future<void> _finish() async {
    final pin = _pin.text.trim();
    if (pin.length < 4) {
      setState(() => _error = 'Choose a 4 to 6 digit PIN');
      return;
    }
    try {
      await ref.read(controlsApiProvider).setPin(pin);
      TransactionPin.value = pin;
      ref.read(uiPrefsProvider.notifier).finishOnboarding(_name.text.trim());
      if (mounted) {
        Navigator.of(context).pop();
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }
}
