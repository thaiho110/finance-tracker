package com.financetracker.auth.config;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsConfig Unit Tests")
class UserDetailsConfigTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserDetailsConfig userDetailsConfig;

    @Test
    @DisplayName("Should expose the real users.id, not an id derived from the email")
    void shouldCarryRealUserId() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("thai@example.com").passwordHash("hash").role("user").build();
        when(userRepository.findByEmail("thai@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsConfig.loadUserByUsername("thai@example.com");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) details).getId()).isEqualTo(id);
        assertThat(details.getUsername()).isEqualTo("thai@example.com");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should reject unknown users")
    void shouldRejectUnknownUser() {
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsConfig.loadUserByUsername("gone@example.com"))
            .isInstanceOf(UsernameNotFoundException.class);
    }
}
