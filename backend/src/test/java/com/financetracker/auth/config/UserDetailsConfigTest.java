package com.financetracker.auth.config;

import com.financetracker.access.model.Permission;
import com.financetracker.access.service.AccessService;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsConfig Unit Tests")
class UserDetailsConfigTest {

    @Mock private UserRepository userRepository;
    @Mock private AccessService accessService;

    @InjectMocks private UserDetailsConfig userDetailsConfig;

    @Test
    @DisplayName("Should expose the real users.id, the role and the effective permissions")
    void shouldCarryIdRoleAndPermissions() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("thai@example.com").passwordHash("hash").role("USER").build();
        when(userRepository.findByEmail("thai@example.com")).thenReturn(Optional.of(user));
        when(accessService.effectivePermissions(id, "USER")).thenReturn(Set.of(Permission.CSV_IMPORT));

        UserDetails details = userDetailsConfig.loadUserByUsername("thai@example.com");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser authenticated = (AuthenticatedUser) details;
        assertThat(authenticated.getId()).isEqualTo(id);
        assertThat(authenticated.getRoleCode()).isEqualTo("USER");
        assertThat(authenticated.hasPermission(Permission.CSV_IMPORT)).isTrue();
        assertThat(authenticated.hasPermission(Permission.RECEIPT_SCAN)).isFalse();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_USER", "CSV_IMPORT");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should mark disabled accounts as disabled")
    void shouldReportDisabledAccounts() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("off@example.com").passwordHash("hash").role("USER").enabled(false).build();
        when(userRepository.findByEmail("off@example.com")).thenReturn(Optional.of(user));
        when(accessService.effectivePermissions(id, "USER")).thenReturn(Set.of());

        assertThat(userDetailsConfig.loadUserByUsername("off@example.com").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should reject unknown users")
    void shouldRejectUnknownUser() {
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsConfig.loadUserByUsername("gone@example.com"))
            .isInstanceOf(UsernameNotFoundException.class);
    }
}
