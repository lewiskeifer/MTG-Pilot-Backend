package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.User;
import keifer.service.UserService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api
@RequestMapping("/users")
@RestController
public class UsersController {

    private final UserService userService;

    public UsersController(@NonNull UserService userService) {
        this.userService = userService;
    }

    @GetMapping()
    public List<User> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/{userId}")
    public User getUser(@PathVariable("userId") Long userId) {
        return userService.getUser(userId);
    }

    @PutMapping()
    public User saveUser(@RequestBody User user) {
        return userService.saveUser(user);
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(@PathVariable("userId") Long userId) {
        userService.deleteUser(userId);
    }


}
