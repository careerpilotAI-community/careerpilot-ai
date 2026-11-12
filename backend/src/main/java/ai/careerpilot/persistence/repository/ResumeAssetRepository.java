package ai.careerpilot.persistence.repository;

import ai.careerpilot.persistence.entity.ResumeAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeAssetRepository extends JpaRepository<ResumeAssetEntity, UUID> {
    List<ResumeAssetEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ResumeAssetEntity> findByIdAndUserId(UUID id, UUID userId);
    long sumSizeBytesByUserId(UUID userId);
}
