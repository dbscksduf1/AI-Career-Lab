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
│       │   ├── JobPostingRepository.java  # existsByUrl, findByUrl
│       │   └── RecommendationRepository.java  # findRecommendedJobIdsByUserId
│       │                                       # findRecommendedJobUrlsByUserId
│       └── config\
│           └── SecurityConfig.java        # /api/** 전체 permitAll, OAuth2 설정
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
- 사람인: 키워드별 5페이지, 상세페이지 파싱 (지원자격/요구사항/우대사항)
- 키워드: 백엔드, 프론트엔드, 풀스택, 데이터엔지니어, DevOps, 기획, 디자이너, 마케팅, 영업, 인사, 재무
- 공고 중복 저장 방지: `existsByUrl()` 체크 + url 컬럼 unique 제약

### OAuth2 플로우
1. 구글 로그인 성공 → `/api/auth/oauth2/success`
2. email, name, userId 세션 저장
3. `http://localhost:5500/AI채용추천_프론트/dashboard.html` 리다이렉트

### 관리자 페이지
- 접근: `soo5586068@gmail.com` 구글 로그인 시 dashboard.html nav에 금색 "관리자" 버튼 표시
- `/api/admin/**` — 세션 이메일로 관리자 인증
- 기능: 통계 조회, 유저 목록/삭제, 크롤링 실행, 전체 배치 실행

---

## 환경 설정 (application.yml)
- DB: `localhost:3306/aicareerlab` / root / 6068
- Gmail SMTP: `soo5586068@gmail.com` / 앱비밀번호 `ccwbfnszvagalvat`
- Google OAuth2: 클라이언트 ID/Secret 설정 완료
- Redis: 비활성화 (none)
- 배치 자동실행: disabled (수동 또는 스케줄만)

---

## 완료된 작업 ✅

### 백엔드
- [x] Spring Boot 프로젝트 세팅 (Gradle, Java 21)
- [x] MySQL 연결 + 전체 엔티티 (User, UserProfile, JobPosting, Recommendation)
- [x] Google OAuth2 로그인 + 이메일/PW 로그인
- [x] 프로필 저장/조회 API
- [x] 사람인 크롤러 (Jsoup, 상세페이지 파싱)
- [x] Spring Batch 추천 파이프라인 (주간 + 즉시 실행)
- [x] 점수 계산 (하드 필터 + 소프트 스코어, 78~98점)
- [x] 점수 세부 내역 저장 (ScoreDetail, 배치 Writer에서 저장)
- [x] 중복 추천 완전 방지 (ID + URL 이중 체크 + DB 유니크 제약)
- [x] Gmail SMTP 이메일 발송
- [x] 관리자 API (AdminController)
- [x] Swagger 설정

### 프론트엔드
- [x] index.html — 랜딩
- [x] login.html — 구글 OAuth + 이메일/PW
- [x] setup.html — 15개 카테고리 / 세부직무 2단계 / 스킬 / 지역 / 경력
- [x] dashboard.html — 날짜별 추천 공고, 점수 링, 카드 상세 펼치기
  - "공고 보러가기" + "매칭점수 분석하기" 버튼
  - 점수 분석 모달 (기본점수/스킬/태그/보너스 + 바 차트)
  - 필터 (전체/사람인/원티드/90점+)
  - 지금받기, NEXT_BATCH 카운트다운
  - 관리자 계정만 "관리자" 버튼 표시
- [x] admin.html — 관리자 전용
  - 통계 카드 (유저/프로필/공고/추천 수)
  - 크롤링 / 배치 수동 실행
  - 유저 테이블 (검색, 삭제)
  - 실행 로그

---

## 배포 현황 🚀

