# AI Career Lab — 프로젝트 현황 (최신)

## 프로젝트 개요
사용자가 직무/지역/기술스택/경력을 설정하면, 채용공고를 크롤링해서
적합도 점수 상위 10개를 매주 월요일 오전 9시 이메일로 발송하는 서비스.
대시보드에서도 동일한 추천 공고를 날짜별로 확인 가능.
"지금받기" 버튼으로 즉시 추천 가능.

---

## 기술 스택 (실제 사용 중)

| 영역 | 기술 |
|------|------|
| 프론트 | 순수 HTML/CSS/JS (프레임워크 없음) |
| 백엔드 | Spring Boot 3.2.3, Java 21 |
| 인증 | Google OAuth2 + 이메일/PW (세션 기반 HttpSession) |
| DB | MySQL (Hibernate ddl-auto: update) |
| 배치 | Spring Batch (매주 월 09:00, 즉시 실행 모두 지원) |
| 크롤러 | Jsoup (사람인만 동작, Wanted는 422 에러로 미작동) |
| 이메일 | Gmail SMTP (앱비밀번호 방식) |
| API 문서 | Springdoc Swagger |
| 인프라 | AWS EC2 t3.micro, Ubuntu 22.04, Nginx, Let's Encrypt, Vercel |
| 미사용(비활성화) | Redis, Elasticsearch |

---

## 프로젝트 구조

```
c:\ai채용추천\
├── AI채용추천_백엔드\
│   └── src\main\java\com\aicareerlab\
│       ├── controller\
│       │   ├── AuthController.java        # 로그인/로그아웃/OAuth2 콜백
│       │   ├── ProfileController.java     # 프로필 저장/조회
│       │   ├── RecommendationController.java  # 추천 조회/지금받기/크롤링
│       │   └── AdminController.java       # 관리자 API (stats/users/crawl/batch)
│       ├── entity\
│       │   ├── User.java                  # id, email, name, provider, role, createdAt
│       │   ├── UserProfile.java           # jobs, stacks, region, career, email, natural
│       │   ├── JobPosting.java            # title, company, location, platform, url(unique),
│       │   │                              #   tags, qualifications, requirements, preferred,
│       │   │                              #   salary, education, careerLevel, deadline
│       │   └── Recommendation.java        # user, jobPosting, score, baseScore, skillScore,
│       │                                  #   tagScore, bonusScore, matchedSkills, sentAt
│       │                                  #   unique(user_id, job_posting_id)
│       ├── dto\
│       │   ├── ScoreDetail.java           # totalScore, baseScore, skillScore, tagScore, bonusScore, matchedSkills
│       │   └── RecommendationGroupResponse.java  # date + jobs[] (score 세부 포함)
│       ├── batch\
│       │   ├── RecommendationBatchConfig.java  # Reader/Processor/Writer
│       │   └── BatchScheduler.java        # 주간 스케줄 + 즉시 실행
│       ├── crawler\
│       │   ├── SaraminCrawler.java        # Jsoup 크롤러 (5페이지, 상세페이지 파싱)
│       │   ├── WantedCrawler.java         # 422 에러로 미작동
│       │   └── CrawlerService.java        # 키워드별 크롤링 + 중복 URL 저장 방지
│       ├── service\
│       │   ├── ScoreCalculator.java       # calculate() / calculateDetail() 반환
│       │   ├── EmailService.java          # Gmail SMTP 이메일 발송
│       │   ├── AuthService.java
│       │   └── RecommendationService.java
│       ├── repository\
│       │   ├── UserRepository.java
│       │   ├── UserProfileRepository.java
│       │   ├── JobPostingRepository.java  # existsByUrl, findExistingUrls (IN 쿼리)
│       │   └── RecommendationRepository.java  # findRecommendedJobIdsByUserId
│       │                                       # findRecommendedJobUrlsByUserId
│       └── config\
│           ├── SecurityConfig.java        # /api/** 전체 permitAll, OAuth2 설정
│           ├── CorsConfig.java            # Vercel + localhost CORS 허용
│           └── SameSiteCookieFilter.java  # 크로스 도메인 쿠키 SameSite=None 설정
│
└── AI채용추천_프론트\
    ├── index.html       # 랜딩 페이지
    ├── login.html       # 구글 로그인 + 이메일/PW 로그인
    ├── setup.html       # 조건 설정 (직무 카테고리 → 세부직무 2단계)
    ├── dashboard.html   # 추천 공고 대시보드
    └── admin.html       # 관리자 페이지 (soo5586068@gmail.com 전용)
```

