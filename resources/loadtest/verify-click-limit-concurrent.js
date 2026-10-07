// 并发验证点击上限不会超发（可同时压多个实例）
// 用法：k6 run -e URI=<短链后缀> -e LIMIT=50 -e TOTAL=500 -e NODES=http://127.0.0.1:8013,http://127.0.0.1:8014 verify-click-limit-concurrent.js
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const URI = __ENV.URI;
const TOTAL = Number(__ENV.TOTAL || 500);
const LIMIT = Number(__ENV.LIMIT || 50);
const HOST = __ENV.HOST || 'nurl.ink:8003';
const ORIGIN = __ENV.ORIGIN || 'https://www.bilibili.com';
const NODES = (__ENV.NODES || 'http://127.0.0.1:8003').split(',');

const allowed = new Counter('allowed');
const blocked = new Counter('blocked');
const other = new Counter('other');

export const options = {
    scenarios: {
        burst: { executor: 'shared-iterations', vus: 50, iterations: TOTAL, maxDuration: '60s' },
    },
    summaryTrendStats: ['avg', 'p(95)'],
};

export default function () {
    const node = NODES[__ITER % NODES.length];
    const r = http.get(`${node}/${URI}`, { headers: { Host: HOST }, redirects: 0 });
    const loc = r.headers['Location'] || '';
    if (loc === ORIGIN) allowed.add(1);
    else if (loc.indexOf('notfound') >= 0) blocked.add(1);
    else other.add(1);
}

export function handleSummary(data) {
    const v = (m) => (data.metrics[m] && data.metrics[m].values.count) || 0;
    const a = v('allowed'), b = v('blocked'), o = v('other');
    const ok = a === LIMIT && o === 0;
    const msg = `\nlimit=${LIMIT} total=${TOTAL} allowed=${a} blocked=${b} other=${o}\n` +
        (ok ? '[PASS] 放行次数恰好等于上限，未超发\n' : '[FAIL] 放行次数与上限不一致\n');
    return { stdout: msg };
}
