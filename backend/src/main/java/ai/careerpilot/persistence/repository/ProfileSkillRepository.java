package ai.careerpilot.persistence.repository;
import ai.careerpilot.persistence.entity.ProfileSkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProfileSkillRepository extends JpaRepository<ProfileSkillEntity,UUID>{
 List<ProfileSkillEntity> findAllByUserIdOrderByNormalizedSkillNameAsc(UUID userId);
 Optional<ProfileSkillEntity> findByIdAndUserId(UUID id,UUID userId);
 boolean existsByUserIdAndNormalizedSkillName(UUID userId,String normalizedSkillName);
 long deleteByIdAndUserId(UUID id,UUID userId);
}