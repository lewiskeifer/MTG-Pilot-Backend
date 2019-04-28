package keifer.persistence;

import keifer.persistence.model.CardEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CardRepository extends CrudRepository<CardEntity, Long> {

    List<CardEntity> findAll();

}
