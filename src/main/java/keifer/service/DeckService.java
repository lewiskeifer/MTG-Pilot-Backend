package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;

import java.util.List;

public interface DeckService {

    List<Deck> getDecks(Long userId);

    Deck getDeck(Long userId, Long deckId);

    Card saveCard(Long userId, Long deckId, Card card);

    void saveDeck(Long userId, Deck deck) throws Exception;

    void refreshDeck(Long userId, Long deckId);

    void deleteCard(Long userId, Long cardId);

    void deleteDeck(Long userId, Long deckId);

}
