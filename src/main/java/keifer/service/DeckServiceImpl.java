package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeckServiceImpl implements DeckService {

    private DeckRepository deckRepository;
    private DeckConverter deckConverter;
    private CardRepository cardRepository;
    private CardConverter cardConverter;
    private TcgService tcgService;

    public DeckServiceImpl(@NonNull DeckRepository deckRepository,
                           @NonNull DeckConverter deckConverter,
                           @NonNull CardRepository cardRepository,
                           @NonNull CardConverter cardConverter,
                           @NonNull TcgService tcgService) {
        this.deckRepository = deckRepository;
        this.deckConverter = deckConverter;
        this.cardRepository = cardRepository;
        this.cardConverter = cardConverter;
        this.tcgService = tcgService;
    }

    @Override
    public List<Deck> getDecks() {

        return deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList());
    }

    @Override
    public Deck getDeck(Long deckId) {

        DeckEntity deckEntity = deckRepository.findOneById(deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        return deckConverter.convert(deckEntity);
    }

    @Override
    public void saveCard(Long deckId, Card card) {

        DeckEntity deckEntity = deckRepository.findOneById(deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        String productConditionId = tcgService.fetchProductConditionId(card);
        double marketPrice = tcgService.fetchMarketPrice(productConditionId);

        CardEntity cardEntity = CardEntity.builder()
                .name(card.getName())
                .version(card.getVersion())
                .isFoil(card.getIsFoil())
                .cardCondition(card.getCardCondition())
                .purchasePrice(card.getPurchasePrice())
                .quantity(card.getQuantity())
                .productConditionId(productConditionId)
                .marketPrice(marketPrice)
                .deckEntity(deckEntity)
                .build();

        // Save new card
        if (card.getId() == null) {
            deckEntity.getCardEntities().add(cardEntity);
            deckRepository.save(deckEntity);
        }
        // Update old card
        else {
            cardEntity.setId(card.getId());
            cardRepository.save(cardEntity);
        }
    }

}
