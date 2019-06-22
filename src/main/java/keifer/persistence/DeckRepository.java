package keifer.persistence;

import keifer.persistence.model.DeckEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DeckRepository extends CrudRepository<DeckEntity, Long> {

    List<DeckEntity> findAll();

    DeckEntity findOneById(Long id);

    List<DeckEntity> findByUserEntityId(Long userEntityId);

}
