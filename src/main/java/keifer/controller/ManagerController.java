package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.service.DataMigrationService;
import keifer.service.DeckService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api
@RequestMapping("/manager")
@RestController
public class ManagerController {

    private final DeckService deckService;
    private final DataMigrationService dataMigrationService;

    public ManagerController(@NonNull DeckService deckService, @NonNull DataMigrationService dataMigrationService) {
        this.deckService = deckService;
        this.dataMigrationService = dataMigrationService;
    }

    @GetMapping("/decks")
    public List<Deck> getDecks() {
        return deckService.getDecks();
    }

    @GetMapping("/decks/{deckId}")
    public Deck getDeck(@PathVariable("deckId") Long deckId) {
        return deckService.getDeck(deckId);
    }

    @PutMapping("/decks/{deckId}")
    public void addCardToDeck(@PathVariable("deckId") Long deckId, @RequestBody Card card) {
        deckService.addCardToDeck(deckId, card);
    }

    @GetMapping("/migrate")
    public void migrate() {
        dataMigrationService.migrateData();
    }

}
