package keifer.persistence;

import keifer.persistence.model.CardEntity;
import keifer.persistence.model.SealedEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SealedRepository extends CrudRepository<SealedEntity, Long> {

    List<SealedEntity> findAll();

    SealedEntity findOneById(Long id);

}
