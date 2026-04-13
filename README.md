# AI Career Lab

> 사용자 맞춤 채용공고 추천 서비스 — 직무/지역/기술스택/경력 기반으로 채용공고를 자동 수집하고 적합도 점수를 산출해 매주 이메일로 발송합니다.

**서비스 주소**: https://ai-career-lab-rho.vercel.app

---

## 주요 기능

- **맞춤 조건 설정**: 직무 카테고리(15개) → 세부직무 2단계 선택, 기술스택, 지역, 경력 설정
- **자동 채용공고 수집**: 사람인 크롤링 (Jsoup, 키워드 11개, 상세페이지 파싱)
- **적합도 점수 계산**: 하드 필터(지역/경력/직무) + 소프트 스코어(스킬/태그/보너스) 78~98점
- **주간 이메일 발송**: 매주 월요일 오전 9시 상위 10개 공고 Gmail SMTP 발송
- **대시보드**: 날짜별 추천 공고, 점수 링, D-day 배지, 매칭 점수 분석 모달
- **즉시 추천**: "지금받기" 버튼으로 즉시 배치 실행
- **관리자 페이지**: 통계 조회, 크롤링/배치 수동 실행, 유저 관리

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.2.3, Spring Batch, Spring Security, Spring Data JPA |
| Database | MySQL 8, Hibernate |
| Crawler | Jsoup (정적 HTML 파싱, 3단계 폴백 전략) |
| Email | JavaMailSender, Gmail SMTP |
| Auth | Google OAuth2, BCrypt, HttpSession |
| Frontend | HTML5, CSS3, Vanilla JavaScript, Canvas API |
| Infra | AWS EC2 (t3.micro), Nginx, Let's Encrypt, Vercel, Ubuntu 22.04 |
| API Docs | Springdoc OpenAPI (Swagger UI) |

---

## 시스템 아키텍처

```
[Vercel]               [EC2 t3.micro - Seoul]
Frontend  ──HTTPS──▶  Nginx (443)
                          │
                          ▼
                      Spring Boot (8080)
                          │
                    ┌─────┴─────┐
                    ▼           ▼
                  MySQL      Gmail SMTP
```

---

## 핵심 구현

### Spring Batch 추천 파이프라인
```
ItemReader → ItemProcessor → ItemWriter
(유저 조회)   (점수 계산)    (추천 저장 + 이메일 발송)
```
- `@StepScope` 빈에서 채용공고 전체를 1회 로딩 후 모든 유저 처리에 재사용
- 청크 사이즈: 10 (유저 10명 단위 처리)
- 매주 월요일 09:00 자동 실행 + 즉시 실행 API 제공

### 점수 계산 알고리즘
```
totalScore = 기본점수(78) + 스킬매칭(0~12) + 태그매칭(0~5) + 직접매칭(0~3) + ID변동(0~2)
```
- **하드 필터**: 지역/경력/직무 불일치 시 0점 반환 (추천 제외)
- **스킬매칭**: 공고 요구스킬 대비 유저 보유스킬 비율 × 12
- **태그매칭**: 공고 태그 중 유저 직무 키워드 포함 비율 × 5

### Jsoup 크롤러 3단계 폴백
1. `.tb_recruit_info` 테이블에서 자격/우대/담당 파싱
2. `.wrap_jv_cont .cont_wrap` 섹션에서 파싱
3. 전체 텍스트에서 키워드 기반 섹션 추출

### 크로스 도메인 세션 쿠키
Vercel(프론트)과 EC2(백엔드)가 서로 다른 도메인이라 세션 쿠키 차단 문제 발생.
```java
// TomcatContextCustomizer로 SameSite=None 설정
processor.setSameSiteCookies(SameSiteCookies.NONE.getValue());
```
`forward-headers-strategy: native`로 Nginx X-Forwarded-Proto 인식.

---

## 성능 개선 (수치)

| 항목 | 개선 전 | 개선 후 | 효과 |
|------|---------|---------|------|
| 추천 조회 쿼리 수 | N+1 (추천 50건 → 51 쿼리) | 1 쿼리 (JOIN FETCH) | **98% 감소** |
| 관리자 유저 목록 쿼리 수 | 2N+1 (유저 100명 → 201 쿼리) | 3 쿼리 (일괄 조회) | **98.5% 감소** |
| 크롤러 URL 중복 체크 쿼리 수 | N (공고 500건 → 500 쿼리) | 1 쿼리 (IN 쿼리) | **99.8% 감소** |
| 크롤링 API 응답 대기 | 60초 이상 (동기) | 즉시 응답 (비동기 Thread) | - |
| 안정 동시 사용자 | 50명 | 200명 | **4배 향상** |

### 부하 테스트 (k6)

Nginx 튜닝으로 동시 사용자 처리 용량 4배 향상

