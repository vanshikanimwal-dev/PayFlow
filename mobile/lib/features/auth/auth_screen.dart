import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key, required this.register});

  final bool register;

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _phone = TextEditingController();
  final _code = TextEditingController();
  String _role = 'USER';
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 36),
              Text('PayFlow', style: Theme.of(context).textTheme.labelLarge?.copyWith(letterSpacing: 2.4, fontWeight: FontWeight.w800)),
              const SizedBox(height: 12),
              const CircleAvatar(radius: 28, backgroundColor: Color(0xFF146B54), child: Icon(Icons.account_balance_wallet_outlined, color: Colors.white)),
              const SizedBox(height: 16),
              Text(widget.register ? 'Create a wallet' : 'Welcome back', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              const Text('Simulated rupees only. Nothing here moves real money.'),
              const SizedBox(height: 24),
              TextField(controller: _email, decoration: const InputDecoration(labelText: 'Email'), keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 12),
              TextField(controller: _password, decoration: const InputDecoration(labelText: 'Password'), obscureText: true),
              if (!widget.register) ...[
                const SizedBox(height: 12),
                TextField(controller: _code, decoration: const InputDecoration(labelText: 'Authenticator code, if you turned it on')),
              ],
              if (widget.register) ...[
                const SizedBox(height: 12),
                TextField(controller: _phone, decoration: const InputDecoration(labelText: 'Phone (optional)')),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: _role,
                  decoration: const InputDecoration(labelText: 'Role'),
                  items: const [
                    DropdownMenuItem(value: 'USER', child: Text('Customer')),
                    DropdownMenuItem(value: 'MERCHANT', child: Text('Merchant')),
                  ],
                  onChanged: (value) => setState(() => _role = value ?? 'USER'),
                ),
              ],
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: Text(_busy ? 'Please wait…' : (widget.register ? 'Register' : 'Log in')),
              ),
              TextButton(
                onPressed: () => context.go(widget.register ? '/login' : '/register'),
                child: Text(widget.register ? 'Already have an account' : 'Create an account'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(payflowApiProvider);
      final result = widget.register
          ? await api.register(email: _email.text.trim(), phone: _phone.text.trim(), password: _password.text, role: _role)
          : await api.login(email: _email.text.trim(), password: _password.text, code: _code.text.trim());
      await ref.read(sessionProvider.notifier).adopt(result.accessToken, result.refreshToken);
      if (mounted) {
        context.go('/');
      }
    } on ApiException catch (error) {
      setState(() => _error = error.message.isEmpty ? friendlyError(error.code) : error.message);
    } catch (error) {
      setState(() => _error = 'Could not reach the API. Is Docker running?');
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }
}
