package keifer.persistence;

import keifer.persistence.model.VersionEntity;
import org.springframework.data.repository.CrudRepository;

public interface VersionRepository extends CrudRepository<VersionEntity, Long> {

    VersionEntity findTopByGroupId(Integer groupId);

    VersionEntity findTopByName(String name);

}
