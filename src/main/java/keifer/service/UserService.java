package keifer.service;

import keifer.api.model.Login;
import keifer.api.model.User;

import javax.servlet.ServletException;
import java.util.List;

public interface UserService {

    User login(Login login) throws ServletException;

    List<User> getUsers();

    User getUser(Long userId);

    User saveUser(User user) throws ServletException;

    void deleteUser(Long userId);

}
