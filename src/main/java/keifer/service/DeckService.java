package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;

import java.util.List;

public interface DeckService {

    List<Deck> getDecks();

    Deck getDeck(Long deckId);

    Card saveCard(Long deckId, Card card);

    void saveDeck(Deck deck);

    void refreshDeck(Long deckId);

    void deleteCard(Long cardId);

    void deleteDeck(Long deckId);

}
