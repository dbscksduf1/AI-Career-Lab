package com.aicareerlab.controller;

import com.aicareerlab.crawler.CrawlerService;
import com.aicareerlab.entity.User;
import com.aicareerlab.repository.JobPostingRepository;
import com.aicareerlab.repository.RecommendationRepository;
import com.aicareerlab.repository.UserProfileRepository;
import com.aicareerlab.repository.UserRepository;
import com.aicareerlab.batch.BatchScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "관리자 API")
public class AdminController {

    private static final String ADMIN_EMAIL = "soo5586068@gmail.com";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecommendationRepository recommendationRepository;
    private final CrawlerService crawlerService;
    private final BatchScheduler batchScheduler;

    private boolean isAdmin(HttpSession session) {
        String email = (String) session.getAttribute("userEmail");
        return ADMIN_EMAIL.equals(email);
    }

    // ── 전체 통계 ──────────────────────────────────────────
    @GetMapping("/stats")
    @Operation(summary = "대시보드 통계")
    public ResponseEntity<Map<String, Object>> getStats(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        long totalUsers       = userRepository.count();
        long totalPostings    = jobPostingRepository.count();
        long totalRecommendations = recommendationRepository.count();
        long profileComplete  = userProfileRepository.count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalPostings", totalPostings);
        stats.put("totalRecommendations", totalRecommendations);
        stats.put("profileComplete", profileComplete);

        return ResponseEntity.ok(stats);
    }

    // ── 유저 목록 ───────────────────────────────────────────
    @GetMapping("/users")
    @Operation(summary = "유저 목록 + 프로필 요약")
    public ResponseEntity<List<Map<String, Object>>> getUsers(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        List<User> users = userRepository.findAll();

        // 프로필 일괄 조회 (N+1 방지)
        Map<Long, com.aicareerlab.entity.UserProfile> profileMap = userProfileRepository.findAllWithUser().stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        // 추천 수 일괄 조회 (N+1 방지)
        Map<Long, Long> recCountMap = recommendationRepository.countGroupByUserId().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("email", user.getEmail());
            row.put("name", user.getName());
            row.put("provider", user.getProvider());
            row.put("createdAt", user.getCreatedAt());

            com.aicareerlab.entity.UserProfile profile = profileMap.get(user.getId());
            if (profile != null) {
                row.put("jobs", profile.getJobs());
                row.put("region", profile.getRegion());
                row.put("career", profile.getCareer());
                row.put("stacks", profile.getStacks());
                row.put("profileEmail", profile.getEmail());
                row.put("hasProfile", true);
            } else {
                row.put("hasProfile", false);
            }

            row.put("recommendationCount", recCountMap.getOrDefault(user.getId(), 0L));
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── 유저 삭제 ───────────────────────────────────────────
    @DeleteMapping("/users/{userId}")
    @Operation(summary = "유저 삭제")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        userRepository.findById(userId).ifPresent(user -> {
            log.info("관리자가 유저 삭제: {}", user.getEmail());
            userRepository.delete(user);
        });

        return ResponseEntity.ok("삭제 완료");
    }

    // ── 크롤링 수동 실행 ────────────────────────────────────
    @PostMapping("/crawl")
    @Operation(summary = "크롤링 즉시 실행")
    public ResponseEntity<String> crawlNow(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        log.info("관리자 크롤링 수동 실행");
        crawlerService.crawlAll();
        long total = jobPostingRepository.count();
        return ResponseEntity.ok("크롤링 완료. 총 공고: " + total + "건");
    }

    // ── 배치 수동 실행 (전체 유저) ──────────────────────────
    @PostMapping("/batch")
    @Operation(summary = "전체 유저 추천 배치 즉시 실행")
    public ResponseEntity<String> runBatch(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        log.info("관리자 배치 수동 실행");
        batchScheduler.runBatch();
        return ResponseEntity.ok("배치 실행 완료");
    }

    // ── 공고 수 현황 ────────────────────────────────────────
    @GetMapping("/postings/count")
    @Operation(summary = "플랫폼별 공고 수")
    public ResponseEntity<Map<String, Object>> getPostingsCount(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        long total   = jobPostingRepository.count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        return ResponseEntity.ok(result);
    }
}
