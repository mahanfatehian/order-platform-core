package com.orderprocessing.userservice.service;

import com.orderprocessing.security.service.TokenRevocationService;
import com.orderprocessing.userservice.dto.ChangePasswordRequest;
import com.orderprocessing.userservice.dto.CreateUserRequest;
import com.orderprocessing.userservice.dto.PageResponse;
import com.orderprocessing.userservice.dto.UpdateProfileRequest;
import com.orderprocessing.userservice.dto.UpdateUserRolesRequest;
import com.orderprocessing.userservice.dto.UpdateUserStatusRequest;
import com.orderprocessing.userservice.dto.UserResponse;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.exception.AuthenticationFailedException;
import com.orderprocessing.userservice.exception.DuplicateResourceException;
import com.orderprocessing.userservice.exception.ForbiddenOperationException;
import com.orderprocessing.userservice.exception.ResourceNotFoundException;
import com.orderprocessing.userservice.repository.RoleRepository;
import com.orderprocessing.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_ROLE = "ROLE_USER";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "username", "username",
            "email", "email",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    @Transactional
    public UserResponse register(CreateUserRequest request) {
        String username = normalizeIdentifier(request.getUsername());
        String email = normalizeIdentifier(request.getEmail());
        ensureUnique(username, email, null);

        RoleEntity defaultRole = roleRepository.findByName(USER_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Default role ROLE_USER not found"));
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(cleanName(request.getFirstName()))
                .lastName(cleanName(request.getLastName()))
                .enabled(true)
                .accountNonLocked(true)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();
        return mapToResponse(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrent(UUID userId) {
        return mapToResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateCurrent(UUID userId, UpdateProfileRequest request) {
        UserEntity user = findUserForUpdate(userId);
        boolean securityIdentityChanged = false;

        if (request.getUsername() != null) {
            String username = normalizeIdentifier(request.getUsername());
            if (!username.equals(user.getUsername())) {
                ensureUnique(username, null, userId);
                user.setUsername(username);
                securityIdentityChanged = true;
            }
        }
        if (request.getEmail() != null) {
            String email = normalizeIdentifier(request.getEmail());
            if (!email.equals(user.getEmail())) {
                ensureUnique(null, email, userId);
                user.setEmail(email);
            }
        }
        if (request.getFirstName() != null) {
            user.setFirstName(cleanName(request.getFirstName()));
        }
        if (request.getLastName() != null) {
            user.setLastName(cleanName(request.getLastName()));
        }
        if (securityIdentityChanged) {
            tokenRevocationService.incrementTokenVersion(userId);
        }
        return mapToResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity user = findUserForUpdate(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);
        tokenRevocationService.incrementTokenVersion(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchAdminUsers(
            String search,
            Boolean enabled,
            int page,
            int size,
            String sort,
            Sort.Direction direction
    ) {
        String sortField = SORT_FIELDS.get(sort);
        if (sortField == null) {
            throw new IllegalArgumentException("Unsupported user sort field: " + sort);
        }
        String query = search == null || search.isBlank() ? "" : search.strip();
        Page<UserResponse> result = userRepository.search(
                        query,
                        enabled,
                        PageRequest.of(page, size, Sort.by(direction, sortField))
                )
                .map(this::mapToResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public UserResponse getAdminUser(UUID userId) {
        return mapToResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateUserStatusRequest request) {
        roleRepository.findByNameForUpdate(ADMIN_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role ROLE_ADMIN not found"));
        UserEntity user = findUserForUpdate(userId);
        boolean wasActiveAdmin = isActiveAdmin(user);
        boolean nextEnabled = request.getEnabled() == null ? user.isEnabled() : request.getEnabled();
        boolean nextUnlocked = request.getAccountNonLocked() == null
                ? user.isAccountNonLocked()
                : request.getAccountNonLocked();
        if (wasActiveAdmin && (!nextEnabled || !nextUnlocked) && userRepository.countActiveAdmins() <= 1) {
            throw new ForbiddenOperationException("The last active administrator cannot be disabled or locked");
        }
        boolean changed = user.isEnabled() != nextEnabled || user.isAccountNonLocked() != nextUnlocked;
        user.setEnabled(nextEnabled);
        user.setAccountNonLocked(nextUnlocked);
        UserResponse response = mapToResponse(userRepository.saveAndFlush(user));
        if (changed) {
            tokenRevocationService.incrementTokenVersion(userId);
        }
        return response;
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, UpdateUserRolesRequest request) {
        roleRepository.findByNameForUpdate(ADMIN_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role ROLE_ADMIN not found"));
        UserEntity user = findUserForUpdate(userId);
        Set<String> requestedNames = request.getRoles().stream()
                .map(value -> value.strip().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        Set<RoleEntity> roles = new HashSet<>(roleRepository.findAllByNameIn(requestedNames));
        Set<String> resolvedNames = roles.stream().map(RoleEntity::getName).collect(Collectors.toSet());
        if (!resolvedNames.equals(requestedNames)) {
            throw new IllegalArgumentException("One or more requested roles do not exist");
        }

        Set<String> currentNames = roleNames(user);
        if (isActiveAdmin(user)
                && currentNames.contains(ADMIN_ROLE)
                && !requestedNames.contains(ADMIN_ROLE)
                && userRepository.countActiveAdmins() <= 1) {
            throw new ForbiddenOperationException("The last active administrator cannot lose ROLE_ADMIN");
        }
        if (!currentNames.equals(requestedNames)) {
            user.setRoles(roles);
            userRepository.saveAndFlush(user);
            tokenRevocationService.incrementTokenVersion(userId);
        }
        return mapToResponse(user);
    }

    private void ensureUnique(String username, String email, UUID currentUserId) {
        if (username != null && userRepository.existsByUsernameIgnoreCase(username)) {
            UserEntity existing = userRepository.findByUsernameIgnoreCase(username).orElse(null);
            if (existing == null || !existing.getId().equals(currentUserId)) {
                throw new DuplicateResourceException("Username already exists");
            }
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            UserEntity existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (existing == null || !existing.getId().equals(currentUserId)) {
                throw new DuplicateResourceException("Email already exists");
            }
        }
    }

    private UserEntity findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private UserEntity findUserForUpdate(UUID id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private boolean isActiveAdmin(UserEntity user) {
        return user.isEnabled() && user.isAccountNonLocked() && roleNames(user).contains(ADMIN_ROLE);
    }

    private Set<String> roleNames(UserEntity user) {
        return user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet());
    }

    private String normalizeIdentifier(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private UserResponse mapToResponse(UserEntity user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(roleNames(user))
                .build();
    }
}
