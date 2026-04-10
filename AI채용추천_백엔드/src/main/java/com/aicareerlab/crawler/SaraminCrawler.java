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
public class SaraminCrawler implements BaseCrawler {

    private static final String BASE_URL = "https://www.saramin.co.kr/zf_user/search/recruit";
    private static final int MAX_PAGES = 1000; // 공고 없으면 자동 break

    @Override
    public List<JobPosting> crawl(String keyword) {
        List<JobPosting> results = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                String url = BASE_URL + "?searchType=search&searchword="
                        + java.net.URLEncoder.encode(keyword, "UTF-8")
                        + "&recruitPage=" + page;

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10_000)
                        .get();

                Elements items = doc.select(".item_recruit");
                if (items.isEmpty()) break;

                for (Element item : items) {
                    try {
                        String title   = item.select(".job_tit a").text().trim();
                        String company = item.select(".corp_name a").first() != null
                                ? item.select(".corp_name a").first().text().trim() : "";
                        String href    = item.select(".job_tit a").attr("href");
                        String jobUrl  = "https://www.saramin.co.kr" + href;
                        String tagsStr = item.select(".job_sector span").stream()
                                .map(Element::text)
                                .reduce((a, b) -> a + "," + b).orElse("");

                        Elements conditions = item.select(".job_condition span");
                        String location    = conditions.size() > 0 ? conditions.get(0).text().trim() : "";
                        String education   = "학력무관";
                        String salary      = "회사내규에 따름";
                        String careerLevel = "신입·경력"; // 기본값

                        for (Element cond : conditions) {
                            String text = cond.text().trim();
                            if (text.contains("대졸") || text.contains("고졸") || text.contains("학력무관")
                                    || text.contains("초대졸") || text.contains("석사") || text.contains("박사")) {
                                education = text;
                            }
                            if (text.contains("만원") || text.contains("연봉") || text.contains("협의")) {
                                salary = text;
                            }
                            // 경력조건: "신입", "경력", "신입·경력", "경력3년↑" 등
                            if (text.equals("신입") || text.equals("경력") || text.contains("신입·경력")
                                    || text.contains("경력") && (text.contains("년") || text.contains("↑"))) {
                                careerLevel = text;
                            }
                        }

                        if (title.isEmpty() || jobUrl.isEmpty()) continue;

                        String[] details = scrapeDetail(jobUrl);

                        results.add(JobPosting.builder()
                                .title(title)
                                .company(company)
                                .location(location)
                                .platform("saramin")
                                .url(jobUrl)
                                .tags(tagsStr.isEmpty() ? "[]" : "[\"" + tagsStr.replace(",", "\",\"") + "\"]")
                                .qualifications(details[0])
                                .requirements(details[1])
                                .preferred(details[2])
                                .education(education)
                                .salary(salary)
                                .careerLevel(careerLevel)
                                .deadline(LocalDate.now().plusDays(30))
                                .build());

                        Thread.sleep(800);

                    } catch (Exception e) {
                        log.warn("사람인 공고 파싱 실패: {}", e.getMessage());
                    }
                }

                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("사람인 크롤링 오류 (keyword={}, page={}): {}", keyword, page, e.getMessage());
            }
        }

        log.info("사람인 크롤링 완료: keyword={}, 수집={}건", keyword, results.size());
        return results;
    }

    private String[] scrapeDetail(String jobUrl) {
        String qualifications = "";
        String requirements   = "";
        String preferred      = "";

        try {
            Document doc = Jsoup.connect(jobUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10_000)
                    .get();

            // 사람인 상세페이지 구조: 섹션 제목 + 내용 파싱
            // 방법 1: .tb_recruit_info 테이블
            Elements rows = doc.select(".tb_recruit_info tr");
            for (Element row : rows) {
                String label   = row.select("th").text().trim();
                String content = row.select("td").text().trim();
                if (label.contains("자격") || label.contains("지원조건")) {
                    qualifications = truncate(content, 200);
                } else if (label.contains("우대")) {
                    preferred = truncate(content, 200);
                } else if (label.contains("업무") || label.contains("담당")) {
                    requirements = truncate(content, 200);
                }
            }

            // 방법 2: .wrap_jv_cont 내 섹션 (상세 JD 영역)
            if (qualifications.isEmpty() && requirements.isEmpty()) {
                Elements sections = doc.select(".wrap_jv_cont .cont_wrap");
                for (Element section : sections) {
                    String heading = section.select(".tit_job_section, h3, .tit").text().trim();
                    String content = section.select(".content, .desc, p").text().trim();
                    if (content.isEmpty()) content = section.ownText().trim();

                    if (heading.contains("자격") || heading.contains("지원조건")) {
                        qualifications = truncate(content, 200);
                    } else if (heading.contains("우대")) {
                        preferred = truncate(content, 200);
                    } else if (heading.contains("업무") || heading.contains("담당") || heading.contains("주요")) {
                        requirements = truncate(content, 200);
                    }
                }
            }

            // 방법 3: 전체 텍스트에서 키워드 기반 추출
            if (qualifications.isEmpty() && requirements.isEmpty()) {
                String fullText = doc.select(".wrap_jv_cont, .jv_cont, .job_cont").text();
                if (!fullText.isEmpty()) {
                    // 담당업무 / 자격요건 / 우대사항 패턴 검색
                    requirements   = extractSection(fullText, new String[]{"담당업무", "주요업무", "담당 업무"}, 200);
                    qualifications = extractSection(fullText, new String[]{"자격요건", "지원자격", "자격 요건"}, 200);
                    preferred      = extractSection(fullText, new String[]{"우대사항", "우대 사항"}, 200);
                }
            }

        } catch (Exception e) {
            log.debug("상세페이지 크롤링 실패 ({}): {}", jobUrl, e.getMessage());
        }

        return new String[]{
            qualifications.isEmpty() ? "공고 페이지 참고" : qualifications,
            requirements.isEmpty()   ? "공고 페이지 참고" : requirements,
            preferred.isEmpty()      ? "해당 없음"        : preferred
        };
    }

    private String extractSection(String fullText, String[] keywords, int maxLen) {
        for (String kw : keywords) {
            int idx = fullText.indexOf(kw);
            if (idx >= 0) {
                int start = idx + kw.length();
                int end   = Math.min(start + maxLen, fullText.length());
                // 다음 섹션 키워드 전까지만
                String[] nextSections = {"담당업무", "자격요건", "우대사항", "복리후생", "근무조건"};
                for (String next : nextSections) {
                    int nextIdx = fullText.indexOf(next, start);
                    if (nextIdx > start && nextIdx < end) end = nextIdx;
                }
                String result = fullText.substring(start, end).trim();
                if (!result.isEmpty()) return truncate(result, maxLen);
            }
        }
        return "";
    }

    private String truncate(String text, int max) {
        if (text == null || text.isEmpty()) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
