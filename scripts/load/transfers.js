import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Point this at a running API. Register two funded users first and pass their tokens.
// Example:
//   k6 run -e TOKEN_A=... -e TOKEN_B=... -e EMAIL_B=bob@payflow.local scripts/load/transfers.js
export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export default function () {
  const fromA = __ITER % 2 === 0;
  const token = fromA ? __ENV.TOKEN_A : __ENV.TOKEN_B;
  const to = fromA ? __ENV.EMAIL_B : __ENV.EMAIL_A;
  const response = http.post(
    `${base}/transfers`,
    JSON.stringify({
      toUserEmailOrPhone: to,
      amountMinor: 100,
      note: 'load',
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': uuidv4(),
      },
    },
  );
  check(response, {
    'accepted or a business rejection': (res) => res.status === 201 || res.status === 422 || res.status === 409,
  });
  sleep(0.2);
}
