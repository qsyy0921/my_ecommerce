const { performance } = require('node:perf_hooks');

const config = {
  scenario: process.env.SCENARIO || 'seckill-query',
  marketHost: process.env.MARKET_HOST || 'http://127.0.0.1:8091',
  mallHost: process.env.MALL_HOST || 'http://127.0.0.1:8070',
  total: Number(process.env.TOTAL || 200),
  concurrency: Number(process.env.CONCURRENCY || 20),
  activityId: Number(process.env.ACTIVITY_ID || 900001),
  goodsId: process.env.GOODS_ID || '9890001',
  source: process.env.SOURCE || 's01',
  channel: process.env.CHANNEL || 'c01',
  payChannel: process.env.PAY_CHANNEL || 'mock',
  warmup: process.env.WARMUP !== 'false',
  queryResult: process.env.QUERY_RESULT === 'true',
  resultWaitMs: Number(process.env.RESULT_WAIT_MS || 80),
  userPrefix: process.env.USER_PREFIX || 'pt',
};

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = values.slice().sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
}

function payloadOf(index) {
  const userId = `${config.userPrefix}_${Date.now()}_${index}`;
  if (config.scenario === 'group-buy-query') {
    return {
      url: `${config.marketHost}/api/v1/gbm/index/query_group_buy_market_config`,
      body: {
        userId,
        source: config.source,
        channel: config.channel,
        goodsId: config.goodsId,
      },
    };
  }
  if (config.scenario === 'group-buy-lock') {
    return {
      url: `${config.marketHost}/api/v1/gbm/trade/lock_market_pay_order`,
      body: {
        userId,
        source: config.source,
        channel: config.channel,
        goodsId: config.goodsId,
        activityId: config.activityId,
        teamId: process.env.TEAM_ID || '',
        outTradeNo: shortBizNo('GB', index, 12),
        notifyConfigVO: {
          notifyType: 'MQ',
          notifyMQ: 'topic.team_success',
        },
      },
    };
  }
  if (config.scenario === 'mall-mock-pay-callback') {
    return {
      url: `${config.mallHost}/api/v1/alipay/create_pay_order`,
      callback: true,
      body: {
        userId,
        productId: config.goodsId,
        marketType: 0,
        payChannel: 'mock',
      },
    };
  }
  if (config.scenario === 'mall-seckill-pay') {
    return {
      url: `${config.mallHost}/api/v1/alipay/create_pay_order`,
      body: {
        userId,
        productId: config.goodsId,
        activityId: config.activityId,
        marketType: 2,
        payChannel: config.payChannel,
      },
    };
  }
  if (config.scenario === 'seckill-lock') {
    return {
      url: `${config.marketHost}/api/v1/gbm/seckill/lock_seckill_order`,
      body: {
        userId,
        source: config.source,
        channel: config.channel,
        goodsId: config.goodsId,
        activityId: config.activityId,
        outTradeNo: `${Date.now()}${index}`.slice(0, 32),
      },
    };
  }
  return {
    url: `${config.marketHost}/api/v1/gbm/seckill/query_seckill_market_config`,
    body: {
      userId,
      source: config.source,
      channel: config.channel,
      goodsId: config.goodsId,
      activityId: config.activityId,
    },
  };
}

function shortBizNo(prefix, index, maxLength) {
  const value = `${prefix}${Date.now().toString(36)}${index.toString(36)}`.toUpperCase();
  return value.slice(0, maxLength);
}