---

## 핵심 로직

### 점수 계산 (ScoreCalculator)
- **하드 필터** (0점 반환): 지역 불일치, 경력 불일치, 직무 키워드 불일치
- **점수 구성** (78~98점):
  - 기본점수: 78점
  - 스킬매칭: 공고 요구스킬 대비 유저 스킬 비율 × 12 (최대 +12)
  - 태그매칭: 태그 비율 × 5 (최대 +5)
  - 직접매칭: fullText에 유저 스킬 포함 수 (최대 +3)
  - ID 변동: posting.id % 3 (+0~2, 공고별 미세 차별화)
- `calculateDetail()` → ScoreDetail 반환 (세부 항목 분리)

### 중복 추천 방지
- Recommendation 엔티티: `unique(user_id, job_posting_id)`
- 배치 시 기추천 ID + URL 모두 조회해서 필터링
- Writer에서 개별 save + 예외 무시 (DB 제약 위반 시 스킵)

### 크롤링
- 사람인: 키워드별 최대 1000페이지 (공고 없으면 자동 break), 상세페이지 파싱
- 키워드: 백엔드, 프론트엔드, 풀스택, 데이터엔지니어, DevOps, 기획, 디자이너, 마케팅, 영업, 인사, 재무
- 공고 중복 저장 방지: IN 쿼리로 기존 URL 일괄 조회 후 필터링
- 크롤링 API: 비동기 실행 (백그라운드 Thread로 처리, 즉시 응답 반환)

### OAuth2 플로우
1. 구글 로그인 성공 → `/api/auth/oauth2/success`
2. email, name, userId 세션 저장
3. `https://ai-career-lab-rho.vercel.app/dashboard.html` 리다이렉트

### 세션 쿠키 크로스 도메인 처리
- Vercel(프론트)과 EC2(백엔드)가 서로 다른 도메인 → SameSite 쿠키 문제 발생
- TomcatContextCustomizer로 Rfc6265CookieProcessor의 SameSite=None 설정
- application-prod.yml에 `forward-headers-strategy: native` 추가 (Nginx X-Forwarded-Proto 인식)

### 관리자 페이지
- 접근: `soo5586068@gmail.com` 구글 로그인 시 dashboard.html nav에 금색 "관리자" 버튼 표시
- `/api/admin/**` — 세션 이메일로 관리자 인증
- 기능: 통계 조회, 유저 목록/삭제, 크롤링 실행(비동기), 전체 배치 실행

---

## 환경 설정

### 로컬 (application.yml)
- DB: `localhost:3306/aicareerlab` / root / 6068
- Gmail SMTP: `soo5586068@gmail.com` / 앱비밀번호
- Google OAuth2: 클라이언트 ID/Secret 설정 완료
- Redis: 비활성화 (none)

### 운영 (application-prod.yml — EC2 서버에만 존재)
- DB: `localhost:3306/aicareerlab` / aicareer / 6068
- `forward-headers-strategy: native` (Nginx HTTPS 프록시 인식)
- redirect-uri 명시: `https://aicareerlab.duckdns.org/login/oauth2/code/google`

---

## 배포 현황 ✅ 완료

### 서비스 주소
- **프론트**: https://ai-career-lab-rho.vercel.app
- **백엔드 API**: https://aicareerlab.duckdns.org
- **Swagger**: https://aicareerlab.duckdns.org/swagger-ui.html

