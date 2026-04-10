package com.aicareerlab.dto;

import com.aicareerlab.entity.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import java.util.List;

@Getter
public class ProfileResponse {

    private List<String> jobs;
    private List<String> stacks;
    private String region;
    private String career;
    private String email;
    private String natural;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProfileResponse from(UserProfile profile) {
        ProfileResponse response = new ProfileResponse();
        try {
            response.jobs   = objectMapper.readValue(profile.getJobs(),   new TypeReference<>() {});
            response.stacks = objectMapper.readValue(profile.getStacks(), new TypeReference<>() {});
        } catch (Exception e) {
            response.jobs   = List.of();
            response.stacks = List.of();
        }
        response.region  = profile.getRegion();
        response.career  = profile.getCareer();
        response.email   = profile.getEmail();
        response.natural = profile.getNatural();
        return response;
    }
}
