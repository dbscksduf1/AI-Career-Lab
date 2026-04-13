package com.aicareerlab.controller;

import com.aicareerlab.dto.AuthResponse;
import com.aicareerlab.dto.LoginRequest;
import com.aicareerlab.entity.User;
import com.aicareerlab.repository.UserRepository;
import com.aicareerlab.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Operation(summary = "이메일/PW 로그인 (없으면 자동 회원가입)")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpSession session) {
        return ResponseEntity.ok(authService.login(request, session));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<Void> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 유저 정보")
    public ResponseEntity<AuthResponse> me(HttpSession session) {
        String email = (String) session.getAttribute("userEmail");
        String name  = (String) session.getAttribute("userName");
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new AuthResponse(email, name, true, "인증됨"));
    }

    @GetMapping("/oauth2/success")
    @Operation(summary = "Google OAuth2 로그인 성공 후 리다이렉트")
    public void oauth2Success(HttpSession session,
            @AuthenticationPrincipal OAuth2User oAuth2User,
            HttpServletResponse response) throws IOException {
        if (oAuth2User != null) {
            String email = oAuth2User.getAttribute("email");
            String name  = oAuth2User.getAttribute("name");
            session.setAttribute("userEmail", email);
            session.setAttribute("userName",  name);
            userRepository.findByEmail(email).ifPresent(user ->
                session.setAttribute("userId", user.getId())
            );
        }
        response.sendRedirect("https://ai-career-lab-rho.vercel.app/dashboard.html");
    }
}