### 인프라
- **EC2**: t3.micro, Ubuntu 22.04 LTS, 서울 리전 (ap-northeast-2)
- **퍼블릭 IP**: 54.180.103.141 (Stop/Start 시 변경 — DuckDNS 토큰으로 업데이트 필요)
- **DuckDNS 토큰**: 3d6d6c55-94c4-4b20-b403-bacd8008f63e
- **스왑**: 2GB (/swapfile) — t3.micro 메모리 부족 방지
- **systemd**: aicareerlab.service (서버 재시작 시 자동 실행)
- **Nginx**: 리버스 프록시 (80 → HTTPS 리다이렉트, 443 → 8080)
- **HTTPS**: Let's Encrypt (certbot, 2026-07-09 만료, 자동 갱신)

### EC2 접속
```powershell
ssh -i "C:\Users\USER\Downloads\aicareerlab-key.pem" ubuntu@54.180.103.141
```

### IP 변경 시 DuckDNS 업데이트
```
https://www.duckdns.org/update?domains=aicareerlab&token=3d6d6c55-94c4-4b20-b403-bacd8008f63e&ip=새IP주소
```

### EC2 운영 명령어
```bash
sudo systemctl restart aicareerlab   # 앱 재시작
sudo systemctl status aicareerlab    # 상태 확인
sudo journalctl -u aicareerlab -f    # 실시간 로그
tail -f ~/app.log                    # 앱 로그
free -h                              # 메모리 확인
```

### JAR 재배포 절차 (로컬 → EC2)
```powershell
# 1. 로컬 빌드
cd "c:\ai채용추천\AI채용추천_백엔드"
./gradlew bootJar

# 2. 복사 + 업로드
copy "c:\ai채용추천\AI채용추천_백엔드\build\libs\ai-career-lab-0.0.1-SNAPSHOT.jar" "c:\app.jar"
scp -i "C:\Users\USER\Downloads\aicareerlab-key.pem" "C:\app.jar" ubuntu@54.180.103.141:~/app.jar

# 3. EC2에서 재시작
sudo systemctl restart aicareerlab
```

---

## 완료된 작업 ✅

### 백엔드
- [x] Spring Boot 프로젝트 세팅 (Gradle, Java 21)
- [x] MySQL 연결 + 전체 엔티티 (User, UserProfile, JobPosting, Recommendation)
- [x] Google OAuth2 로그인 + 이메일/PW 로그인
- [x] 프로필 저장/조회 API
- [x] 사람인 크롤러 (Jsoup, 상세페이지 3단계 파싱 폴백)
- [x] Spring Batch 추천 파이프라인 (주간 + 즉시 실행)
- [x] 점수 계산 (하드 필터 + 소프트 스코어, 78~98점)
- [x] 점수 세부 내역 저장 (ScoreDetail, 배치 Writer에서 저장)
- [x] 중복 추천 완전 방지 (ID + URL 이중 체크 + DB 유니크 제약)
- [x] Gmail SMTP 이메일 발송
- [x] 관리자 API (AdminController)
- [x] N+1 쿼리 최적화 (JOIN FETCH, 배치 IN 쿼리)
- [x] 크롤링 비동기 처리 (백그라운드 Thread)
- [x] SameSite=None 쿠키 설정 (크로스 도메인 세션)
- [x] Swagger 설정

### 프론트엔드
- [x] index.html — 랜딩
- [x] login.html — 구글 OAuth + 이메일/PW
- [x] setup.html — 15개 카테고리 / 세부직무 2단계 / 스킬 / 지역 / 경력
- [x] dashboard.html — 날짜별 추천 공고, 점수 링, 카드 상세 펼치기, D-day 배지
  - "공고 보러가기" + "매칭점수 분석하기" 버튼
  - 점수 분석 모달 (기본점수/스킬/태그/보너스 + 바 차트)
  - 필터 (전체/사람인/90점+)
  - 지금받기, NEXT_BATCH 카운트다운
  - 관리자 계정만 "관리자" 버튼 표시
