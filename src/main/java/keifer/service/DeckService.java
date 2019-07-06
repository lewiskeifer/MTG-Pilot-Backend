package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;

import javax.servlet.ServletException;
import java.util.List;

public interface DeckService {

    List<Deck> getDecks();

    List<Deck> getDecks(Long userId);

    Deck getDeck(Long userId, Long deckId);

    List<String> getVersions();String

    getVersion(Integer groupId);

    Card saveCard(Long userId, Long deckId, Card card);

    Deck saveDeck(Long userId, Deck deck) throws ServletException;

    void createDeckSnapshot(Long userId, Long deckId);

    void deleteCard(Long userId, Long deckId, Long cardId);

    void deleteDeck(Long userId, Long deckId);

    void refreshAllDecks();

}
