import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/app_scope.dart';
import 'core/local/cache_loader.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final cache = await openLocalCache();
  final container = ProviderContainer(overrides: [
    localCacheProvider.overrideWithValue(cache),
  ]);
  await container.read(sessionProvider.notifier).restore();
  runApp(UncontrolledProviderScope(container: container, child: const PayflowApp()));
}
