import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    seckill_lock: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 300),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500'],
  },
};

const host = __ENV.HOST || 'http://127.0.0.1:8091';
const activityId = Number(__ENV.ACTIVITY_ID || 900001);
const goodsId = __ENV.GOODS_ID || '9890001';
const source = __ENV.SOURCE || 's01';
const channel = __ENV.CHANNEL || 'c01';

export function setup() {
  const payload = JSON.stringify({
    userId: 'k6_warmup',
    source,
    channel,
    goodsId,
    activityId,
  });
  http.post(`${host}/api/v1/gbm/seckill/query_seckill_market_config`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'trace-id': 'k6-seckill-warmup',
    },
  });
}

export default function () {
  const userId = `u_${__VU}_${__ITER}`;
  const payload = JSON.stringify({
    userId,
    source,
    channel,
    goodsId,
    activityId,
    outTradeNo: `${Date.now()}${__VU}${__ITER}`.slice(0, 32),
  });

  const res = http.post(`${host}/api/v1/gbm/seckill/lock_seckill_order`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'trace-id': `k6-${__VU}-${__ITER}`,
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'business response exists': (r) => r.json('code') !== undefined,
  });

  sleep(0.1);
}
