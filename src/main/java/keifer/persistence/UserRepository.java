package keifer.persistence;

import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.UserEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends CrudRepository<UserEntity, Long> {

    List<UserEntity> findAll();

    UserEntity findOneById(Long id);

    UserEntity findOneByUsername(String username);

}
