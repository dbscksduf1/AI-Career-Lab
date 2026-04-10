package com.aicareerlab.service;

import com.aicareerlab.dto.ProfileRequest;
import com.aicareerlab.dto.ProfileResponse;
import com.aicareerlab.entity.User;
import com.aicareerlab.entity.UserProfile;
import com.aicareerlab.repository.UserProfileRepository;
import com.aicareerlab.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    public ProfileResponse save(ProfileRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        // 이미 프로필 있으면 수정, 없으면 생성
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(UserProfile.builder().user(user).build());

        try {
            profile.setJobs(objectMapper.writeValueAsString(request.getJobs()));
            profile.setStacks(objectMapper.writeValueAsString(request.getStacks()));
        } catch (Exception e) {
            throw new RuntimeException("데이터 변환 오류");
        }

        profile.setRegion(request.getRegion());
        profile.setCareer(request.getCareer());
        profile.setEmail(request.getEmail());
        profile.setNatural(request.getNatural());

        return ProfileResponse.from(userProfileRepository.save(profile));
    }

    public ProfileResponse get(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("프로필이 없습니다."));
        return ProfileResponse.from(profile);
    }
}
