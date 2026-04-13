import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const recommendDuration = new Trend('recommend_duration');

export const options = {
  insecureSkipTLSVerify: true,  // TLS 검증 오버헤드 제거
  stages: [
    { duration: '30s', target: 30 },   // 0 → 30명 (워밍업)
    { duration: '1m',  target: 100 },  // 30 → 100명
    { duration: '1m',  target: 200 },  // 100 → 200명
    { duration: '1m',  target: 200 },  // 200명 유지
    { duration: '30s', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    errors: ['rate<0.1'],
  },
};

const BASE_URL = 'https://aicareerlab.duckdns.org';

const params = {
  headers: { 'Content-Type': 'application/json' },
  timeout: '10s',
};

export default function () {
  // 이메일 로그인
  const loginStart = Date.now();
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: `testuser${__VU % 50}@test.com`, password: 'test123456' }),
    params
  );
  loginDuration.add(Date.now() - loginStart);

  const loginOk = check(loginRes, {
    '로그인 2xx/4xx': (r) => r.status < 500,
    '응답시간 < 3s': (r) => r.timings.duration < 3000,
  });
  errorRate.add(!loginOk);

  sleep(1);

  // 추천 조회
  const recStart = Date.now();
  const recRes = http.get(`${BASE_URL}/api/recommendations`, params);
  recommendDuration.add(Date.now() - recStart);

  check(recRes, {
    '추천 조회 응답': (r) => r.status < 500,
  });

  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  const avg = data.metrics.http_req_duration.values.avg.toFixed(0);
  const med = data.metrics.http_req_duration.values.med.toFixed(0);
  const p90 = data.metrics.http_req_duration.values['p(90)'].toFixed(0);
  const p95 = data.metrics.http_req_duration.values['p(95)'].toFixed(0);
  const max = data.metrics.http_req_duration.values.max.toFixed(0);
  const tps = data.metrics.http_reqs.values.rate.toFixed(2);
  const total = data.metrics.http_reqs.values.count;
  const failed = data.metrics.http_req_failed.values.count;
  const errRate = ((data.metrics.http_req_failed.values.rate || 0) * 100).toFixed(2);

  return {
    stdout: `
========================================
  AI Career Lab 부하 테스트 결과
  서버: AWS EC2 t3.micro (1GB RAM + 2GB Swap)
  최대 동시 사용자: 200명
========================================
  총 요청 수:  ${total}건
  TPS:         ${tps} req/s
  실패 요청:   ${failed}건 (${errRate}%)

  [응답시간]
  평균:   ${avg}ms
  중앙값: ${med}ms
  P90:    ${p90}ms
  P95:    ${p95}ms
  최대:   ${max}ms
========================================
`,
  };
}
