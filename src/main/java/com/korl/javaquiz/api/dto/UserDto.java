package com.korl.javaquiz.api.dto;

import com.korl.javaquiz.domain.AppUser;
import com.korl.javaquiz.domain.Role;

import java.util.UUID;

public class UserDto {

    public UUID id;
    public String email;
    public String displayName;
    public Role role;

    public static UserDto from(AppUser user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.displayName = user.getDisplayName();
        dto.role = user.getRole();
        return dto;
    }
}
