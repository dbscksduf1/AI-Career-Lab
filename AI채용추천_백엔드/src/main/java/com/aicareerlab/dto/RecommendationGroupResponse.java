package com.aicareerlab.dto;

import com.aicareerlab.entity.Recommendation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class RecommendationGroupResponse {

    private String date;
    private List<JobDto> jobs;

    @Getter
    @AllArgsConstructor
    public static class JobDto {
        private Long id;
        private String platform;
        private String title;
        private String company;
        private String location;
        private int score;
        private String deadline;
        private String url;
        private String tags;
        private String qualifications;
        private String requirements;
        private String preferred;
        private String salary;
        private String education;
        // 점수 세부 내역
        private int baseScore;
        private int skillScore;
        private int tagScore;
        private int bonusScore;
        private String matchedSkills;
    }

    public static RecommendationGroupResponse from(LocalDate date, List<Recommendation> recommendations) {
        List<JobDto> jobs = recommendations.stream().map(r -> new JobDto(
                r.getJobPosting().getId(),
                r.getJobPosting().getPlatform(),
                r.getJobPosting().getTitle(),
                r.getJobPosting().getCompany(),
                r.getJobPosting().getLocation(),
                r.getScore(),
                r.getJobPosting().getDeadline() != null ? r.getJobPosting().getDeadline().toString() : null,
                r.getJobPosting().getUrl(),
                r.getJobPosting().getTags(),
                r.getJobPosting().getQualifications(),
                r.getJobPosting().getRequirements(),
                r.getJobPosting().getPreferred(),
                r.getJobPosting().getSalary() != null ? r.getJobPosting().getSalary() : "회사내규에 따름",
                r.getJobPosting().getEducation() != null ? r.getJobPosting().getEducation() : "학력무관",
                r.getBaseScore(),
                r.getSkillScore(),
                r.getTagScore(),
                r.getBonusScore(),
                r.getMatchedSkills() != null ? r.getMatchedSkills() : ""
        )).collect(Collectors.toList());

        return new RecommendationGroupResponse(date.toString(), jobs);
    }
}
