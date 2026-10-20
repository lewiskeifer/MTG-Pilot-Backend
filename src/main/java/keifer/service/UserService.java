package keifer.service;

import keifer.api.model.Login;
import keifer.api.model.PasswordChange;
import keifer.api.model.User;

import javax.servlet.ServletException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface UserService {

    List<User> getUsers();

    User getUser(Long userId);

    User login(Login login);

    User saveUser(User user);

    /** Changes a password, given the current one. */
    User resetPassword(PasswordChange passwordChange);

    void deleteUser(Long userId);

}
