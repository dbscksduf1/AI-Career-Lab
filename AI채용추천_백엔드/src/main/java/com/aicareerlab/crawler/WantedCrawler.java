package com.aicareerlab.crawler;

import com.aicareerlab.entity.JobPosting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WantedCrawler implements BaseCrawler {

    private static final String API_URL = "https://www.wanted.co.kr/api/v4/jobs?limit=20&offset=";
    private final ObjectMapper objectMapper;

    @Override
    public List<JobPosting> crawl(String keyword) {
        List<JobPosting> results = new ArrayList<>();

        // 원티드는 API로 수집 (keyword 기반 검색)
        String searchUrl = "https://www.wanted.co.kr/api/v4/jobs?"
                + "query=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                + "&limit=20&offset=0";

        try {
            String response = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .header("Referer", "https://www.wanted.co.kr")
                    .timeout(10_000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            JsonNode root = objectMapper.readTree(response);
            JsonNode jobs = root.path("data");

            for (JsonNode job : jobs) {
                try {
                    String title   = job.path("position").asText();
                    String company = job.path("company").path("name").asText();
                    String location = job.path("address").path("location").asText();
                    long   id      = job.path("id").asLong();
                    String jobUrl  = "https://www.wanted.co.kr/wd/" + id;

                    if (title.isEmpty()) continue;

                    results.add(JobPosting.builder()
                            .title(title)
                            .company(company)
                            .location(location)
                            .platform("wanted")
                            .url(jobUrl)
                            .tags("[]")
                            .deadline(LocalDate.now().plusDays(30))
                            .build());

                } catch (Exception e) {
                    log.warn("원티드 공고 파싱 실패: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("원티드 크롤링 오류 (keyword={}): {}", keyword, e.getMessage());
        }

        log.info("원티드 크롤링 완료: keyword={}, 수집={}건", keyword, results.size());
        return results;
    }
}
