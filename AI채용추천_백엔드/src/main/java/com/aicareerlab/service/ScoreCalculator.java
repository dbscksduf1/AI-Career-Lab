package com.aicareerlab.service;

import com.aicareerlab.dto.ScoreDetail;
import com.aicareerlab.entity.JobPosting;
import com.aicareerlab.entity.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final ObjectMapper objectMapper;

    // 공고에서 스킬을 감지하기 위한 전체 기술 목록
    private static final List<String> ALL_SKILLS = Arrays.asList(
        "java", "spring", "springboot", "node", "nodejs", "python", "django", "fastapi",
        "react", "vue", "angular", "typescript", "javascript", "kotlin", "swift",
        "mysql", "postgresql", "oracle", "mongodb", "redis", "elasticsearch",
        "docker", "kubernetes", "aws", "gcp", "azure", "linux", "git",
        "kafka", "rabbitmq", "mqtt", "graphql", "rest", "restapi",
        "openai", "pytorch", "tensorflow", "hadoop", "spark",
        "flutter", "android", "ios", "react native",
        "jenkins", "github actions", "ci/cd", "terraform",
        "jpa", "hibernate", "mybatis", "nginx", "apache"
    );

    public int calculate(UserProfile profile, JobPosting posting) {
        ScoreDetail detail = calculateDetail(profile, posting);
        return detail != null ? detail.getTotalScore() : 0;
    }

    public ScoreDetail calculateDetail(UserProfile profile, JobPosting posting) {
        try {
            List<String> userStacks = objectMapper.readValue(profile.getStacks(), new TypeReference<>() {});
            List<String> userJobs   = objectMapper.readValue(profile.getJobs(),   new TypeReference<>() {});

            String titleLower    = posting.getTitle().toLowerCase();
            String locationLower = posting.getLocation() != null ? posting.getLocation().toLowerCase() : "";
            String careerLevel   = posting.getCareerLevel() != null ? posting.getCareerLevel() : "";
            String fullText      = (titleLower + " "
                    + (posting.getTags()           != null ? posting.getTags()           : "") + " "
                    + (posting.getQualifications() != null ? posting.getQualifications() : "") + " "
                    + (posting.getRequirements()   != null ? posting.getRequirements()   : "") + " "
                    + (posting.getPreferred()       != null ? posting.getPreferred()       : "")).toLowerCase();

            // ── 1. 지역 필터 (절대 조건) ────────────────────────────────────
            String region = profile.getRegion();
            if (region != null && !region.isEmpty() && !region.equals("any")) {
                if (!regionMatch(region, locationLower)) return null;
            }

            // ── 2. 경력 필터 (절대 조건) ────────────────────────────────────
            String career = profile.getCareer();
            if ("new".equals(career)) {
                if (!careerLevel.isEmpty() && !careerLevel.contains("신입")) return null;
                if (containsAny(titleLower, "시니어", "senior", "lead", "팀장", "수석", "책임")) return null;
            } else if ("1-3".equals(career)) {
                if (careerLevel.equals("신입")) return null;
                if (containsAny(careerLevel, "5년", "6년", "7년", "8년", "9년", "10년")) return null;
                if (containsAny(titleLower, "시니어", "수석", "팀장")) return null;
            } else if ("3-5".equals(career)) {
                if (careerLevel.equals("신입")) return null;
            }

            // ── 3. 직무 필터 (제목 기반 절대 조건) ─────────────────────────
            boolean jobMatch = userJobs.stream().anyMatch(job ->
                titleKeywords(job).stream().anyMatch(kw -> titleLower.contains(kw))
            );
            if (!jobMatch) return null;

            // ── 4. 스킬 매칭 점수 계산 ──────────────────────────────────────
            List<String> userStacksLower = userStacks.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

            // 공고가 요구하는 스킬 (전체 스킬 목록 기준)
            List<String> jobRequiredSkills = ALL_SKILLS.stream()
                    .filter(skill -> fullText.contains(skill))
                    .collect(Collectors.toList());

            // 태그에서도 스킬 추출 (사람인 tags 필드)
            List<String> tagSkills = Arrays.stream(
                    posting.getTags() != null ? posting.getTags().replaceAll("[\\[\\]\"]", "").split(",") : new String[0])
                    .map(String::trim).map(String::toLowerCase)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());

            // 유저 스킬과 태그 매칭 수
            long tagMatched = tagSkills.stream()
                    .filter(tag -> userStacksLower.stream().anyMatch(us -> us.contains(tag) || tag.contains(us)))
                    .count();

            // 매칭된 스킬 수
            long skillMatched = jobRequiredSkills.stream()
                    .filter(skill -> userStacksLower.stream().anyMatch(us -> us.contains(skill) || skill.contains(us)))
                    .count();

            // 매칭된 스킬 이름 목록
            String matchedSkillsStr = jobRequiredSkills.stream()
                    .filter(skill -> userStacksLower.stream().anyMatch(us -> us.contains(skill) || skill.contains(us)))
                    .collect(Collectors.joining(", "));

            // ── 5. 점수 계산 (78 ~ 98) ──────────────────────────────────────
            int baseScore = 78;

            // 스킬 매칭 점수 (최대 +12)
            int skillScore = 0;
            if (!jobRequiredSkills.isEmpty()) {
                double ratio = (double) skillMatched / jobRequiredSkills.size();
                skillScore = (int) Math.round(ratio * 12);
            }

            // 태그 매칭 점수 (최대 +5)
            int tagScore = 0;
            if (!tagSkills.isEmpty()) {
                double tagRatio = (double) tagMatched / tagSkills.size();
                tagScore = (int) Math.round(tagRatio * 5);
            }

            // 유저 스킬 직접 매칭 보너스 (최대 +3)
            long directMatch = userStacksLower.stream()
                    .filter(fullText::contains).count();
            int bonusScore = Math.min((int) directMatch, 3);

            // 공고 ID 기반 미세 변동 (+0~2)
            int idVariation = (int)(posting.getId() % 3);

            int total = Math.min(Math.max(baseScore + skillScore + tagScore + bonusScore + idVariation, 78), 98);

            return new ScoreDetail(total, baseScore, skillScore, tagScore, bonusScore, matchedSkillsStr);

        } catch (Exception e) {
            log.warn("점수 계산 오류: {}", e.getMessage());
            return null;
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private boolean regionMatch(String region, String location) {
        return switch (region) {
            case "seoul"    -> location.contains("서울");
            case "gangnam"  -> location.contains("강남") || location.contains("서초");
            case "gyeonggi" -> location.contains("경기") || location.contains("판교") || location.contains("분당");
            case "remote"   -> location.contains("재택") || location.contains("원격");
            case "busan"    -> location.contains("부산");
            default         -> true;
        };
    }

    private String jobKeyword(String jobVal) {
        // 복수 키워드는 anyMatch로 처리하므로 가장 대표 키워드 반환
        return switch (jobVal) {
            case "backend"   -> "백엔드";
            case "frontend"  -> "프론트엔드";
            case "fullstack" -> "풀스택";
            case "devops"    -> "devops";
            case "data"      -> "데이터";
            case "ml"        -> "머신러닝";
            case "mobile"    -> "모바일";
            case "security"  -> "보안";
            case "pm"        -> "pm";
            case "planning"  -> "기획";
            case "uxui"      -> "디자인";
            case "hr"        -> "인사";
            case "finance"   -> "재무";
            default          -> jobVal.toLowerCase();
        };
    }

    private List<String> jobKeywords(String jobVal) {
        return titleKeywords(jobVal);
    }

    private List<String> titleKeywords(String jobVal) {
        return switch (jobVal) {
            // 개발
            case "backend"        -> List.of("백엔드", "backend", "풀스택", "fullstack", "개발자", "java", "spring", "서버개발", "서버 개발", "node", "python", "django");
            case "frontend"       -> List.of("프론트엔드", "frontend", "front-end", "풀스택", "개발자", "react", "vue", "angular", "퍼블리셔");
            case "fullstack"      -> List.of("풀스택", "fullstack", "full-stack", "백엔드", "프론트엔드", "개발자");
            case "devops"         -> List.of("devops", "데브옵스", "인프라", "sre", "클라우드", "cloud", "kubernetes", "쿠버네티스");
            case "data"           -> List.of("데이터엔지니어", "data engineer", "빅데이터", "데이터 플랫폼");
            case "ml"             -> List.of("머신러닝", "machine learning", "딥러닝", "ai 개발", "mlops", "인공지능");
            case "mobile"         -> List.of("모바일", "ios", "android", "앱 개발", "앱개발", "flutter", "swift", "kotlin");
            case "security"       -> List.of("보안", "security", "정보보안", "취약점");
            case "embedded"       -> List.of("임베디드", "embedded", "펌웨어", "firmware", "rtos");
            case "game"           -> List.of("게임", "game", "unity", "unreal");
            case "blockchain"     -> List.of("블록체인", "blockchain", "web3", "nft", "defi");
            // 기획/전략
            case "pm"             -> List.of("pm ", "po ", "프로덕트 매니저", "product manager", "product owner");
            case "planning"       -> List.of("서비스 기획", "기획자", "콘텐츠 기획");
            case "bizdev"         -> List.of("사업개발", "bd ", "business development", "신사업");
            case "data_analyst"   -> List.of("데이터 분석", "data analyst", "데이터분석");
            case "strategy"       -> List.of("전략 기획", "경영 기획", "전략기획", "경영기획");
            case "ir"             -> List.of("ir ", "투자유치", "investor relations");
            case "consulting"     -> List.of("컨설팅", "consulting", "컨설턴트");
            // 디자인
            case "uxui"           -> List.of("ux", "ui ", "ux/ui", "ui/ux", "프로덕트 디자인", "퍼블리셔");
            case "graphic"        -> List.of("그래픽", "graphic", "브랜드 디자인", "시각 디자인");
            case "motion"         -> List.of("영상", "모션", "motion", "영상 편집");
            case "product_design" -> List.of("제품 디자인", "product design", "산업 디자인");
            case "fashion_design" -> List.of("패션 디자인", "fashion design");
            case "space_design"   -> List.of("공간 디자인", "인테리어 디자인", "interior design");
            case "illustration"   -> List.of("일러스트", "illustration", "웹툰");
            // 마케팅
            case "digital_mkt"    -> List.of("디지털 마케팅", "digital marketing", "퍼포먼스");
            case "content"        -> List.of("콘텐츠 마케팅", "content marketing", "콘텐츠 기획");
            case "performance"    -> List.of("퍼포먼스 마케팅", "performance marketing", "그로스");
            case "brand_mkt"      -> List.of("브랜드 마케팅", "brand marketing", "브랜드 매니저");
            case "sns"            -> List.of("sns", "소셜미디어", "커뮤니티 매니저");
            case "pr"             -> List.of("pr ", "홍보", "public relations");
            case "crm_mkt"        -> List.of("crm", "그로스", "growth", "리텐션");
            case "global_mkt"     -> List.of("글로벌 마케팅", "해외 마케팅", "global marketing");
            // 경영지원
            case "hr"             -> List.of("인사", "hr ", "채용", "hrd", "인사담당");
            case "hrd"            -> List.of("교육", "hrd", "인재개발", "교육담당");
            case "finance"        -> List.of("재무", "회계", "finance", "경리", "재경");
            case "tax"            -> List.of("세무", "회계사", "tax", "세무사");
            case "biz_op"         -> List.of("경영전략", "경영기획", "전략기획");
            case "operation"      -> List.of("운영", "cs ", "고객서비스", "고객지원", "운영담당");
            case "legal"          -> List.of("법무", "legal", "계약", "변호사");
            case "purchase"       -> List.of("구매", "자재", "purchasing", "조달");
            case "general"        -> List.of("총무", "시설", "general affairs");
            // 영업
            case "sales"          -> List.of("영업", "sales", "영업담당");
            case "b2b"            -> List.of("b2b", "기업영업", "법인영업");
            case "b2c"            -> List.of("b2c", "소비자영업", "리테일 영업");
            case "account"        -> List.of("영업관리", "kam", "account manager");
            case "partner"        -> List.of("파트너십", "제휴", "partnership");
            case "export"         -> List.of("해외영업", "export", "글로벌 영업");
            case "tech_sales"     -> List.of("기술영업", "technical sales", "솔루션 영업");
            case "retail"         -> List.of("유통", "채널영업", "retail");
            // 의료/제약
            case "cra"            -> List.of("임상", "cra", "clinical");
            case "rd_pharma"      -> List.of("연구개발", "r&d", "제약 연구");
            case "qa"             -> List.of("qa", "qc", "품질");
            case "ra"             -> List.of("인허가", "ra ", "regulatory");
            case "msl"            -> List.of("msl", "학술영업", "medical science");
            case "med_info"       -> List.of("의학정보", "mi ", "medical information");
            case "gmp"            -> List.of("gmp", "생산", "제조");
            case "bioinfo"        -> List.of("바이오인포", "bioinformatics");
            case "pv"             -> List.of("약물감시", "pv ", "pharmacovigilance", "안전성");
            // 의료직
            case "nurse"          -> List.of("간호사", "nurse", "간호");
            case "pt"             -> List.of("물리치료", "physical therapy");
            case "ot"             -> List.of("작업치료", "occupational therapy");
            case "radiologist"    -> List.of("방사선", "radiologist");
            case "lab"            -> List.of("임상병리", "laboratory");
            case "dental"         -> List.of("치위생", "dental");
            case "health_admin"   -> List.of("의무기록", "원무");
            case "emr"            -> List.of("의료 it", "emr", "의료정보");
            // 금융
            case "banking"        -> List.of("은행", "여신", "banking");
            case "investment"     -> List.of("투자", "자산운용", "investment");
            case "insurance"      -> List.of("보험", "insurance", "언더라이팅");
            case "fin_plan"       -> List.of("재무설계", "fp ", "financial planner");
            case "fintech"        -> List.of("핀테크", "fintech");
            case "risk"           -> List.of("리스크", "risk management");
            case "audit"          -> List.of("감사", "내부통제", "audit");
            // 교육
            case "teacher"        -> List.of("교사", "강사", "teacher");
            case "tutor"          -> List.of("과외", "튜터", "tutor");
            case "edu_plan"       -> List.of("교육 기획", "교육콘텐츠");
            case "edu_admin"      -> List.of("교육행정", "교육 행정");
            case "coach"          -> List.of("코치", "컨설턴트", "coach");
            case "edtech"         -> List.of("에듀테크", "edtech");
            // 물류
            case "scm"            -> List.of("scm", "공급망", "supply chain");
            case "logistics"      -> List.of("물류", "logistics", "물류운영");
            case "import"         -> List.of("수출입", "무역", "import", "export");
            case "warehouse"      -> List.of("창고", "재고", "warehouse");
            case "distribution"   -> List.of("유통", "distribution");
            case "customs"        -> List.of("관세", "통관", "customs");
            // 미디어
            case "pd"             -> List.of("pd ", "방송", "연출", "프로듀서");
            case "writer"         -> List.of("작가", "카피라이터", "writer", "copywriter");
            case "journalist"     -> List.of("기자", "에디터", "journalist", "editor");
            case "photographer"   -> List.of("사진", "영상촬영", "photographer");
            case "youtube"        -> List.of("유튜브", "크리에이터", "youtube");
            case "game_plan"      -> List.of("게임 기획", "게임기획");
            // 건설
            case "architect"      -> List.of("건축", "architect", "건축설계");
            case "civil"          -> List.of("토목", "플랜트", "civil");
            case "interior"       -> List.of("인테리어", "interior", "시공");
            case "pm_const"       -> List.of("현장관리", "현장 관리", "공사관리");
            case "mep"            -> List.of("기계설비", "전기설비", "mep");
            case "real_estate"    -> List.of("부동산", "real estate", "분양");
            // 뷰티/패션
            case "fashion_md"     -> List.of("패션 md", "바이어", "buyer", "md ");
            case "beauty_rd"      -> List.of("뷰티 연구", "화장품 연구", "원료개발");
            case "styling"        -> List.of("스타일리스트", "stylist");
            case "makeup"         -> List.of("메이크업", "makeup");
            case "beauty_mkt"     -> List.of("뷰티 마케팅", "beauty marketing");
            case "fashion_mkt"    -> List.of("패션 마케팅", "fashion marketing");
            // 공공/기타
            case "public_admin"   -> List.of("행정", "공무원", "공공기관");
            case "welfare"        -> List.of("사회복지", "welfare", "복지사");
            case "ngo"            -> List.of("ngo", "비영리", "사회공헌");
            case "research"       -> List.of("연구원", "연구소", "researcher");
            case "env"            -> List.of("환경", "안전관리", "ehs");
            default               -> List.of(jobVal.toLowerCase());
        };
    }
}
