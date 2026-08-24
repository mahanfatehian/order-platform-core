package com.orderprocessing.userservice.service;

import com.orderprocessing.security.service.TokenRevocationService;
import com.orderprocessing.userservice.dto.ChangePasswordRequest;
import com.orderprocessing.userservice.dto.CreateUserRequest;
import com.orderprocessing.userservice.dto.PageResponse;
import com.orderprocessing.userservice.dto.UserResponse;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.repository.RoleRepository;
import com.orderprocessing.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void unfilteredAdminSearchUsesTypedEmptyStringInsteadOfNull() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        when(users.searchIds(eq(""), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        new UserService(users, roles, encoder, revocation)
                .searchAdminUsers(null, null, 0, 20, "createdAt", Sort.Direction.DESC);

        verify(users).searchIds(eq(""), isNull(), any(Pageable.class));
    }

    @Test
    void adminSearchLoadsOnlyThePagedIdentifiersAndKeepsTheirOrder() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UserEntity first = user("bravo");
        UserEntity second = user("alpha");
        List<UUID> ordered = List.of(first.getId(), second.getId());
        when(users.searchIds(eq(""), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ordered, PageRequest.of(0, 20), 57));
        // Returned deliberately out of order: an "in :ids" query guarantees nothing about row order.
        when(users.findAllWithRolesByIdIn(anyList())).thenReturn(List.of(second, first));

        PageResponse<UserResponse> page = new UserService(users, roles, encoder, revocation)
                .searchAdminUsers(null, null, 0, 20, "username", Sort.Direction.ASC);

        assertThat(page.content()).extracting(UserResponse::getUsername).containsExactly("bravo", "alpha");
        assertThat(page.totalElements()).isEqualTo(57);
        verify(users).findAllWithRolesByIdIn(ordered);
    }

    @Test
    void adminSearchSkipsTheSecondQueryWhenThePageIsEmpty() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        when(users.searchIds(eq("ghost"), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<UserResponse> page = new UserService(users, roles, encoder, revocation)
                .searchAdminUsers("ghost", null, 0, 20, "createdAt", Sort.Direction.DESC);

        assertThat(page.content()).isEmpty();
        verify(users, never()).findAllWithRolesByIdIn(anyList());
    }

    @Test
    void adminSearchDropsAUserDeletedBetweenTheTwoQueries() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UserEntity survivor = user("survivor");
        when(users.searchIds(eq(""), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(survivor.getId(), UUID.randomUUID()), PageRequest.of(0, 20), 2));
        when(users.findAllWithRolesByIdIn(anyList())).thenReturn(List.of(survivor));

        PageResponse<UserResponse> page = new UserService(users, roles, encoder, revocation)
                .searchAdminUsers(null, null, 0, 20, "createdAt", Sort.Direction.DESC);

        assertThat(page.content()).extracting(UserResponse::getUsername).containsExactly("survivor");
    }

    private static UserEntity user(String username) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .roles(Set.of(RoleEntity.builder().name("ROLE_USER").build()))
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void registrationNormalizesIdentityHashesPasswordAndAssignsOnlyUserRole() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        RoleEntity userRole = RoleEntity.builder().name("ROLE_USER").build();
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(encoder.encode("Customer123!")).thenReturn("hash");
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        CreateUserRequest request = new CreateUserRequest(
                " Customer.User ",
                " Customer@Example.COM ",
                "Customer123!",
                " Customer ",
                " User "
        );

        UserResponse response = new UserService(users, roles, encoder, revocation).register(request);

        assertThat(response.getUsername()).isEqualTo("customer.user");
        assertThat(response.getEmail()).isEqualTo("customer@example.com");
        assertThat(response.getRoles()).containsExactly("ROLE_USER");
        verify(encoder).encode("Customer123!");
    }

    @Test
    void passwordChangeVerifiesCurrentPasswordAndRevokesExistingSessions() {
        UUID userId = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UserEntity user = UserEntity.builder()
                .username("customer")
                .email("customer@example.com")
                .passwordHash("old-hash")
                .roles(Set.of(RoleEntity.builder().name("ROLE_USER").build()))
                .build();
        user.setId(userId);
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
        when(encoder.matches("Current123!", "old-hash")).thenReturn(true);
        when(encoder.matches("NewPassword1!", "old-hash")).thenReturn(false);
        when(encoder.encode("NewPassword1!")).thenReturn("new-hash");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Current123!");
        request.setNewPassword("NewPassword1!");

        new UserService(users, roles, encoder, revocation).changePassword(userId, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(revocation).incrementTokenVersion(userId);
    }
}