- [x] admin.html — 관리자 전용
  - 통계 카드 (유저/프로필/공고/추천 수)
  - 크롤링 / 배치 수동 실행 (비동기)
  - 유저 테이블 (검색, 삭제)
  - 실행 로그

### 배포
- [x] AWS EC2 t3.micro 인스턴스 생성 및 설정
- [x] Java 21 + MySQL 설치
- [x] Nginx 리버스 프록시
- [x] Let's Encrypt HTTPS 인증서
- [x] systemd 자동 실행 등록
- [x] 스왑 2GB 추가
- [x] Vercel 프론트 배포
- [x] Google OAuth2 리다이렉트 URI 등록
- [x] 크로스 도메인 세션 쿠키 문제 해결

---

## 남은 작업 (선택)
- [ ] GitHub Actions CI/CD (코드 push 시 자동 배포)
- [ ] 테스트 코드 (JUnit, Mockito)
- [ ] 이메일 HTML 템플릿 디자인 개선
- [ ] setup.html 자연어 조건 실제 활용 (현재 저장만 됨)
- [ ] 추천 공고 조기 마감 감지

---

## 포트폴리오 / 이력서용 정리

### 문제 해결 경험 (수치 포함)

| 문제 | 원인 | 해결 | 수치 |
|------|------|------|------|
| 대시보드 N+1 쿼리 | Lazy 로딩으로 추천 N건 → 공고 N번 SELECT | JOIN FETCH 쿼리로 1회 조회 | 쿼리 수 N+1 → 1 (추천 50건 기준 51 → 1, 98% 감소) |
| 관리자 유저 목록 N+1 | 유저마다 프로필/추천수 개별 쿼리 (2N+1) | 프로필 일괄 조회 + GROUP BY 추천수 1쿼리 | 쿼리 수 2N+1 → 3 (유저 100명 기준 201 → 3, 98.5% 감소) |
| 크롤러 URL 중복체크 N번 | 공고마다 existsByUrl() 개별 SELECT | IN 쿼리로 기존 URL 일괄 조회 | 쿼리 수 N → 1 (공고 500건 기준 500 → 1, 99.8% 감소) |
| 배치 공고 중복 로딩 | 유저마다 findAll() 호출 | @StepScope에서 1회 로딩 후 재사용 | DB 조회 N → 1 (유저 10명 기준 10 → 1) |
| 동시 사용자 50명 한계 | Nginx worker_connections 768, ulimit 1024, SSL 캐시 없음 → TLS 핸드쉐이크 과부하 | worker_connections 4096, ulimit 65536, SSL session cache 20MB, epoll 설정 | 안정 동시 사용자 50명 → 200명, 성공률 99.98%, TPS 35 req/s |
| 크롤링 API 타임아웃 | 동기 처리로 HTTP 요청 타임아웃 발생 | 백그라운드 Thread 비동기 실행 | 응답 대기 60s+ → 즉시 응답 |
| 중복 추천 발생 | URL 변형 공고가 동일 공고로 재추천됨 | ID + URL 이중 필터 + DB 유니크 제약 | - |
| 크로스 도메인 세션 쿠키 차단 | Vercel↔EC2 다른 도메인, SameSite 기본값이 Lax | TomcatContextCustomizer로 SameSite=None; Secure 설정 | - |
| Nginx 뒤에서 OAuth2 state 불일치 | Spring Boot가 HTTP로 인식해 state 쿠키 설정 오류 | forward-headers-strategy: native로 X-Forwarded-Proto 인식 | - |
| EC2 SSH 매우 느려짐 | Java 프로세스 2개 이상 동시 실행으로 메모리 고갈 | 스왑 2GB 추가 + JVM 힙 제한(-Xmx400m) | - |
| JS 렌더링 사이트 크롤링 불가 | Jsoup은 정적 HTML만 파싱 가능 | SSR 사이트(사람인)만 유지 | - |

