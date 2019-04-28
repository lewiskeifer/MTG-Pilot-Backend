package keifer.controller;

import io.swagger.annotations.Api;
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

    // Enable frontend consumer
    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/decks")
    public List<Deck> getDecks() {
        return deckService.getDecks();
    }

    // Enable frontend consumer
    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/decks/{deckId}")
    public Deck getDeck(@PathVariable("deckId") Long deckId) {
        return deckService.getDeck(deckId);
    }

    @GetMapping("/migrate")
    public void migrate() {
        dataMigrationService.migrateData();
    }

}
