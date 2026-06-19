package com.moviebooking.service;

import com.moviebooking.domain.entity.User;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.auth.AuthDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.UserRepository;
import com.moviebooking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException("EMAIL_EXISTS", "Email already registered", HttpStatus.BAD_REQUEST);
        }
        var user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        user = userRepository.save(user);
        return AuthDtos.UserResponse.of(user.getId(), user.getEmail(), user.getRole());
    }

    public AuthDtos.UserResponse login(AuthDtos.LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase(), request.getPassword()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        var session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return AuthDtos.UserResponse.of(((UserPrincipal) auth.getPrincipal()).getId(),
                ((UserPrincipal) auth.getPrincipal()).getEmail(),
                ((UserPrincipal) auth.getPrincipal()).getRole());
    }

    public AuthDtos.UserResponse me(UserPrincipal principal) {
        return AuthDtos.UserResponse.of(principal.getId(), principal.getEmail(), principal.getRole());
    }
}
