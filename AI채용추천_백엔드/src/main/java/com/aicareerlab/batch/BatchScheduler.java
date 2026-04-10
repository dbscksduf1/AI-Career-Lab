package com.aicareerlab.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job recommendationJob;

    // 매주 월요일 오전 9시 실행
    @Scheduled(cron = "0 0 9 * * MON")
    public void runWeeklyBatch() {
        log.info("=== 주간 추천 배치 시작: {} ===", LocalDateTime.now());
        runBatch();
    }

    // 즉시 실행 (지금받기 버튼용)
    public void runBatch() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTime", LocalDateTime.now().toString())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(recommendationJob, params);
            log.info("배치 실행 완료: status={}", execution.getStatus());

        } catch (Exception e) {
            log.error("배치 실행 실패: {}", e.getMessage());
        }
    }
}
