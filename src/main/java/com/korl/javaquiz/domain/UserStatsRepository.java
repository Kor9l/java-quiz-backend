package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStatsEntity, UUID> {
}
