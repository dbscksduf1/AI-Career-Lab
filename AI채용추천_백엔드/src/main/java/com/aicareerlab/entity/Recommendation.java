package com.aicareerlab.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "job_posting_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    private int score;        // 적합도 점수 (0~100)
    private int baseScore;    // 기본 점수
    private int skillScore;   // 스킬 매칭 점수
    private int tagScore;     // 태그 매칭 점수
    private int bonusScore;   // 직접 매칭 보너스
    private String matchedSkills; // 매칭된 스킬 목록 (콤마 구분)

    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
