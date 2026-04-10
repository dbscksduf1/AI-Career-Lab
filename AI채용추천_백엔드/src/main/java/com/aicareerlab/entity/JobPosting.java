package com.aicareerlab.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_postings")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    private String location;

    @Column(nullable = false)
    private String platform;  // "saramin", "wanted"

    @Column(columnDefinition = "TEXT", unique = true)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String tags;        // JSON 배열 ["Java", "Spring"]

    @Column(columnDefinition = "TEXT")
    private String qualifications;  // 지원자격

    @Column(columnDefinition = "TEXT")
    private String requirements;    // 요구사항

    @Column(columnDefinition = "TEXT")
    private String preferred;       // 우대사항

    private String salary;          // 연봉 (없으면 "회사내규에 따름")
    private String education;       // 학력 (없으면 "학력무관")
    private String careerLevel;     // "신입", "경력", "신입·경력", "경력3년↑" 등

    private LocalDate deadline;

    private LocalDateTime collectedAt;

    @PrePersist
    protected void onCreate() {
        collectedAt = LocalDateTime.now();
    }
}
