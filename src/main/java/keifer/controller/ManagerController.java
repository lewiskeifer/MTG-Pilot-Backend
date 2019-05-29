package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.service.DataMigrationService;
import keifer.service.DeckService;
import keifer.service.TcgService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api
@RequestMapping("/manager")
@RestController
public class ManagerController {

    private final DeckService deckService;
    private final DataMigrationService dataMigrationService;
    private final TcgService tcgService;

    public ManagerController(@NonNull DeckService deckService,
                             @NonNull DataMigrationService dataMigrationService,
                             @NonNull TcgService tcgService) {
        this.deckService = deckService;
        this.dataMigrationService = dataMigrationService;
        this.tcgService = tcgService;
    }

    @GetMapping("/decks")
    public List<Deck> getDecks() {
        return deckService.getDecks();
    }

    @GetMapping("/decks/{deckId}")
    public Deck getDeck(@PathVariable("deckId") Long deckId) {
        return deckService.getDeck(deckId);
    }

    @PutMapping("/decks/{deckId}/cards")
    public Card saveCard(@PathVariable("deckId") Long deckId, @RequestBody Card card) {
        return deckService.saveCard(deckId, card);
    }

    @PutMapping("/decks")
    public void saveDeck(@RequestBody Deck deck) {
        deckService.saveDeck(deck);
    }

    @PutMapping("/decks/{deckId}/refresh")
    public void refreshDeck(@PathVariable("deckId") Long deckId) {
        deckService.refreshDeck(deckId);
    }

    @DeleteMapping("/decks/{deckId}/cards/{cardId}")
    public void deleteCard(@PathVariable("deckId") Long deckId, @PathVariable("cardId") Long cardId) {
        deckService.deleteCard(cardId);
    }

    @DeleteMapping("/decks/{deckId}")
    public void deleteDeck(@PathVariable("deckId") Long deckId) {
        deckService.deleteDeck(deckId);
    }

    @GetMapping("/migrate")
    public void migrate() {
        dataMigrationService.migrateData();
    }

}
