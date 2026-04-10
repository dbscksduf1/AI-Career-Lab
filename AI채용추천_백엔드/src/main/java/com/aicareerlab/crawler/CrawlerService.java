package com.aicareerlab.crawler;

import com.aicareerlab.entity.JobPosting;
import com.aicareerlab.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final SaraminCrawler saraminCrawler;
    private final JobPostingRepository jobPostingRepository;

    // 수집할 키워드 목록
    private static final List<String> KEYWORDS = List.of(
            "백엔드", "프론트엔드", "풀스택", "데이터엔지니어", "DevOps",
            "기획", "디자이너", "마케팅", "영업", "인사", "재무"
    );

    // 매일 새벽 2시 자동 수집
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledCrawl() {
        log.info("=== 채용공고 자동 수집 시작 ===");
        crawlAll();
        log.info("=== 채용공고 자동 수집 완료 ===");
    }

    public void crawlAll() {
        for (String keyword : KEYWORDS) {
            crawlAndSave(saraminCrawler, keyword);
        }
    }

    private void crawlAndSave(BaseCrawler crawler, String keyword) {
        List<JobPosting> postings = crawler.crawl(keyword);
        if (postings.isEmpty()) return;

        // 기존 URL 일괄 조회 (N번 SELECT → 1번 IN 쿼리)
        Set<String> urls = postings.stream()
                .map(JobPosting::getUrl)
                .collect(Collectors.toSet());
        Set<String> existing = jobPostingRepository.findExistingUrls(urls);

        List<JobPosting> newPostings = postings.stream()
                .filter(p -> !existing.contains(p.getUrl()))
                .collect(Collectors.toList());

        if (!newPostings.isEmpty()) {
            jobPostingRepository.saveAll(newPostings);
        }

        log.info("저장 완료: keyword={}, 신규={}건 / 전체={}건", keyword, newPostings.size(), postings.size());
    }
}
