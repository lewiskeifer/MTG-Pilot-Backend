package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.Sealed;
import keifer.api.model.SealedCollection;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.ServletException;
import java.util.List;
import java.util.Map;

public interface SealedService {

    List<SealedCollection> getSealedCollections(Long userId);

    SealedCollection getSealedCollection(Long userId, Long sealedId);

    SealedCollection saveSealedCollection(Long userId, SealedCollection sealedCollection) throws ServletException;

    Sealed saveSealed(Long userId, Long deckId, Sealed sealed);

    SealedCollection saveSealedCollectionOrdering(Long userId, Long sealedCollectionId, Integer order);

    void createSealedCollectionSnapshot(Long userId, Long deckId);

    void refreshAllSealedCollections();
}
