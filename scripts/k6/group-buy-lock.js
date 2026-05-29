import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const successCount = new Counter('group_buy_business_success');
const fullCount = new Counter('group_buy_team_full_or_invalid');
const errorCount = new Counter('group_buy_business_error');

export const options = {
  scenarios: {
    group_buy_lock: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 50),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 300),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const baseUrl = __ENV.MARKET_BASE_URL || 'http://127.0.0.1:8091';
const activityId = Number(__ENV.ACTIVITY_ID || 100123);
const goodsId = __ENV.GOODS_ID || '9890001';
const source = __ENV.SOURCE || 's01';
const channel = __ENV.CHANNEL || 'c01';
const teamId = __ENV.TEAM_ID || '';
const mode = __ENV.MODE || 'new_team';

function lockGroupBuy(userId, outTradeNo, selectedTeamId) {
  const payload = JSON.stringify({
    userId,
    source,
    channel,
    goodsId,
    activityId,
    teamId: selectedTeamId,
    outTradeNo,
    notifyConfigVO: {
      notifyType: 'MQ',
      notifyMQ: 'topic.team_success',
    },
  });

  return http.post(`${baseUrl}/api/v1/gbm/trade/lock_market_pay_order`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
}

export function setup() {
  if (teamId || mode !== 'same_team') {
    return { teamId };
  }

  const unique = `setup_${Date.now()}`;
  const res = lockGroupBuy(`gb_owner_${unique}`, `GB_OWNER_${unique}`, '');
  const createdTeamId = res.json('data.teamId');
  if (!createdTeamId) {
    throw new Error(`create group-buy team failed: ${res.body}`);
  }
  return { teamId: createdTeamId };
}

export default function (data) {
  const unique = `${__VU}${__ITER}${Date.now()}`;
  const selectedTeamId = mode === 'same_team' ? data.teamId : teamId;
  const res = lockGroupBuy(`gb_press_${unique}`, `GB${unique}`, selectedTeamId);
  const code = res.json('code');
  if (code === '0000') {
    successCount.add(1);
  } else if (code === 'E0006' || code === 'E0008' || code === 'E0107') {
    fullCount.add(1);
  } else {
    errorCount.add(1);
  }

  check(res, {
    'status is 200': (r) => r.status === 200,
    'business response exists': (r) => !!r.json('code'),
    'business code is expected': () => code === '0000' || code === 'E0006' || code === 'E0008' || code === 'E0107',
  });

  sleep(0.1);
}
