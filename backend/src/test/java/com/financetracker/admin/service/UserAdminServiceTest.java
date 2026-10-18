package com.financetracker.admin.service;

import com.financetracker.access.model.Permission;
import com.financetracker.access.model.Role;
import com.financetracker.access.service.AccessService;
import com.financetracker.admin.dto.AdminUserResponse;
import com.financetracker.admin.dto.CreateUserRequest;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminService Unit Tests")
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccessService accessService;
    @Mock private TransactionService transactionService;
    @Mock private AuditService auditService;

    @InjectMocks private UserAdminService userAdminService;

    private AuthenticatedUser admin;
    private AuthenticatedUser superAdmin;

    @BeforeEach
    void setUp() {
        lenient().when(accessService.rank(Role.USER)).thenReturn(10);
        lenient().when(accessService.rank(Role.ADMIN)).thenReturn(50);
        lenient().when(accessService.rank(Role.SUPER_ADMIN)).thenReturn(100);
        admin = actor(Role.ADMIN, EnumSet.of(Permission.USER_READ, Permission.USER_SUSPEND, Permission.USER_CREATE));
        superAdmin = actor(Role.SUPER_ADMIN, EnumSet.allOf(Permission.class));
    }

    private static User account(String role) {
        return User.builder().id(UUID.randomUUID()).email(role.toLowerCase() + "@example.com").passwordHash("hash").role(role).build();
    }

    private static AuthenticatedUser actor(String role, Set<Permission> permissions) {
        return new AuthenticatedUser(account(role), permissions);
    }

    private User stored(String role) {
        User user = account(role);
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("An admin can disable a user, and the change is audited")
    void adminDisablesUser() {
        User target = stored(Role.USER);

        AdminUserResponse response = userAdminService.setEnabled(admin, target.getId(), false);

        assertThat(response.enabled()).isFalse();
        verify(userRepository).save(target);
        verify(auditService).record(admin.getId(), AuditAction.USER_DISABLED, target.getEmail(), null);
    }

    @Test
    @DisplayName("An admin can't disable another admin")
    void adminCannotManageAdmin() {
        User target = stored(Role.ADMIN);

        assertThatThrownBy(() -> userAdminService.setEnabled(admin, target.getId(), false))
            .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nobody changes their own status or role")
    void cannotManageSelf() {
        User self = User.builder().id(superAdmin.getId()).email("me@example.com").passwordHash("hash").role(Role.SUPER_ADMIN).build();
        when(userRepository.findById(self.getId())).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> userAdminService.changeRole(superAdmin, self.getId(), Role.USER))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("The last enabled super admin can't be demoted")
    void keepsLastSuperAdmin() {
        User target = stored(Role.SUPER_ADMIN);
        when(accessService.requireRole(Role.ADMIN)).thenReturn(Role.ADMIN);
        when(userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userAdminService.changeRole(superAdmin, target.getId(), Role.ADMIN))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("The super admin can promote a user to admin")
    void superAdminPromotes() {
        User target = stored(Role.USER);
        when(accessService.requireRole("admin")).thenReturn(Role.ADMIN);

        AdminUserResponse response = userAdminService.changeRole(superAdmin, target.getId(), "admin");

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        verify(auditService).record(superAdmin.getId(), AuditAction.ROLE_CHANGED, target.getEmail(), "USER -> ADMIN");
    }

    @Test
    @DisplayName("Creating an account with a role other than USER needs ROLE_MANAGE")
    void creatingAdminsNeedsRoleManage() {
        when(accessService.requireRole("ADMIN")).thenReturn(Role.ADMIN);

        assertThatThrownBy(() -> userAdminService.create(admin, new CreateUserRequest("new@example.com", "Secret#123", "ADMIN")))
            .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An admin with USER_CREATE can create a USER account")
    void adminCreatesUser() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = userAdminService.create(admin, new CreateUserRequest("new@example.com", "Secret#123", null));

        assertThat(response.role()).isEqualTo(Role.USER);
        verify(auditService).record(eq(admin.getId()), eq(AuditAction.USER_CREATED), eq("new@example.com"), anyString());
    }
}
