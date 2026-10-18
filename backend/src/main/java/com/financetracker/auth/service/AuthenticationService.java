package com.financetracker.auth.service;

import com.financetracker.access.service.AccessService;
import com.financetracker.auth.dto.AuthRequest;
import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.auth.dto.RegisterRequest;
import com.financetracker.auth.dto.UserInfoResponse;
import com.financetracker.auth.model.RegistrationInvite;
import com.financetracker.auth.model.RegistrationMode;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.settings.service.SettingsService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AccessService accessService;
    private final SettingsService settingsService;
    private final InviteService inviteService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthenticationException("Email already registered");
        }

        // The configured owner can always bootstrap their own account; everyone else is gated by the mode.
        RegistrationInvite invite = null;
        if (!accessService.isConfiguredOwner(request.email())) {
            RegistrationMode mode = settingsService.getRegistrationMode();
            if (mode == RegistrationMode.CLOSED) {
                throw new AuthenticationException("Registration is closed");
            }
            if (mode == RegistrationMode.INVITE_ONLY) {
                invite = inviteService.requireUsable(request.inviteToken());
            }
        }

        var user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(accessService.initialRoleFor(request.email()))
            .build();

        User saved = userRepository.save(user);
        if (invite != null) {
            inviteService.markUsed(invite, saved.getId());
        }

        var userDetails = new org.springframework.security.core.userdetails.User(
            saved.getEmail(), saved.getPasswordHash(), java.util.Collections.emptyList());

        var token = jwtService.generateToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(token, refreshToken, saved.getId().toString(), saved.getEmail(), saved.getRole(),
            permissionNames(saved));
    }

    /** The current registration mode, so clients can show or hide sign-up and ask for an invite. */
    public RegistrationMode registrationMode() {
        return settingsService.getRegistrationMode();
    }

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new AuthenticationException("Invalid email or password"));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        var userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPasswordHash(), java.util.Collections.emptyList());

        var token = jwtService.generateToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(token, refreshToken, user.getId().toString(), user.getEmail(), user.getRole(),
            permissionNames(user));
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
            throw new AuthenticationException("Refresh token is invalid or expired", e);
        }

        var user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("User not found"));
        if (!user.isEnabled()) {
            throw new AuthenticationException("This account is disabled");
        }

        var userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(), user.getPasswordHash(), java.util.Collections.emptyList());

        var newToken = jwtService.generateToken(userDetails);
        var newRefreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(newToken, newRefreshToken, user.getId().toString(), user.getEmail(), user.getRole(),
            permissionNames(user));
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
        return new UserInfoResponse(user.getId().toString(), user.getEmail(), user.getRole(), permissionNames(user));
    }

    /** Effective permissions as sorted names, for clients to show or hide features. */
    private List<String> permissionNames(User user) {
        return accessService.effectivePermissions(user.getId(), user.getRole()).stream()
            .map(Enum::name)
            .sorted()
            .toList();
    }
}
