import 'app_database.dart';
import 'drift_cache.dart';
import 'local_cache.dart';

Future<LocalCache> openLocalCache() async {
  final database = await AppDatabase.open();
  return DriftCache(database);
}
