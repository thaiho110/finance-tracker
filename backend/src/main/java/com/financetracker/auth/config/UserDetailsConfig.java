package com.financetracker.auth.config;

import com.financetracker.access.service.AccessService;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig implements UserDetailsService {

    private final UserRepository userRepository;
    private final AccessService accessService;

    /** Loads the account with its effective permissions; called on every authenticated request. */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .map(user -> new AuthenticatedUser(user, accessService.effectivePermissions(user.getId(), user.getRole())))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
