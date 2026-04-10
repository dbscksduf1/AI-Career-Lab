package com.aicareerlab.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_profiles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String jobs;       // JSON 배열 ["backend", "frontend"]

    @Column(columnDefinition = "TEXT")
    private String stacks;     // JSON 배열 ["Java", "Spring"]

    private String region;     // "seoul", "gyeonggi" 등

    private String career;     // "new", "1-3", "3-5", "5+"

    private String email;      // 추천 받을 이메일

    @Column(name = "natural_desc", columnDefinition = "TEXT")
    private String natural;    // 자연어 추가 조건
}
