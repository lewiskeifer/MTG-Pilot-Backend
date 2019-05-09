package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
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
    private CardConverter cardConverter;

    public DeckServiceImpl(@NonNull DeckRepository deckRepository,
                           @NonNull DeckConverter deckConverter,
                           @NonNull CardConverter cardConverter) {
        this.deckRepository = deckRepository;
        this.deckConverter = deckConverter;
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
    public void addCardToDeck(Long deckId, Card card) {

        DeckEntity deckEntity = deckRepository.findOneById(deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        CardEntity cardEntity = CardEntity.builder()
                .name(card.getName())
                .version(card.getVersion())
                .isFoil(card.getIsFoil())
                .cardCondition(card.getCardCondition())
                .purchasePrice(card.getPurchasePrice())
                .value(card.getValue()) // TODO set properly
                .quantity(card.getQuantity())
                .deckEntity(deckEntity)
                .build();

        deckEntity.getCardEntities().add(cardEntity);
        deckRepository.save(deckEntity);
    }

}
