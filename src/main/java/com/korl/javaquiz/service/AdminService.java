package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.AppUser;
import com.korl.javaquiz.domain.AppUserRepository;
import com.korl.javaquiz.domain.Role;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final AppUserRepository users;
    private final UserStatsRepository stats;

    public AdminService(AppUserRepository users, UserStatsRepository stats) {
        this.users = users;
        this.stats = stats;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AppUser user : users.findAll()) {
            UserStatsEntity statsEntity = stats.findById(user.getId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", UserDto.from(user));
            row.put("createdAt", user.getCreatedAt());
            row.put("authProvider", user.getAuthProvider().name());
            row.put("totalAnswered", statsEntity == null ? 0 : statsEntity.getTotalAnswered());
            row.put("totalCorrect", statsEntity == null ? 0 : statsEntity.getTotalCorrect());
            result.add(row);
        }
        return result;
    }

    @Transactional
    public UserDto changeRole(UUID userId, Role role) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        // There is no way back from an admin-less instance: nobody could grant the role again.
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot demote the last remaining admin");
        }
        user.setRole(role);
        users.save(user);
        return UserDto.from(user);
    }
}