**개선 내용**:
- `worker_connections`: 768 → 4096
- 시스템 파일 디스크립터(`ulimit`): 1,024 → 65,536
- SSL 세션 캐시 추가 (`shared:SSL:20m`) — TLS 재핸드쉐이크 제거
- `multi_accept on` + `use epoll` — I/O 다중화 최적화

**테스트 결과** (최대 200명 동시, 4분):

| 항목 | 수치 |
|------|------|
| 총 요청 수 | 8,518건 |
| TPS | 35.03 req/s |
| 성공률 | **99.98%** |
| 평균 응답시간 | 1,888ms |
| P90 | 3,740ms |
| P95 | 4,278ms |

---

## 문제 해결 경험

| 문제 | 원인 | 해결 |
|------|------|------|
| 대시보드 N+1 쿼리 | Lazy 로딩으로 추천 N건 → 공고 N번 SELECT | JOIN FETCH로 1회 조회 |
| 관리자 유저 목록 N+1 | 유저마다 프로필/추천수 개별 쿼리 | 프로필 일괄 조회 + GROUP BY 추천수 1쿼리 |
| 배치 공고 중복 로딩 | 유저마다 findAll() 호출 | @StepScope에서 1회 로딩 후 재사용 |
| 크롤러 중복 URL 체크 N번 | 공고마다 existsByUrl() 개별 SELECT | IN 쿼리로 기존 URL 일괄 조회 |
| 크롤링 API 타임아웃 | 동기 처리로 HTTP 타임아웃 | 백그라운드 Thread 비동기 실행 |
| 중복 추천 발생 | URL 변형 공고 재추천 | ID + URL 이중 필터 + DB 유니크 제약 |
| 크로스 도메인 세션 차단 | SameSite 기본값 Lax | TomcatContextCustomizer로 SameSite=None |
| OAuth2 state 불일치 | Nginx 뒤에서 HTTP로 인식 | forward-headers-strategy: native |
| EC2 메모리 고갈 | Java 프로세스 중복 실행 | 스왑 2GB + JVM 힙 제한(-Xmx400m) |
| JS 렌더링 사이트 크롤링 불가 | Jsoup은 정적 HTML만 파싱 | SSR 사이트(사람인)만 유지 |

---

## 프로젝트 구조

```
AI채용추천_백엔드/src/main/java/com/aicareerlab/
├── controller/
│   ├── AuthController.java          # OAuth2 콜백, 이메일 로그인
│   ├── ProfileController.java       # 프로필 CRUD
│   ├── RecommendationController.java # 추천 조회, 즉시 실행
│   └── AdminController.java         # 관리자 API
├── batch/
│   ├── RecommendationBatchConfig.java # Batch Job 설정
│   └── BatchScheduler.java           # 스케줄 + 즉시 실행
├── crawler/
│   ├── SaraminCrawler.java           # 사람인 크롤러
│   └── CrawlerService.java           # 크롤링 오케스트레이션
├── service/
│   ├── ScoreCalculator.java          # 점수 계산 알고리즘
│   ├── EmailService.java             # 이메일 발송
│   └── RecommendationService.java
├── entity/                           # JPA 엔티티
├── repository/                       # Spring Data JPA
└── config/
    ├── SecurityConfig.java           # Spring Security
    ├── CorsConfig.java               # CORS 설정
    └── SameSiteCookieFilter.java     # 크로스 도메인 쿠키

AI채용추천_프론트/
├── index.html     # 랜딩
├── login.html     # 로그인 (Google OAuth2 + 이메일/PW)
├── setup.html     # 조건 설정
├── dashboard.html # 추천 대시보드
└── admin.html     # 관리자 페이지
```

---

## 로컬 실행 방법

### 사전 요구사항
- Java 21
- MySQL 8
- IntelliJ IDEA

### 백엔드 실행
```bash
# MySQL에 aicareerlab DB 생성
mysql -u root -p -e "CREATE DATABASE aicareerlab;"

# application.yml 설정 확인 후 실행
cd AI채용추천_백엔드
./gradlew bootRun
```

### 프론트 실행
VS Code에서 Live Server 확장 설치 후 `index.html` 우클릭 → "Open with Live Server"

- 프론트: http://localhost:5500/AI채용추천_프론트/index.html
- Swagger: http://localhost:8080/swagger-ui.html

---

## API 문서

Swagger UI: https://aicareerlab.duckdns.org/swagger-ui.html

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/auth/login | 이메일/PW 로그인 |
| GET | /api/auth/me | 현재 로그인 유저 |
| GET/POST | /api/profile | 프로필 조회/저장 |
| GET | /api/recommendations | 추천 공고 조회 |
| POST | /api/recommendations/send-now | 즉시 추천 실행 |
| POST | /api/admin/crawl | 크롤링 실행 (관리자) |
| POST | /api/admin/batch | 전체 배치 실행 (관리자) |
| GET | /api/admin/stats | 통계 조회 (관리자) |
