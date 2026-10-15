package keifer.persistence;

import keifer.persistence.model.ConditionMultiplierEntity;
import keifer.service.model.CardCondition;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ConditionMultiplierRepository extends CrudRepository<ConditionMultiplierEntity, Long> {

    List<ConditionMultiplierEntity> findAll();

    ConditionMultiplierEntity findOneByCardConditionAndFoil(CardCondition cardCondition, Boolean foil);

}
