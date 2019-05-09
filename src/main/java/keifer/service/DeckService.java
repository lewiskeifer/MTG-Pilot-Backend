package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;

import java.util.List;

public interface DeckService {

    List<Deck> getDecks();

    Deck getDeck(Long deckId);

    void addCardToDeck(Long deckId, Card card);

}
