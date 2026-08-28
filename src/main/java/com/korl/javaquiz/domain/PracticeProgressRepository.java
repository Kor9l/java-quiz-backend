package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeProgressRepository
        extends JpaRepository<PracticeProgressEntity, PracticeProgressEntity.Id> {

    List<PracticeProgressEntity> findByIdUserId(UUID userId);
}
