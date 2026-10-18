package com.financetracker.auth.config;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .map(user -> new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole()))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
