package com.aicareerlab.controller;

import com.aicareerlab.batch.BatchScheduler;
import com.aicareerlab.crawler.CrawlerService;
import com.aicareerlab.dto.RecommendationGroupResponse;
import com.aicareerlab.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendation", description = "추천 API")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final BatchScheduler batchScheduler;
    private final CrawlerService crawlerService;
    private final com.aicareerlab.repository.RecommendationRepository recommendationRepository;
    private final com.aicareerlab.repository.JobPostingRepository jobPostingRepository;

    @GetMapping
    @Operation(summary = "날짜별 추천 공고 목록 조회")
    public ResponseEntity<List<RecommendationGroupResponse>> getRecommendations(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(recommendationService.getGroupedByDate(userId));
    }

    @PostMapping("/crawl")
    @Operation(summary = "크롤링 수동 실행 (테스트용)")
    public ResponseEntity<String> crawlNow() {
        crawlerService.crawlAll();
        return ResponseEntity.ok("크롤링 완료");
    }

    @PostMapping("/send-now")
    @Operation(summary = "지금받기 — 즉시 추천 배치 실행")
    public ResponseEntity<String> sendNow(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        long before = recommendationRepository.countByUserId(userId);
        batchScheduler.runBatch();
        long after = recommendationRepository.countByUserId(userId);
        long newCount = after - before;

        if (newCount == 0) {
            long totalJobPostings = jobPostingRepository.count();
            if (totalJobPostings == 0) {
                return ResponseEntity.ok("공고 데이터 없음: 크롤링을 먼저 실행해주세요.");
            }
            return ResponseEntity.ok("새 추천 없음: 조건에 맞는 신규 공고가 없거나 프로필을 확인해주세요.");
        }
        return ResponseEntity.ok(newCount + "건의 새 추천 공고를 이메일로 발송했습니다.");
    }
}
