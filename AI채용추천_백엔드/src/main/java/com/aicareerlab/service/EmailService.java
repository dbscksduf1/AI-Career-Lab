package com.aicareerlab.service;

import com.aicareerlab.entity.JobPosting;
import com.aicareerlab.entity.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendRecommendations(String toEmail, String userName, List<Recommendation> recommendations) {
        try {
            String subject = "[AI Career Lab] 이번 주 맞춤 채용 추천 " + recommendations.size() + "건";
            String body = buildEmailBody(userName, recommendations);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);

            log.info("이메일 발송 완료: to={}", toEmail);

        } catch (Exception e) {
            log.error("이메일 발송 실패: to={}, error={}", toEmail, e.getMessage());
        }
    }

    private String buildEmailBody(String userName, List<Recommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        sb.append(userName).append("님, 이번 주 맞춤 채용 공고입니다.\n\n");

        for (int i = 0; i < recommendations.size(); i++) {
            JobPosting job = recommendations.get(i).getJobPosting();
            int score = recommendations.get(i).getScore();

            sb.append(i + 1).append(". ").append(job.getTitle()).append("\n");
            sb.append("   회사: ").append(job.getCompany()).append("\n");
            sb.append("   지역: ").append(job.getLocation()).append("\n");
            sb.append("   매칭점수: ").append(score).append("점\n");
            sb.append("   링크: ").append(job.getUrl()).append("\n\n");
        }

        sb.append("--\nAI Career Lab | 매주 월요일 오전 9시 발송");
        return sb.toString();
    }
}
