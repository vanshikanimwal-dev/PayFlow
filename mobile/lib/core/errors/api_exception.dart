class ApiException implements Exception {
  ApiException(this.status, this.code, this.message);

  final int status;
  final String code;
  final String message;

  bool get isTimeout => code == 'TIMEOUT' || code == 'GATEWAY_TIMEOUT';
}

class PayflowTimeout implements Exception {
  const PayflowTimeout();
}

String friendlyError(String code) {
  switch (code) {
    case 'INSUFFICIENT_BALANCE':
      return 'Your wallet does not have enough for this.';
    case 'SELF_TRANSFER':
      return 'You cannot send money to yourself.';
    case 'ACCOUNT_INACTIVE':
      return 'That account is not active.';
    case 'LIMIT_EXCEEDED':
      return 'This is over the transfer limit.';
    case 'ALREADY_PAID':
      return 'This payment request was already paid.';
    case 'ALREADY_REFUNDED':
      return 'This payment was already refunded.';
    case 'IDEMPOTENCY_KEY_REUSED':
      return 'That attempt was already used with a different amount.';
    case 'IDEMPOTENCY_IN_PROGRESS':
      return 'That payment is still being processed.';
    case 'VALIDATION_ERROR':
      return 'Check the amount and the recipient.';
    case 'RATE_LIMITED':
      return 'Too many attempts. Wait a moment and try again.';
    case 'UNAUTHENTICATED':
    case 'TOKEN_EXPIRED':
      return 'Sign in again.';
    case 'FORBIDDEN':
      return 'You cannot do that with this account.';
    case 'NOT_FOUND':
      return 'That record was not found.';
    case 'TIMEOUT':
      return 'Still checking whether the payment went through.';
    default:
      return 'The request could not be completed.';
  }
}

bool suggestsTopUp(String code) => code == 'INSUFFICIENT_BALANCE';
