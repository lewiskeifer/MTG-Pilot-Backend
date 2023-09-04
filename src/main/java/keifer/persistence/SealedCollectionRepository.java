package keifer.persistence;

import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.SealedCollectionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SealedCollectionRepository extends CrudRepository<SealedCollectionEntity, Long> {

    List<SealedCollectionEntity> findAll();

    SealedCollectionEntity findOneByUserEntityIdAndId(Long userEntityId, Long id);

    List<SealedCollectionEntity> findByUserEntityIdOrderBySortOrderAsc(Long userEntityId);

    @Query(value = "SELECT MAX(sort_order) from mtg.sealed_collection_entity;", nativeQuery = true)
    Integer findMaxSortOrder();

}
