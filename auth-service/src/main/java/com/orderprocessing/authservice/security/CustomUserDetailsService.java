package com.orderprocessing.authservice.security;

import com.orderprocessing.userservice.entity.AuthorityEntity;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        for (RoleEntity role : user.getRoles()) {

            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_%s".formatted(role.getName())
                    )
            );

            for (AuthorityEntity authority : role.getAuthorities()) {

                authorities.add(
                        new SimpleGrantedAuthority(
                                authority.getName()
                        )
                );
            }
        }

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
