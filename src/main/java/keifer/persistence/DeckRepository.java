package keifer.persistence;

import keifer.persistence.model.DeckEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DeckRepository extends CrudRepository<DeckEntity, Long> {

    List<DeckEntity> findAll();

    DeckEntity findOneByUserEntityIdAndId(Long userEntityId, Long id);

    List<DeckEntity> findByUserEntityIdOrderBySortOrderAsc(Long userEntityId);

    @Query(value = "SELECT MAX(sort_order) from mtg.deck_entity;", nativeQuery = true)
    int findMaxSortOrder();

}
