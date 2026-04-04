package keifer.persistence;

import keifer.persistence.model.VersionEntity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

public interface VersionRepository extends CrudRepository<VersionEntity, Long> {

    @Cacheable("versionsByGroupId")
    VersionEntity findTopByGroupId(Integer groupId);

    VersionEntity findTopByName(String name);

}
