package com.financetracker.access.service;

import com.financetracker.access.config.AccessProperties;
import com.financetracker.access.model.FeatureState;
import com.financetracker.access.model.Permission;
import com.financetracker.access.model.Role;
import com.financetracker.access.model.UserFeatureOverride;
import com.financetracker.access.repository.RoleRepository;
import com.financetracker.access.repository.UserFeatureOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessService Unit Tests")
class AccessServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private UserFeatureOverrideRepository overrides;

    private final AccessProperties properties = new AccessProperties();
    private AccessService accessService;

    private final UUID userId = UUID.randomUUID();
    private final Role userRole = new Role(Role.USER, "User", 10, List.of(
        Permission.TRANSACTION_READ, Permission.TRANSACTION_WRITE, Permission.CSV_IMPORT, Permission.RECEIPT_SCAN));

    @BeforeEach
    void setUp() {
        accessService = new AccessService(roleRepository, overrides, properties);
        lenient().when(roleRepository.findById(Role.USER)).thenReturn(Optional.of(userRole));
        lenient().when(overrides.findByUserId(userId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Super admin has every permission, whatever is stored")
    void superAdminHasEverything() {
        assertThat(accessService.effectivePermissions(userId, Role.SUPER_ADMIN)).isEqualTo(EnumSet.allOf(Permission.class));
    }

    @Test
    @DisplayName("Other accounts get what their role grants")
    void roleGrantsPermissions() {
        assertThat(accessService.effectivePermissions(userId, Role.USER)).containsExactlyInAnyOrder(
            Permission.TRANSACTION_READ, Permission.TRANSACTION_WRITE, Permission.CSV_IMPORT, Permission.RECEIPT_SCAN);
    }

    @Test
    @DisplayName("Per-user overrides switch features on and off")
    void overridesSwitchFeatures() {
        when(overrides.findByUserId(userId)).thenReturn(List.of(
            new UserFeatureOverride(userId, Permission.RECEIPT_SCAN, false, null),
            new UserFeatureOverride(userId, Permission.DASHBOARD_VIEW, true, null)));

        Set<Permission> permissions = accessService.effectivePermissions(userId, Role.USER);

        assertThat(permissions).doesNotContain(Permission.RECEIPT_SCAN).contains(Permission.DASHBOARD_VIEW, Permission.CSV_IMPORT);
    }

    @Test
    @DisplayName("Overrides of non-feature permissions are ignored")
    void overridesOnlyApplyToFeatures() {
        when(overrides.findByUserId(userId)).thenReturn(List.of(
            new UserFeatureOverride(userId, Permission.ROLE_MANAGE, true, null),
            new UserFeatureOverride(userId, Permission.TRANSACTION_READ, false, null)));

        Set<Permission> permissions = accessService.effectivePermissions(userId, Role.USER);

        assertThat(permissions).doesNotContain(Permission.ROLE_MANAGE).contains(Permission.TRANSACTION_READ);
    }

    @Test
    @DisplayName("Feature list shows the role default, the override and the result")
    void listsFeatureStates() {
        when(overrides.findByUserId(userId)).thenReturn(List.of(new UserFeatureOverride(userId, Permission.CSV_IMPORT, false, null)));

        List<FeatureState> features = accessService.features(userId, Role.USER);

        assertThat(features).extracting(FeatureState::permission)
            .containsExactly(Permission.DASHBOARD_VIEW, Permission.CSV_IMPORT, Permission.RECEIPT_SCAN);
        assertThat(features.get(0)).isEqualTo(new FeatureState(Permission.DASHBOARD_VIEW, false, null, false));
        assertThat(features.get(1)).isEqualTo(new FeatureState(Permission.CSV_IMPORT, true, false, false));
        assertThat(features.get(2)).isEqualTo(new FeatureState(Permission.RECEIPT_SCAN, true, null, true));
    }

    @Test
    @DisplayName("Only feature permissions can be switched per user")
    void rejectsNonFeatureOverride() {
        assertThatThrownBy(() -> accessService.setFeature(userId, Permission.USER_READ, true, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(overrides, never()).save(any());
    }

    @Test
    @DisplayName("Clearing an override returns the feature to the role default")
    void clearsOverride() {
        UserFeatureOverride existing = new UserFeatureOverride(userId, Permission.CSV_IMPORT, false, null);
        when(overrides.findByUserIdAndPermission(userId, Permission.CSV_IMPORT)).thenReturn(Optional.of(existing));

        accessService.setFeature(userId, Permission.CSV_IMPORT, null, null);

        verify(overrides).delete(existing);
    }

    @Test
    @DisplayName("The super admin role can't be edited, and owner-only permissions can't go in any role")
    void protectsRoleTable() {
        Role superAdmin = new Role(Role.SUPER_ADMIN, "Super admin", 100, List.of());
        when(roleRepository.findById(Role.SUPER_ADMIN)).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> accessService.replaceRolePermissions(Role.SUPER_ADMIN, List.of(Permission.USER_READ)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accessService.replaceRolePermissions("user", List.of(Permission.FEATURE_MANAGE)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FEATURE_MANAGE");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registering with the configured owner email gives SUPER_ADMIN; everyone else gets USER")
    void choosesInitialRole() {
        properties.setSuperAdminEmail(" Owner@Example.com ");

        assertThat(accessService.initialRoleFor("owner@example.com")).isEqualTo(Role.SUPER_ADMIN);
        assertThat(accessService.initialRoleFor("someone@example.com")).isEqualTo(Role.USER);
    }
}
