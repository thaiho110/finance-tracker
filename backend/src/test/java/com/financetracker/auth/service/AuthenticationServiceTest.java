package com.financetracker.auth.service;

import com.financetracker.access.model.Permission;
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
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AccessService accessService;
    @Mock private SettingsService settingsService;
    @Mock private InviteService inviteService;

    @InjectMocks private AuthenticationService authService;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("test@example.com", "password123", null);
        authRequest = new AuthRequest("test@example.com", "password123");
        testUser = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("encoded-password")
            .role("USER")
            .build();
    }

    @Test
    @DisplayName("Should register a new user successfully")
    void shouldRegisterUser() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(settingsService.getRegistrationMode()).thenReturn(RegistrationMode.OPEN);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(registerRequest);
        assertThat(response.id()).isEqualTo(testUser.getId().toString());

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo("USER");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw when registering with existing email")
    void shouldThrowWhenEmailExists() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should refuse registration when the mode is CLOSED")
    void shouldRefuseWhenClosed() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(settingsService.getRegistrationMode()).thenReturn(RegistrationMode.CLOSED);

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("closed");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should require a valid invite when the mode is INVITE_ONLY")
    void shouldRequireInviteWhenInviteOnly() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(settingsService.getRegistrationMode()).thenReturn(RegistrationMode.INVITE_ONLY);
        when(inviteService.requireUsable(null)).thenThrow(new AuthenticationException("An invite is required to register"));

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(AuthenticationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should consume a valid invite when the mode is INVITE_ONLY")
    void shouldConsumeInvite() {
        RegisterRequest withInvite = new RegisterRequest("test@example.com", "password123", "invite-token");
        RegistrationInvite invite = RegistrationInvite.builder().id(UUID.randomUUID()).build();
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(settingsService.getRegistrationMode()).thenReturn(RegistrationMode.INVITE_ONLY);
        when(inviteService.requireUsable("invite-token")).thenReturn(invite);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(withInvite);

        assertThat(response.token()).isEqualTo("access-token");
        verify(inviteService).markUsed(invite, testUser.getId());
    }

    @Test
    @DisplayName("The configured owner can register even when the mode is CLOSED")
    void ownerBypassesClosed() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(accessService.isConfiguredOwner("test@example.com")).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.token()).isEqualTo("access-token");
        verify(settingsService, never()).getRegistrationMode();
    }

    @Test
    @DisplayName("Should authenticate user successfully")
    void shouldAuthenticate() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.authenticate(authRequest);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.email()).isEqualTo("test@example.com");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Should throw when authenticating with invalid credentials")
    void shouldThrowWhenInvalidCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate(authRequest))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should refresh token successfully")
    void shouldRefreshToken() {
        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh-token");

        AuthResponse response = authService.refreshToken("Bearer valid-refresh-token");

        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("Should throw when refresh token header is invalid")
    void shouldThrowWhenInvalidRefreshHeader() {
        assertThatThrownBy(() -> authService.refreshToken("InvalidHeader"))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Should reject an expired or malformed refresh token with an authentication error")
    void shouldRejectInvalidRefreshToken() {
        when(jwtService.extractUsername("expired-token")).thenThrow(new MalformedJwtException("expired"));

        assertThatThrownBy(() -> authService.refreshToken("Bearer expired-token"))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("invalid or expired");
    }

    @Test
    @DisplayName("Should return the current user's email and role")
    void shouldReturnCurrentUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        UserInfoResponse me = authService.currentUser("test@example.com");

        assertThat(me.id()).isEqualTo(testUser.getId().toString());
        assertThat(me.email()).isEqualTo("test@example.com");
        assertThat(me.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should return the user's effective permissions, sorted")
    void shouldReturnPermissions() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(accessService.effectivePermissions(testUser.getId(), "USER"))
            .thenReturn(Set.of(Permission.TRANSACTION_READ, Permission.CSV_IMPORT));

        UserInfoResponse me = authService.currentUser("test@example.com");

        assertThat(me.permissions()).containsExactly("CSV_IMPORT", "TRANSACTION_READ");
    }

    @Test
    @DisplayName("Should give a new account the role chosen by the access rules")
    void shouldUseInitialRoleFromAccessRules() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(accessService.initialRoleFor("test@example.com")).thenReturn("SUPER_ADMIN");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        authService.register(registerRequest);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("Should not refresh tokens for a disabled account")
    void shouldRejectRefreshForDisabledAccount() {
        testUser.setEnabled(false);
        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.refreshToken("Bearer valid-refresh-token"))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("disabled");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Should throw when the current user no longer exists")
    void shouldThrowWhenCurrentUserMissing() {
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser("gone@example.com"))
            .isInstanceOf(AuthenticationException.class);
    }
}