### AWS EC2 배포 진행 중
- **EC2 인스턴스**: t3.micro, Ubuntu 22.04 LTS, 서울 리전 (ap-northeast-2)
- **퍼블릭 IP**: 15.165.161.143
- **완료된 배포 작업**:
  - [x] EC2 인스턴스 생성 (t3.micro, 30GB)
  - [x] 보안 그룹 설정 (22, 80, 443, 8080 포트 오픈)
  - [x] Java 21 설치
  - [x] MySQL 설치 + DB/유저 생성 (aicareerlab DB, aicareer 유저)
  - [x] application-prod.yml 작성 (EC2용 환경설정)
  - [x] JAR 빌드 + EC2 업로드 (ai-career-lab-0.0.1-SNAPSHOT.jar)
  - [x] 백엔드 실행 확인 (Started AiCareerLabApplication)
  - [x] Swagger 외부 접속 확인 (http://15.165.161.143:8080/swagger-ui.html)
  - [x] Nginx 설치 + 리버스 프록시 설정
  - [x] DuckDNS 무료 도메인 발급 (aicareerlab.duckdns.org → 15.165.161.143)
  - [x] Google OAuth2 리다이렉트 URI 추가 (http://aicareerlab.duckdns.org/login/oauth2/code/google)

- **다음 배포 단계**:
  - [ ] HTTPS 인증서 발급 (Let's Encrypt certbot) — DNS 전파 문제로 보류
  - [ ] systemd 서비스 등록 (서버 재시작 시 자동 실행)
  - [ ] 프론트 Vercel 배포
  - [ ] CORS 설정 (Vercel 도메인으로 변경)
  - [ ] 프론트 API URL을 aicareerlab.duckdns.org로 변경

- **주의사항**:
  - EC2에서 Java 프로세스가 두 개 이상 뜨면 메모리 부족 → SSH 느려짐
  - 앱 실행 전 반드시 `pgrep -f app.jar` 로 기존 프로세스 확인 후 `kill [PID]`
  - 앱 하나만 실행: `java -jar ~/app.jar --spring.config.location=file:/home/ubuntu/application-prod.yml > ~/app.log 2>&1 &`

### EC2 접속 방법
```powershell
ssh -i "C:\Users\USER\Downloads\aicareerlab-key.pem" ubuntu@15.165.161.143
```

### 앱 실행 명령어 (EC2에서)
```bash
# 백그라운드 실행
java -jar ~/app.jar --spring.config.location=file:/home/ubuntu/application-prod.yml &

# 로그 확인
tail -f ~/app.log
```

---

## 남은 작업 ❌

### 1. 배포 완료 (위 배포 현황 참고)

### 2. 기타 개선 (선택)
- [ ] 이메일 HTML 템플릿 디자인 개선
- [ ] 추천 공고 조기 마감 감지 (deadline 지난 공고 숨기기)
- [ ] setup.html 자연어 조건 실제 활용 (현재 저장만 됨)
- [ ] GitHub Actions CI/CD (코드 push 시 자동 배포)

---

## 개발 환경 실행 방법

- **백엔드**: IntelliJ에서 `AiCareerLabApplication` 실행 (포트 8080)
- **프론트**: VS Code 하단 상태바 **"Go Live"** 버튼 클릭 → 포트 5500 자동 오픈
- **프론트 주소**: `http://localhost:5500/AI채용추천_프론트/index.html`
- **Swagger**: `http://localhost:8080/swagger-ui.html`
- **주의**: Live Server(Go Live)가 꺼져 있으면 localhost:5500 연결 거부 오류 발생

---

## 포트폴리오 / 이력서용 정리

### 문제 해결 경험

| 문제 | 원인 | 해결 |
|------|------|------|
| 중복 추천 발생 | URL 변형 공고가 동일 공고로 재추천됨 | ID + URL 이중 필터 + DB 유니크 제약 |
| 점수 세부 항목 전체 0 표시 | 기존 추천 데이터에 세부 점수 미저장 | ScoreDetail DTO 분리, 배치 Writer에서 항목별 저장 / 구 데이터 감지(isOldData) 분기 처리 |
| 대시보드 N+1 쿼리 | Lazy 로딩으로 추천 N건 → 공고 N번 SELECT | `JOIN FETCH` 쿼리로 1회 조회 |
| 관리자 유저 목록 N+1 | 유저마다 프로필/추천수 개별 쿼리 (2N+1) | 프로필 일괄 조회 + GROUP BY 추천수 1쿼리로 통합 |
| 배치 공고 중복 로딩 | 유저마다 `findAll()` 호출 (488건 × N번) | `@StepScope` 팩토리에서 1회 로딩 후 클로저 재사용 |
| 크롤러 URL 중복체크 N번 | 공고마다 `existsByUrl()` 개별 SELECT | IN 쿼리로 기존 URL 일괄 조회 후 필터링 |
| JS 렌더링 사이트 크롤링 불가 | Jsoup은 정적 HTML만 파싱 가능 | 서버사이드 렌더링 사이트(사람인)만 유지, 나머지 제외 |
| 배치 응답이 성공/실패 구분 안 됨 | 항상 200 반환으로 실제 결과 알 수 없음 | 배치 전후 추천 수 비교해 실제 결과 메시지 반환 |

### 핵심 구현 포인트 (면접용)
- Spring Batch 청크 기반 파이프라인 설계 (Reader → Processor → Writer)
- `@StepScope` 빈에서 공유 데이터 1회 로딩 패턴으로 배치 성능 개선
- 하드 필터(지역/경력/직무) + 소프트 스코어(스킬/태그/보너스) 복합 점수 알고리즘 설계
- DB 유니크 제약 + 애플리케이션 레벨 이중 중복 방지로 동시성 안전성 확보
- Google OAuth2 + 세션 기반 인증 + 관리자 권한 분리
- Jsoup 3단계 파싱 폴백 전략 (테이블 → 섹션 → 전체 텍스트 키워드 추출)
- AWS EC2 t3.micro에 Spring Boot + MySQL 단일 서버 배포 (비용 최소화)
- application-prod.yml 환경변수 분리로 로컬/운영 설정 분리

### 사용 기술 전체 목록 (이력서용)
**Backend**: Java 21, Spring Boot 3.2.3, Spring Batch, Spring Security, Spring Data JPA, Hibernate, MySQL, Jsoup, JavaMailSender, Springdoc OpenAPI, Gradle
**Frontend**: HTML5, CSS3, Vanilla JavaScript, Canvas API
**Infra**: AWS EC2 (t3.micro), Ubuntu 22.04, Nginx (예정), Let's Encrypt (예정), Vercel
**Auth**: Google OAuth2, BCrypt, HttpSession
**Tools**: IntelliJ, VS Code, Live Server, Swagger UI
