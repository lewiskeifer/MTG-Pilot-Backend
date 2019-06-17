package keifer.service;

import io.jsonwebtoken.Jwts;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.Login;
import keifer.api.model.User;

import javax.servlet.ServletException;
import java.util.List;
import java.util.UUID;

public interface UserService {

    User login(Login login) throws ServletException;

    List<User> getUsers();

    User getUser(Long userId);

    User saveUser(User user);

    void deleteUser(Long userId);

}
