import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';
import '../../core/theme/payflow_widgets.dart';
import '../../core/theme/ui_prefs.dart';
import '../wallet/home_screen.dart';
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
  String? _recipientError;
  bool _lookingUp = false;

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
    final favorites = ref.watch(uiPrefsProvider).favorites;
    final balance = ref.watch(walletProvider).value?.balanceMinor;
    return Scaffold(
      appBar: AppBar(title: const Text('Send money')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (balance == 0) ...[
            const SurfaceCard(child: Text('You have ₹0.00. Add fake rupees before you send.')),
            const SizedBox(height: 8),
            OutlinedButton(onPressed: () => context.go('/topup'), child: const Text('Add money')),
            const SizedBox(height: 16),
          ],
          if (favorites.isNotEmpty) ...[
            const Text('People you paid'),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final email in favorites)
                  ActionChip(
                    label: Text(email.split('@').first),
                    onPressed: () => setState(() => _recipient.text = email),
                  ),
              ],
            ),
            const SizedBox(height: 12),
          ],
          TextField(
            key: const Key('recipient'),
            controller: _recipient,
            decoration: InputDecoration(labelText: 'Email or phone', errorText: _recipientError),
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
          const Text('What is this for?'),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final label in const ['Rent', 'Food', 'Travel', 'Friends'])
                ActionChip(label: Text(label), onPressed: () => setState(() => _note.text = label)),
            ],
          ),
          const SizedBox(height: 12),
          TextField(controller: _note, decoration: const InputDecoration(labelText: 'Note')),
          const SizedBox(height: 20),
          FilledButton(
            key: const Key('review'),
            onPressed: !online || _lookingUp || state is MoneySubmitting || state is MoneyChecking ? null : _review,
            child: Text(_lookingUp || state is MoneySubmitting ? 'Checking…' : 'Review transfer'),
          ),
          const SizedBox(height: 16),
          _Result(state: state),
        ],
      ),
    );
  }

  Future<void> _review() async {
    final minor = Paise.parse(_amount.text);
    final recipient = _recipient.text.trim();
    setState(() {
      _amountError = minor == null ? 'Enter a rupee amount such as 20.50' : null;
      _recipientError = recipient.isEmpty ? 'Enter an email or phone' : null;
    });
    if (minor == null || recipient.isEmpty) {
      return;
    }
    setState(() => _lookingUp = true);
    late final RecipientView person;
    try {
      person = await ref.read(payflowApiProvider).recipient(recipient);
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _lookingUp = false;
          _recipientError = error.status == 404 ? 'No wallet uses that email or phone.' : friendlyError(error.code);
        });
      }
      return;
    } catch (_) {
      if (mounted) {
        setState(() {
          _lookingUp = false;
          _recipientError = 'Could not check that person. Try again.';
        });
      }
      return;
    }
    if (!mounted) {
      return;
    }
    setState(() => _lookingUp = false);
    final note = _note.text.trim();
    final session = ref.read(sessionProvider);
    final own = session != null && session.email.toLowerCase() == person.email.toLowerCase();
    final warning = recentPayWarning(
      pays: ref.read(uiPrefsProvider).recent,
      recipient: person.email,
      now: DateTime.now().toUtc(),
    );
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
              Text('Send ${Paise.format(minor)}?', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(person.name, style: Theme.of(context).textTheme.headlineSmall),
              Text(person.email),
              if (note.isNotEmpty) Text(note),
              if (own) ...[
                const SizedBox(height: 12),
                const Text('That is your own wallet.'),
              ],
              if (warning != null) ...[
                const SizedBox(height: 12),
                Text(warning),
              ],
              const SizedBox(height: 16),
              FilledButton(
                key: const Key('confirm'),
                onPressed: own
                    ? null
                    : () async {
                        Navigator.of(context).pop();
                        await ref.read(sendControllerProvider.notifier).confirm(
                              recipient: person.email,
                              name: person.name,
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
