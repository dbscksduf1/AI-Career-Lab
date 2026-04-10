package com.aicareerlab.crawler;

import com.aicareerlab.entity.JobPosting;
import java.util.List;

public interface BaseCrawler {
    List<JobPosting> crawl(String keyword);
}
