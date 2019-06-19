package keifer.controller;

import io.jsonwebtoken.Jwts;
import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.Login;
import keifer.api.model.User;
import keifer.service.UserService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import java.util.List;


@Api
@RequestMapping
@RestController
public class LoginController {

    private final UserService userService;

    public LoginController(@NonNull UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public void register(@RequestBody User user) throws ServletException {
        userService.saveUser(user);
    }

    @PostMapping("/login")
    public User login(@RequestBody Login login) throws ServletException {
        return userService.login(login);
    }
}
