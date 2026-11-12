package ai.careerpilot.persistence.repository;
import ai.careerpilot.persistence.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface JobRepository extends JpaRepository<JobEntity,UUID>{
 List<JobEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
 Optional<JobEntity> findByIdAndUserId(UUID id,UUID userId);
 long deleteByIdAndUserId(UUID id,UUID userId);
 long countByUserId(UUID userId);
}