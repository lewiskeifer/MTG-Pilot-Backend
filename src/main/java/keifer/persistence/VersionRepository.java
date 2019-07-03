package keifer.persistence;

import keifer.persistence.model.VersionEntity;
import org.springframework.data.repository.CrudRepository;

public interface VersionRepository extends CrudRepository<VersionEntity, Long> {

    VersionEntity findOneByGroupId(Integer groupId);

    VersionEntity findOneByName(String name);

}
