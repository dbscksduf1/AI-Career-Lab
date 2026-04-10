package com.aicareerlab.service;

import com.aicareerlab.dto.AuthResponse;
import com.aicareerlab.dto.LoginRequest;
import com.aicareerlab.entity.User;
import com.aicareerlab.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request, HttpSession session) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> registerLocal(request)); // 없으면 자동 회원가입

        if (user.getPassword() == null) {
            return new AuthResponse(null, null, false, "Google 계정은 Google 버튼으로 로그인해주세요.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return new AuthResponse(null, null, false, "비밀번호가 올바르지 않습니다.");
        }

        // Spring Security 인증 컨텍스트 설정
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // 세션에 유저 정보 저장
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userName", user.getName());

        return new AuthResponse(user.getEmail(), user.getName(), true, "로그인 성공");
    }

    private User registerLocal(LoginRequest request) {
        String name = request.getEmail().split("@")[0];
        return userRepository.save(User.builder()
                .email(request.getEmail())
                .name(name)
                .password(passwordEncoder.encode(request.getPassword()))
                .provider("local")
                .role(User.Role.USER)
                .build());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}
