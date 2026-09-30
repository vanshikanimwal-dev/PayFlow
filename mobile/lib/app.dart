import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/app_scope.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/ui_prefs.dart';
import 'features/admin/admin_screen.dart';
import 'features/auth/auth_screen.dart';
import 'features/history/history_screen.dart';
import 'features/merchant/merchant_screen.dart';
import 'features/merchant/scan_screen.dart';
import 'features/more/inbox_screen.dart';
import 'features/more/more_screen.dart';
import 'features/more/welcome_screen.dart';
import 'features/settings/settings_screen.dart';
import 'features/topup/topup_screen.dart';
import 'features/transfer/send_screen.dart';
import 'features/wallet/home_screen.dart';

class PayflowApp extends ConsumerStatefulWidget {
  const PayflowApp({super.key});

  @override
  ConsumerState<PayflowApp> createState() => _PayflowAppState();
}

class _PayflowAppState extends ConsumerState<PayflowApp> {
  GoRouter? _router;
  final _sessionRefresh = _SessionRefresh();

  @override
  void initState() {
    super.initState();
    Connectivity().onConnectivityChanged.listen((results) {
      final online = results.any((result) => result != ConnectivityResult.none);
      ref.read(onlineProvider.notifier).setOnline(online);
    });
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(sessionProvider, (_, _) => _sessionRefresh.ping());
    _router ??= GoRouter(
      initialLocation: '/login',
      refreshListenable: _sessionRefresh,
      redirect: (context, state) {
        final loggedIn = ref.read(sessionProvider) != null;
        final authRoute = state.matchedLocation == '/login' || state.matchedLocation == '/register';
        if (!loggedIn && !authRoute) {
          return '/login';
        }
        if (loggedIn && authRoute) {
          return '/';
        }
        return null;
      },
      routes: [
        GoRoute(path: '/login', builder: (_, _) => const AuthScreen(register: false)),
        GoRoute(path: '/register', builder: (_, _) => const AuthScreen(register: true)),
        StatefulShellRoute.indexedStack(
          builder: (context, state, shell) => _Shell(shell: shell),
          branches: [
            StatefulShellBranch(routes: [GoRoute(path: '/', builder: (_, _) => const HomeScreen())]),
            StatefulShellBranch(routes: [
              GoRoute(path: '/history', builder: (_, _) => const HistoryScreen()),
              GoRoute(path: '/history/:id', builder: (_, state) => DetailScreen(id: state.pathParameters['id']!)),
            ]),
            StatefulShellBranch(routes: [GoRoute(path: '/settings', builder: (_, _) => const SettingsScreen())]),
          ],
        ),
        GoRoute(path: '/send', builder: (_, state) => SendScreen(initialRecipient: state.uri.queryParameters['to'])),
        GoRoute(path: '/topup', builder: (_, _) => const TopUpScreen()),
        GoRoute(path: '/scan', builder: (_, _) => const ScanScreen()),
        GoRoute(path: '/merchant', builder: (_, _) => const MerchantScreen()),
        GoRoute(path: '/admin', builder: (_, _) => const AdminScreen()),
        GoRoute(path: '/more', builder: (_, _) => const MoreScreen()),
        GoRoute(path: '/inbox', builder: (_, _) => const InboxScreen()),
        GoRoute(path: '/welcome', builder: (_, _) => const WelcomeScreen()),
      ],
    );
    final prefs = ref.watch(uiPrefsProvider);
    return MaterialApp.router(
      title: 'PayFlow',
      theme: payflowDarkTheme(),
      darkTheme: payflowDarkTheme(),
      themeMode: prefs.dark ? ThemeMode.dark : ThemeMode.light,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }
}

class _Shell extends StatelessWidget {
  const _Shell({required this.shell});

  final StatefulNavigationShell shell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: shell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: shell.currentIndex,
        onDestinationSelected: shell.goBranch,
        destinations: const [
          NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), label: 'Wallet'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Activity'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'Settings'),
        ],
      ),
    );
  }
}

class _SessionRefresh extends ChangeNotifier {
  void ping() => notifyListeners();
}