async function invoke(index) {
  const { url, body, callback } = payloadOf(index);
  const start = performance.now();
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'trace-id': `pressure-${config.scenario}-${index}`,
      },
      body: JSON.stringify(body),
    });
    const text = await response.text();
    const latency = performance.now() - start;
    let json = {};
    try {
      json = JSON.parse(text);
    } catch (_) {
      json = { code: 'NON_JSON', info: text.slice(0, 120) };
    }
    let resultStatus = json.data && json.data.resultStatus;
    if (callback && json.code === '0000') {
      const outTradeNo = extractInputValue(json.data, 'outTradeNo');
      if (outTradeNo) {
        const callbackUrl = `${config.mallHost}/api/v1/mock-pay/confirm?outTradeNo=${encodeURIComponent(outTradeNo)}&payAmount=0&subject=pressure`;
        const callbackResponse = await fetch(callbackUrl, {
          method: 'GET',
          headers: {
            'trace-id': `pressure-${config.scenario}-callback-${index}`,
          },
        });
        resultStatus = callbackResponse.ok ? 'CALLBACK_SUCCESS' : 'CALLBACK_FAIL';
      } else {
        resultStatus = 'CALLBACK_ORDER_ID_MISSING';
      }
    }
    if (config.scenario === 'seckill-lock' && config.queryResult && json.code === '0000') {
      await new Promise(resolve => setTimeout(resolve, config.resultWaitMs));
      const resultResponse = await fetch(`${config.marketHost}/api/v1/gbm/seckill/query_seckill_order_result`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'trace-id': `pressure-${config.scenario}-result-${index}`,
        },
        body: JSON.stringify({
          userId: body.userId,
          activityId: body.activityId,
          outTradeNo: body.outTradeNo,
        }),
      });
      const resultText = await resultResponse.text();
      try {
        const resultJson = JSON.parse(resultText);
        resultStatus = resultJson.data && resultJson.data.resultStatus;
      } catch (_) {
        resultStatus = 'NON_JSON';
      }
    }
    return {
      latency,
      httpOk: response.ok,
      businessOk: json.code === '0000',
      code: json.code || String(response.status),
      info: json.info || '',
      resultStatus,
    };
  } catch (error) {
    return {
      latency: performance.now() - start,
      httpOk: false,
      businessOk: false,
      code: 'EXCEPTION',
      info: error.message,
    };
  }
}

function extractInputValue(html, name) {
  if (!html) return '';
  const pattern = new RegExp(`name="${name}" value="([^"]+)"`);
  const match = String(html).match(pattern);
  return match ? match[1] : '';
}

async function run() {
  const results = [];
  let next = 0;
  const startedAt = performance.now();

  async function worker() {
    while (next < config.total) {
      const current = next++;
      results.push(await invoke(current));
    }
  }

  const workers = Array.from({ length: config.concurrency }, () => worker());
  await Promise.all(workers);

  const elapsed = (performance.now() - startedAt) / 1000;
  const latencies = results.map(item => item.latency);
  const codeCounts = results.reduce((acc, item) => {
    acc[item.code] = (acc[item.code] || 0) + 1;
    return acc;
  }, {});
  const resultStatusCounts = results.reduce((acc, item) => {
    if (item.resultStatus) {
      acc[item.resultStatus] = (acc[item.resultStatus] || 0) + 1;
    }
    return acc;
  }, {});

  const summary = {
    scenario: config.scenario,
    total: config.total,
    concurrency: config.concurrency,
    elapsedSeconds: Number(elapsed.toFixed(3)),
    throughput: Number((results.length / elapsed).toFixed(2)),
    httpSuccess: results.filter(item => item.httpOk).length,
    businessSuccess: results.filter(item => item.businessOk).length,
    businessFailed: results.filter(item => !item.businessOk).length,
    avgMs: Number((latencies.reduce((sum, item) => sum + item, 0) / latencies.length).toFixed(2)),
    p50Ms: Number(percentile(latencies, 50).toFixed(2)),
    p95Ms: Number(percentile(latencies, 95).toFixed(2)),
    p99Ms: Number(percentile(latencies, 99).toFixed(2)),
    maxMs: Number(Math.max(...latencies).toFixed(2)),
    codeCounts,
    resultStatusCounts,
  };

  console.log(JSON.stringify(summary, null, 2));
}

async function warmupSeckillStock() {
  const response = await fetch(`${config.marketHost}/api/v1/gbm/seckill/query_seckill_market_config`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'trace-id': `pressure-${config.scenario}-warmup`,
    },
    body: JSON.stringify({
      userId: 'pressure_warmup',
      source: config.source,
      channel: config.channel,
      goodsId: config.goodsId,
      activityId: config.activityId,
    }),
  });
  const text = await response.text();
  let json = {};
  try {
    json = JSON.parse(text);
  } catch (_) {
    json = { code: 'NON_JSON', info: text.slice(0, 120) };
  }
  console.log(JSON.stringify({
    warmup: true,
    httpStatus: response.status,
    code: json.code,
    availableCount: json.data && json.data.availableCount,
  }, null, 2));
}

async function main() {
  if (config.warmup && config.scenario !== 'seckill-query') {
    await warmupSeckillStock();
  }
  await run();
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
