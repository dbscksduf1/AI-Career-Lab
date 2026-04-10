package com.aicareerlab.crawler;

import com.aicareerlab.entity.JobPosting;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JobkoreaCrawler implements BaseCrawler {

    private static final String BASE_URL = "https://www.jobkorea.co.kr/Search/";
    private static final int MAX_PAGES = 1000; // 공고 없으면 자동 break

    @Override
    public List<JobPosting> crawl(String keyword) {
        List<JobPosting> results = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                String url = BASE_URL + "?stext="
                        + java.net.URLEncoder.encode(keyword, "UTF-8")
                        + "&tabType=recruit&Page_No=" + page;

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .header("Referer", "https://www.jobkorea.co.kr/")
                        .timeout(12_000)
                        .get();

                // 잡코리아 공고 목록 셀렉터
                Elements items = doc.select(".list-default .list-item");
                if (items.isEmpty()) {
                    items = doc.select(".recruit-list li.item");
                }
                if (items.isEmpty()) {
                    log.warn("잡코리아 파싱 실패 (keyword={}, page={}): 공고 목록 없음 — JS 렌더링 페이지일 수 있음", keyword, page);
                    break;
                }

                for (Element item : items) {
                    try {
                        // 제목 + URL
                        Element titleEl = item.selectFirst(".title a, .job-title a, h2 a, .recruit-title a");
                        if (titleEl == null) continue;

                        String title  = titleEl.text().trim();
                        String href   = titleEl.attr("href");
                        if (title.isEmpty() || href.isEmpty()) continue;

                        String jobUrl = href.startsWith("http") ? href : "https://www.jobkorea.co.kr" + href;

                        // 회사명
                        Element corpEl = item.selectFirst(".company a, .corp-name a, .company-name a");
                        String company = corpEl != null ? corpEl.text().trim() : "";

                        // 지역
                        Element locEl = item.selectFirst(".location, .work-place, .loc");
                        String location = locEl != null ? locEl.text().trim() : "";

                        // 경력
                        Element careerEl = item.selectFirst(".career, .exp");
                        String careerLevel = careerEl != null ? careerEl.text().trim() : "신입·경력";

                        // 학력
                        Element eduEl = item.selectFirst(".edu, .education");
                        String education = eduEl != null ? eduEl.text().trim() : "학력무관";

                        // 태그/기술스택
                        Elements tagEls = item.select(".tag-list span, .skill-list span, .tags span");
                        String tagsStr = tagEls.stream()
                                .map(Element::text)
                                .filter(t -> !t.isBlank())
                                .reduce((a, b) -> a + "," + b).orElse("");

                        // 마감일
                        Element deadlineEl = item.selectFirst(".date, .deadline, .period");
                        String deadlineText = deadlineEl != null ? deadlineEl.text().trim() : "";
                        LocalDate deadline = parseDeadline(deadlineText);

                        results.add(JobPosting.builder()
                                .title(title)
                                .company(company)
                                .location(location)
                                .platform("jobkorea")
                                .url(jobUrl)
                                .tags(tagsStr.isEmpty() ? "[]" : "[\"" + tagsStr.replace(",", "\",\"") + "\"]")
                                .qualifications("공고 페이지 참고")
                                .requirements("공고 페이지 참고")
                                .preferred("해당 없음")
                                .education(education.isEmpty() ? "학력무관" : education)
                                .salary("회사내규에 따름")
                                .careerLevel(careerLevel.isEmpty() ? "신입·경력" : careerLevel)
                                .deadline(deadline)
                                .build());

                        Thread.sleep(500);

                    } catch (Exception e) {
                        log.warn("잡코리아 공고 파싱 실패: {}", e.getMessage());
                    }
                }

                log.info("잡코리아 {}페이지 수집: keyword={}, 공고={}건", page, keyword, items.size());
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("잡코리아 크롤링 오류 (keyword={}, page={}): {}", keyword, page, e.getMessage());
                break;
            }
        }

        log.info("잡코리아 크롤링 완료: keyword={}, 수집={}건", keyword, results.size());
        return results;
    }

    private LocalDate parseDeadline(String text) {
        try {
            // "~12/31", "2025.12.31", "12.31" 등 파싱
            text = text.replaceAll("[~\\s]", "");
            if (text.contains(".")) {
                String[] parts = text.split("\\.");
                if (parts.length == 3) {
                    return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } else if (parts.length == 2) {
                    return LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
        } catch (Exception ignored) {}
        return LocalDate.now().plusDays(30);
    }
}
