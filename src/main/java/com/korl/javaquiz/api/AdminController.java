package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.domain.Role;
import com.korl.javaquiz.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return adminService.listUsers();
    }

    @PatchMapping("/users/{id}/role")
    public UserDto changeRole(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Role role = Role.valueOf(body.get("role"));
        return adminService.changeRole(id, role);
    }
}
