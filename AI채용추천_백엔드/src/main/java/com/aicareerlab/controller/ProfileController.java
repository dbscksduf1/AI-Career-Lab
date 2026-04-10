package com.aicareerlab.controller;

import com.aicareerlab.dto.ProfileRequest;
import com.aicareerlab.dto.ProfileResponse;
import com.aicareerlab.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "프로필 API")
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    @Operation(summary = "프로필 저장 (없으면 생성, 있으면 수정)")
    public ResponseEntity<ProfileResponse> save(
            @Valid @RequestBody ProfileRequest request,
            HttpSession session) {
        return ResponseEntity.ok(profileService.save(request, session));
    }

    @GetMapping
    @Operation(summary = "프로필 조회")
    public ResponseEntity<ProfileResponse> get(HttpSession session) {
        return ResponseEntity.ok(profileService.get(session));
    }
}
