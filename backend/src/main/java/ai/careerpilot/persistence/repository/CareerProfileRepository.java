package ai.careerpilot.persistence.repository;

import ai.careerpilot.persistence.entity.CareerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CareerProfileRepository extends JpaRepository<CareerProfileEntity, UUID> {
    Optional<CareerProfileEntity> findByUserId(UUID userId);
}
