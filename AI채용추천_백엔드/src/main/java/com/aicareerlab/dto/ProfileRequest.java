package com.aicareerlab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import java.util.List;

@Getter
public class ProfileRequest {

    @NotEmpty(message = "직무를 하나 이상 선택해주세요.")
    private List<String> jobs;

    @NotEmpty(message = "보유 스킬을 하나 이상 선택해주세요.")
    private List<String> stacks;

    private String region;

    private String career;

    @Email
    private String email;

    private String natural;
}
