package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.User;
import keifer.service.DataMigrationService;
import keifer.service.DeckService;
import keifer.service.TcgService;
import keifer.service.UserService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import java.util.List;

@Api
@RequestMapping("/manager/users")
@RestController
public class ManagerController {

    private final UserService userService;
    private final DeckService deckService;
    private final DataMigrationService dataMigrationService;
    private final TcgService tcgService;

    public ManagerController(@NonNull UserService userService,
                             @NonNull DeckService deckService,
                             @NonNull DataMigrationService dataMigrationService,
                             @NonNull TcgService tcgService) {
        this.userService = userService;
        this.deckService = deckService;
        this.dataMigrationService = dataMigrationService;
        this.tcgService = tcgService;
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
    public User saveUser(@RequestBody User user) throws ServletException {
        return userService.saveUser(user);
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(@PathVariable("userId") Long userId) {
        userService.deleteUser(userId);
    }


    @GetMapping("/decks")
    public List<Deck> getAllDecks() {
        return deckService.getDecks();
    }

    @GetMapping("/{userId}/decks")
    public List<Deck> getDecks(@PathVariable("userId") Long userId) {
        return deckService.getDecks(userId);
    }

    @GetMapping("/{userId}/decks/{deckId}")
    public Deck getDeck(@PathVariable("userId") Long userId,
                        @PathVariable("deckId") Long deckId) {
        return deckService.getDeck(userId, deckId);
    }

    @PutMapping("/{userId}/decks/{deckId}/cards")
    public Card saveCard(@PathVariable("userId") Long userId,
                         @PathVariable("deckId") Long deckId,
                         @RequestBody Card card) {
        return deckService.saveCard(userId, deckId, card);
    }

    @PutMapping("/{userId}/decks")
    public void saveDeck(@PathVariable("userId") Long userId,
                         @RequestBody Deck deck) throws ServletException {
        deckService.saveDeck(userId, deck);
    }

    @PutMapping("/{userId}/decks/{deckId}/refresh")
    public void refreshDeck(@PathVariable("userId") Long userId,
                            @PathVariable("deckId") Long deckId) {
        deckService.refreshDeck(userId, deckId);
    }

    @DeleteMapping("/{userId}/decks/{deckId}/cards/{cardId}")
    public void deleteCard(@PathVariable("userId") Long userId,
                           @PathVariable("deckId") Long deckId,
                           @PathVariable("cardId") Long cardId) {
        deckService.deleteCard(userId, cardId);
    }

    @DeleteMapping("/{userId}/decks/{deckId}")
    public void deleteDeck(@PathVariable("userId") Long userId,
                           @PathVariable("deckId") Long deckId) {
        deckService.deleteDeck(userId, deckId);
    }


    @GetMapping("/migrateText")
    public void migrateText() {
        dataMigrationService.migrateTextData();
    }

    @GetMapping("/migrateJson")
    public void migrateJson() {
        dataMigrationService.migrateJsonData();
    }

    @GetMapping("/migrateSql")
    public void migrateSql() {
        dataMigrationService.migrateSqlData();
    }

}
