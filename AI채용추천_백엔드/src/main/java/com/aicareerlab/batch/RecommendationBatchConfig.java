package com.aicareerlab.batch;

import com.aicareerlab.dto.ScoreDetail;
import com.aicareerlab.entity.*;
import com.aicareerlab.repository.*;
import com.aicareerlab.service.EmailService;
import com.aicareerlab.service.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RecommendationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecommendationRepository recommendationRepository;
    private final ScoreCalculator scoreCalculator;
    private final EmailService emailService;

    // ─── Job ───────────────────────────────────────────────
    @Bean
    public Job recommendationJob() {
        return new JobBuilder("recommendationJob", jobRepository)
                .start(recommendationStep())
                .build();
    }

    // ─── Step ──────────────────────────────────────────────
    @Bean
    public Step recommendationStep() {
        return new StepBuilder("recommendationStep", jobRepository)
                .<User, List<Recommendation>>chunk(10, transactionManager)
                .reader(userItemReader())
                .processor(userItemProcessor())
                .writer(recommendationItemWriter())
                .build();
    }

    // ─── Reader: 전체 유저 목록 ─────────────────────────────
    @Bean
    @StepScope
    public ListItemReader<User> userItemReader() {
        List<User> users = userRepository.findAll();
        log.info("배치 대상 유저: {}명", users.size());
        return new ListItemReader<>(users);
    }

    // ─── Processor: 유저별 공고 필터링 + 점수 계산 → TOP 10 ──
    @Bean
    @StepScope
    public ItemProcessor<User, List<Recommendation>> userItemProcessor() {
        // 배치 시작 시 공고 한 번만 로딩 (유저마다 재조회 방지)
        List<JobPosting> allPostings = jobPostingRepository.findAll();
        log.info("배치 공고 캐시: {}건", allPostings.size());

        return user -> {
            UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
            if (profile == null || profile.getJobs() == null) {
                log.info("프로필 없음, 스킵: userId={}", user.getId());
                return null;
            }

            if (allPostings.isEmpty()) {
                log.info("수집된 공고 없음, 스킵: userId={}", user.getId());
                return null;
            }

            // 이미 추천된 공고 ID + URL 모두 조회 (중복 방지 이중 체크)
            Set<Long>   alreadyRecommendedIds  = recommendationRepository.findRecommendedJobIdsByUserId(user.getId());
            Set<String> alreadyRecommendedUrls = recommendationRepository.findRecommendedJobUrlsByUserId(user.getId());

            log.info("유저 {} 기추천 공고: {}건", user.getEmail(), alreadyRecommendedIds.size());

            // 전체 공고 점수 계산
            List<Recommendation> scored = allPostings.stream()
                    .map(posting -> {
                        ScoreDetail detail = scoreCalculator.calculateDetail(profile, posting);
                        if (detail == null) return null;
                        return Recommendation.builder()
                                .user(user)
                                .jobPosting(posting)
                                .score(detail.getTotalScore())
                                .baseScore(detail.getBaseScore())
                                .skillScore(detail.getSkillScore())
                                .tagScore(detail.getTagScore())
                                .bonusScore(detail.getBonusScore())
                                .matchedSkills(detail.getMatchedSkills())
                                .sentAt(LocalDateTime.now())
                                .build();
                    })
                    .filter(r -> r != null && r.getScore() > 0)
                    .sorted(Comparator.comparingInt(Recommendation::getScore).reversed())
                    .collect(Collectors.toList());

            // 이미 추천된 공고 완전 제외 (ID와 URL 둘 다 체크)
            List<Recommendation> top10 = scored.stream()
                    .filter(r -> {
                        Long jobId  = r.getJobPosting().getId();
                        String jobUrl = r.getJobPosting().getUrl();
                        return !alreadyRecommendedIds.contains(jobId)
                            && (jobUrl == null || !alreadyRecommendedUrls.contains(jobUrl));
                    })
                    .limit(10)
                    .collect(Collectors.toList());

            log.info("유저 {} 추천 완료: TOP10 평균점수={}",
                    user.getEmail(),
                    top10.stream().mapToInt(Recommendation::getScore).average().orElse(0));

            return top10;
        };
    }

    // ─── Writer: DB 저장 + 이메일 발송 ─────────────────────
    @Bean
    @StepScope
    public ItemWriter<List<Recommendation>> recommendationItemWriter() {
        return chunk -> {
            for (List<Recommendation> recommendations : chunk.getItems()) {
                if (recommendations == null || recommendations.isEmpty()) continue;

                // 개별 저장 — 혹시 중복이 있어도 나머지는 정상 저장
                List<Recommendation> saved = new ArrayList<>();
                for (Recommendation rec : recommendations) {
                    try {
                        recommendationRepository.save(rec);
                        saved.add(rec);
                    } catch (Exception e) {
                        log.warn("중복 추천 스킵 (userId={}, jobId={}): {}",
                                rec.getUser().getId(), rec.getJobPosting().getId(), e.getMessage());
                    }
                }
                if (saved.isEmpty()) continue;

                User user = saved.get(0).getUser();
                UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
                String toEmail = (profile != null && profile.getEmail() != null)
                        ? profile.getEmail() : user.getEmail();

                emailService.sendRecommendations(toEmail, user.getName(), saved);
            }
        };
    }
}
