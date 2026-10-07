// 短链接跳转压测（k6）
// 用法：k6 run -e VUS=50 -e DURATION=30s -e URI=HnCzd resources/loadtest/restore.js
import http from 'k6/http';
import { check } from 'k6';

const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '30s';
const URI = __ENV.URI || 'HnCzd';
const HOST = __ENV.HOST || 'nurl.ink:8003';
const TARGET = __ENV.TARGET || 'http://127.0.0.1:8003';

export const options = {
    scenarios: {
        restore: { executor: 'constant-vus', vus: VUS, duration: DURATION },
    },
    thresholds: { checks: ['rate>0.99'] },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const res = http.get(`${TARGET}/${URI}`, {
        headers: { Host: HOST },
        redirects: 0,
    });
    check(res, { 'is 302': (r) => r.status === 302 });
}
