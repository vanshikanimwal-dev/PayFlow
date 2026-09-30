import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';

class InboxScreen extends ConsumerStatefulWidget {
  const InboxScreen({super.key});

  @override
  ConsumerState<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends ConsumerState<InboxScreen> {
  List<dynamic> _items = [];
  String? _message;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await ref.read(controlsApiProvider).notifications();
      if (mounted) {
        setState(() {
          _items = items;
          _loading = false;
        });
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _message = error.message;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: _loading
          ? const _Shimmer()
          : _items.isEmpty
              ? Center(child: Text(_message ?? 'No alerts yet.\nPayments will show up here.', textAlign: TextAlign.center))
              : ListView(
                  children: [
                    for (final row in _items)
                      ListTile(
                        leading: const Icon(Icons.notifications_none),
                        title: Text((row as Map<String, dynamic>)['title']?.toString() ?? ''),
                        subtitle: Text(row['body']?.toString() ?? ''),
                        onTap: () async {
                          await ref.read(controlsApiProvider).markRead(row['id'].toString());
                          await _load();
                        },
                      ),
                  ],
                ),
    );
  }
}

class _Shimmer extends StatelessWidget {
  const _Shimmer();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: List.generate(
        4,
        (_) => Container(
          height: 64,
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(color: const Color(0xFF1B2622), borderRadius: BorderRadius.circular(16)),
        ),
      ),
    );
  }
}
