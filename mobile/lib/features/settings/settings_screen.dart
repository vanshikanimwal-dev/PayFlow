import 'package:flutter/material.dart';
import 'package:local_auth/local_auth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_scope.dart';
import '../../core/theme/app_theme.dart';
import '../../core/theme/payflow_widgets.dart';
import '../../core/theme/ui_prefs.dart';

Future<void> _unlock(BuildContext context) async {
  try {
    final ok = await LocalAuthentication().authenticate(localizedReason: 'Unlock PayFlow');
    if (ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Phone lock accepted')));
    }
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('This device has no fingerprint or face unlock')));
    }
  }
}

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    final prefs = ref.watch(uiPrefsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          SurfaceCard(
            child: Row(
              children: [
                CircleAvatar(
                  backgroundColor: PayflowColors.greenDeep,
                  child: Text(
                    (session?.email ?? '?').substring(0, 1).toUpperCase(),
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(session?.email ?? '', style: const TextStyle(fontWeight: FontWeight.w700)),
                    Text(session?.role ?? ''),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(tr(prefs.hindi, 'dark')),
            value: prefs.dark,
            onChanged: (_) => ref.read(uiPrefsProvider.notifier).toggleDark(),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(tr(prefs.hindi, 'language')),
            value: prefs.hindi,
            onChanged: (_) => ref.read(uiPrefsProvider.notifier).toggleLanguage(),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Unlock with fingerprint or face'),
            subtitle: const Text('Uses the lock already on this phone'),
            onTap: () => _unlock(context),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('PIN, savings, schedule, requests'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/more'),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Welcome and PIN'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/welcome'),
          ),
          const SizedBox(height: 20),
          const Text('This app talks to the PayFlow API. It never sends a real card.'),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: () async {
              await ref.read(sessionProvider.notifier).logout();
              if (context.mounted) {
                context.go('/login');
              }
            },
            child: const Text('Log out'),
          ),
        ],
      ),
    );
  }
}
