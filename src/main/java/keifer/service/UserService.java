package keifer.service;

import keifer.api.model.Login;
import keifer.api.model.User;

import javax.servlet.ServletException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface UserService {

    List<User> getUsers();

    User getUser(Long userId);

    User login(Login login);

    User saveUser(User user);

    User resetPassword(User user);

    void deleteUser(Long userId);

}
