import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/theme/payflow_widgets.dart';
import 'send_controller.dart';

class SendScreen extends ConsumerStatefulWidget {
  const SendScreen({super.key, this.initialRecipient});

  final String? initialRecipient;

  @override
  ConsumerState<SendScreen> createState() => _SendScreenState();
}

class _SendScreenState extends ConsumerState<SendScreen> {
  final _recipient = TextEditingController();
  final _amount = TextEditingController();
  final _note = TextEditingController();
  String? _amountError;

  @override
  void initState() {
    super.initState();
    final to = widget.initialRecipient;
    if (to != null && to.isNotEmpty) {
      _recipient.text = to;
    }
  }

  @override
  void dispose() {
    _recipient.dispose();
    _amount.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(sendControllerProvider);
    final online = ref.watch(onlineProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Send money')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          TextField(
            key: const Key('recipient'),
            controller: _recipient,
            decoration: const InputDecoration(labelText: 'Email or phone'),
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: 'Amount (INR)', errorText: _amountError),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            children: [
              for (final rupees in const [50, 100, 500, 1000])
                ActionChip(
                  label: Text('₹$rupees'),
                  onPressed: () => setState(() => _amount.text = '$rupees'),
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(controller: _note, decoration: const InputDecoration(labelText: 'Note')),
          const SizedBox(height: 20),
          FilledButton(
            key: const Key('review'),
            onPressed: !online || state is MoneySubmitting || state is MoneyChecking ? null : _review,
            child: Text(state is MoneySubmitting ? 'Sending…' : 'Review transfer'),
          ),
          const SizedBox(height: 16),
          _Result(state: state),
        ],
      ),
    );
  }

  Future<void> _review() async {
    final minor = Paise.parse(_amount.text);
    setState(() => _amountError = minor == null ? 'Enter a rupee amount such as 20.50' : null);
    if (minor == null || _recipient.text.trim().isEmpty) {
      return;
    }
    final recipient = _recipient.text.trim();
    final note = _note.text.trim();
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Confirm', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(Paise.format(minor), style: Theme.of(context).textTheme.headlineMedium),
              Text('To $recipient'),
              if (note.isNotEmpty) Text(note),
              const SizedBox(height: 16),
              FilledButton(
                key: const Key('confirm'),
                onPressed: () async {
                  Navigator.of(context).pop();
                  await ref.read(sendControllerProvider.notifier).confirm(
                        recipient: recipient,
                        amountMinor: minor,
                        note: note,
                      );
                },
                child: const Text('Send now'),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _Result extends StatelessWidget {
  const _Result({required this.state});

  final MoneyState state;

  @override
  Widget build(BuildContext context) {
    return switch (state) {
      MoneyIdle() => const SizedBox.shrink(),
      MoneySubmitting() => const LinearProgressIndicator(),
      MoneyChecking(:final message) => Text(message),
      MoneySuccess(:final title, :final detail) => SurfaceCard(child: Text('$title. $detail')),
      MoneyFailure(:final code, :final message) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(message.isEmpty ? friendlyError(code) : message),
            if (suggestsTopUp(code))
              TextButton(onPressed: () => context.go('/topup'), child: const Text('Top up')),
          ],
        ),
    };
  }
}
