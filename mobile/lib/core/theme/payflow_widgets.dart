import 'package:flutter/material.dart';

import '../money/paise.dart';
import 'app_theme.dart';

class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      'COMPLETED' || 'PAID' || 'CAPTURED' => PayflowColors.green,
      'FAILED' || 'REVERSED' => PayflowColors.danger,
      _ => const Color(0xFF92400E),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(999)),
      child: Text(status, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.3)),
    );
  }
}

class SurfaceCard extends StatelessWidget {
  const SurfaceCard({super.key, required this.child, this.color, this.padding = const EdgeInsets.all(18)});

  final Widget child;
  final Color? color;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: color ?? (dark ? const Color(0xFF1B2622) : PayflowColors.card),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: dark ? const Color(0xFF2E4038) : const Color(0xFFE7E1D6)),
      ),
      child: child,
    );
  }
}

class TxRow extends StatelessWidget {
  const TxRow({super.key, required this.type, required this.status, required this.when, required this.amountMinor, this.onTap});

  final String type;
  final String status;
  final String when;
  final int amountMinor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final inbound = type == 'TOPUP' || type == 'REFUND';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: inbound ? const Color(0xFFDCECE6) : const Color(0xFFF3E6D8),
        child: Icon(inbound ? Icons.south_west : Icons.north_east, color: PayflowColors.ink, size: 18),
      ),
      title: Text(type, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Text(when),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(Paise.format(amountMinor), style: const TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          StatusChip(status: status),
        ],
      ),
    );
  }
}
