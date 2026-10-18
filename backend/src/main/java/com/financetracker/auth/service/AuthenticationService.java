package com.financetracker.auth.service;

import com.financetracker.auth.dto.AuthRequest;
import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.auth.dto.RegisterRequest;
import com.financetracker.auth.dto.UserInfoResponse;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.common.exception.AuthenticationException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthenticationException("Email already registered");
        }

        var user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role("user")
            .build();

        userRepository.save(user);

        var userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPasswordHash(), java.util.Collections.emptyList());

        var token = jwtService.generateToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(token, refreshToken, user.getId().toString(), user.getEmail(), user.getRole());
    }

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        var userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPasswordHash(), java.util.Collections.emptyList());

        var token = jwtService.generateToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(token, refreshToken, user.getId().toString(), user.getEmail(), user.getRole());
    }

    public AuthResponse refreshToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationException("Invalid refresh token");
        }
        String refreshToken = authHeader.substring(7);
        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationException("Refresh token is invalid or expired");
        }

        var user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("User not found"));

        var userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPasswordHash(), java.util.Collections.emptyList());

        var newToken = jwtService.generateToken(userDetails);
        var newRefreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(newToken, newRefreshToken, user.getId().toString(), user.getEmail(), user.getRole());
    }

    /**
     * The signed-in user's profile, used by clients to restore their session after a reload.
     *
     * @param email the authenticated principal's username (email)
     * @throws AuthenticationException when the account no longer exists
     */
    public UserInfoResponse currentUser(String email) {
        var user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("User not found"));
        return new UserInfoResponse(user.getId().toString(), user.getEmail(), user.getRole());
    }
}
