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
  const TxRow({
    super.key,
    required this.type,
    required this.status,
    required this.when,
    required this.amountMinor,
    this.inbound,
    this.onTap,
  });

  final String type;
  final String status;
  final String when;
  final int amountMinor;
  final bool? inbound;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final comingIn = inbound ?? (type == 'TOPUP' || type == 'REFUND');
    final bubble = comingIn
        ? (dark ? const Color(0xFF1E3A32) : const Color(0xFFDCECE6))
        : (dark ? const Color(0xFF3A2E28) : const Color(0xFFF3E6D8));
    final amountColor = comingIn
        ? (dark ? const Color(0xFF3DDC97) : PayflowColors.green)
        : (dark ? const Color(0xFFFFB4A8) : PayflowColors.danger);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: bubble,
        child: Icon(
          comingIn ? Icons.south_west : Icons.north_east,
          color: dark ? const Color(0xFFF4F1EA) : PayflowColors.ink,
          size: 18,
        ),
      ),
      title: Text(_label(type), style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Text(when),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text('${comingIn ? '+' : '−'}${Paise.format(amountMinor)}', style: TextStyle(fontWeight: FontWeight.w700, color: amountColor)),
          const SizedBox(height: 4),
          StatusChip(status: status),
        ],
      ),
    );
  }

  static String _label(String type) {
    return switch (type) {
      'TOPUP' => 'Top up',
      'TRANSFER' => 'Transfer',
      'PAYMENT' => 'Payment',
      'REFUND' => 'Refund',
      _ => type,
    };
  }
}
