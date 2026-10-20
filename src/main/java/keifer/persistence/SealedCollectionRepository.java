package keifer.persistence;

import keifer.persistence.model.SealedCollectionEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SealedCollectionRepository extends CrudRepository<SealedCollectionEntity, Long> {

    List<SealedCollectionEntity> findAll();

    SealedCollectionEntity findOneByUserEntityIdAndId(Long userEntityId, Long id);

    List<SealedCollectionEntity> findByUserEntityIdOrderBySortOrderAsc(Long userEntityId);

}
