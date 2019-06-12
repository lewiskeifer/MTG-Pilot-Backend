package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.User;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<User> getUsers();

    User getUser(Long userId);

    User saveUser(User user);

    void deleteUser(Long userId);

}
