package com.aicareerlab.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScoreDetail {
    private int totalScore;
    private int baseScore;
    private int skillScore;
    private int tagScore;
    private int bonusScore;
    private String matchedSkills;
}