### 부하 테스트 결과 (k6)
- **테스트 도구**: k6
- **시나리오**: 0 → 30 → 100 → 200명 단계적 증가 (총 4분)
- **개선 전 문제**: worker_connections 768 + ulimit 1024로 50명 초과 시 `tls: bad record MAC` 에러 다수 발생
- **개선 내용**:
  - Nginx `worker_connections`: 768 → 4096 (5.3배)
  - 시스템 파일 디스크립터(`ulimit -n`): 1024 → 65536 (64배)
  - SSL 세션 캐시 추가: `ssl_session_cache shared:SSL:20m` (TLS 재핸드쉐이크 제거)
  - `multi_accept on` + `use epoll` (I/O 다중화 최적화)
- **개선 후 결과**:

| 항목 | 수치 |
|------|------|
| 최대 동시 사용자 | 200명 |
| 총 요청 수 | 8,518건 |
| TPS | 35.03 req/s |
| 성공률 | 99.98% |
| 평균 응답시간 | 1,888ms |
| 중앙값 | 1,678ms |
| P90 | 3,740ms |
| P95 | 4,278ms |

### 이력서용 성과 문장
- **N+1 쿼리 최적화**: JOIN FETCH 도입으로 추천 조회 쿼리 수 98% 감소 (51건 → 1건)
- **관리자 API 최적화**: 일괄 조회 쿼리로 DB 요청 98.5% 감소 (201건 → 3건)
- **크롤러 최적화**: IN 쿼리 일괄 중복 체크로 DB 요청 99.8% 감소 (500건 → 1건)
- **Nginx 튜닝**: worker_connections, SSL 세션 캐시, epoll 설정으로 안정 동시 처리 사용자 50명 → 200명 (4배), 성공률 99.98%
- **크롤링 비동기화**: 동기 처리 대비 응답 대기 60초 이상 → 즉시 응답

### 핵심 구현 포인트 (면접용)
- Spring Batch 청크 기반 파이프라인 설계 (Reader → Processor → Writer)
- `@StepScope` 빈에서 공유 데이터 1회 로딩 패턴으로 배치 성능 개선
- 하드 필터(지역/경력/직무) + 소프트 스코어(스킬/태그/보너스) 복합 점수 알고리즘 설계
- DB 유니크 제약 + 애플리케이션 레벨 이중 중복 방지로 동시성 안전성 확보
- Google OAuth2 + 세션 기반 인증 + 관리자 권한 분리
- Jsoup 3단계 파싱 폴백 전략 (테이블 → 섹션 → 전체 텍스트 키워드 추출)
- TomcatContextCustomizer로 SameSite=None 쿠키 설정 (크로스 도메인 세션)
- Nginx 리버스 프록시 환경에서 forward-headers-strategy로 HTTPS 인식
- AWS EC2 t3.micro에 Spring Boot + MySQL 단일 서버 배포 (비용 최소화)
- 스왑 메모리 + JVM 힙 제한으로 1GB RAM 서버 안정화
- k6 부하 테스트로 병목 분석 및 Nginx 튜닝으로 처리 용량 4배 향상

### 사용 기술 전체 목록 (이력서용)
**Backend**: Java 21, Spring Boot 3.2.3, Spring Batch, Spring Security, Spring Data JPA, Hibernate, MySQL, Jsoup, JavaMailSender, Springdoc OpenAPI, Gradle
**Frontend**: HTML5, CSS3, Vanilla JavaScript, Canvas API
**Infra**: AWS EC2 (t3.micro), Ubuntu 22.04, Nginx, Let's Encrypt (certbot), Vercel
**Auth**: Google OAuth2, BCrypt, HttpSession, SameSite Cookie
**Tools**: IntelliJ, VS Code, Swagger UI, DuckDNS

---

## 개발 환경 실행 방법

- **백엔드**: IntelliJ에서 `AiCareerLabApplication` 실행 (포트 8080)
- **프론트**: VS Code 하단 상태바 **"Go Live"** 버튼 클릭 → 포트 5500 자동 오픈
- **프론트 주소**: `http://localhost:5500/AI채용추천_프론트/index.html`
- **Swagger**: `http://localhost:8080/swagger-ui.html`
