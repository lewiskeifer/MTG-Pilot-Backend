package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.DeckSnapshotEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.DeckFormat;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        List<Deck> decks = new ArrayList<>();
        decks.add(getDeckOverview());
        decks.addAll(deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList()));

        return decks;
    }

    @Override
    public Deck getDeck(Long deckId) {

        // Deck Overview
        if (deckId == 0) {
            return getDeckOverview();
        }

        DeckEntity deckEntity = fetchDeck(deckId);

        return deckConverter.convert(deckEntity);
    }

    private Deck getDeckOverview() {

        Deck deck = Deck.builder().id(0L).name("Deck Overview").cards(new ArrayList<>()).build();

        List<Deck> decks = deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList());

        long count = 0L;
        for (Deck newDeck : decks) {
            double deckValue = 0;
            for (Card card : newDeck.getCards()) {
                deckValue += (card.getMarketPrice() * card.getQuantity());
            }

            deck.getCards().add(Card.builder()
                    .id(count++)
                    .name(newDeck.getName())
                    .version("")
                    .purchasePrice(0.0)
                    .quantity(1)
                    .url("")
                    .marketPrice(deckValue)
                    .build());
        }

        return deck;
    }

    // TODO null checking on incoming object

    @Override
    public Card saveCard(Long deckId, Card card) {

        DeckEntity deckEntity = fetchDeck(deckId);

        Map<String, String> results = tcgService.fetchProductConditionIdAndUrl(card);
        double marketPrice = tcgService.fetchMarketPrice(results.get("productConditionId"));

        CardEntity cardEntity = CardEntity.builder()
                .name(card.getName())
                .version(card.getVersion())
                .isFoil(card.getIsFoil())
                .cardCondition(CardCondition.fromString(card.getCardCondition()))
                .purchasePrice(card.getPurchasePrice())
                .quantity(card.getQuantity())
                .productConditionId(results.get("productConditionId"))
                .url(results.get("image"))
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

        return cardConverter.convert(cardEntity);
    }

    @Override
    public void saveDeck(Deck deck) {

        DeckEntity deckEntity = null;

        if (deck.getId() == null) {
            deckEntity = DeckEntity.builder()
                    .name(deck.getName())
                    .deckFormat(DeckFormat.fromString("Casual")) //TODO
                    .build();
        }
        else {
            deckEntity = fetchDeck(deck.getId());
            deckEntity.setName(deck.getName());
        }

        deckRepository.save(deckEntity);
    }

    @Override
    public void refreshDeck(Long deckId) {

        // Deck Overview
        if (deckId == 0) {
            List<DeckEntity> deckEntities = deckRepository.findAll();
            for (DeckEntity deckEntity : deckEntities) {
                double aggregatePurchasePrice = 0;
                double aggregateValue = 0;
                for (CardEntity cardEntity : deckEntity.getCardEntities()) {
                    aggregatePurchasePrice += cardEntity.getPurchasePrice();
                    aggregateValue += (saveCardEntity(cardEntity) * cardEntity.getQuantity());
                }

                saveDeckEntitySnapshot(deckEntity, aggregatePurchasePrice, aggregateValue);
            }

            return;
        }

        DeckEntity deckEntity = fetchDeck(deckId);
        double aggregatePurchasePrice = 0;
        double aggregateValue = 0;

        for (CardEntity cardEntity : deckEntity.getCardEntities()) {
            aggregatePurchasePrice += cardEntity.getPurchasePrice();
            aggregateValue += (saveCardEntity(cardEntity) * cardEntity.getQuantity());
        }

        saveDeckEntitySnapshot(deckEntity, aggregatePurchasePrice, aggregateValue);
    }

    @Override
    public void deleteCard(Long cardId) {

        cardRepository.deleteById(cardId);
    }

    @Override
    public void deleteDeck(Long deckId) {

        DeckEntity deckEntity = fetchDeck(deckId);

        deckRepository.delete(deckEntity);
    }

    private DeckEntity fetchDeck(Long deckId) {

        DeckEntity deckEntity = deckRepository.findOneById(deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        return deckEntity;
    }

    private double saveCardEntity(CardEntity cardEntity) {

        cardEntity.setMarketPrice(tcgService.fetchMarketPrice(cardEntity.getProductConditionId()));
        cardRepository.save(cardEntity);

        return cardEntity.getMarketPrice();
    }

    private void saveDeckEntitySnapshot(DeckEntity deckEntity, double aggregatePurchasePrice, double aggregateValue) {

        LocalDateTime localDateTime = LocalDateTime.now();

        List <DeckSnapshotEntity> deckSnapshotEntities = deckEntity.getDeckSnapshotEntities();

        if (localDateTime.getDayOfYear() ==
                deckSnapshotEntities.get(deckEntity.getDeckSnapshotEntities().size() - 1).getTimestamp().getDayOfYear()) {

            System.out.println("Snapshot found for today, overwriting.");

            deckEntity.getDeckSnapshotEntities().get(deckSnapshotEntities.size() - 1).setPurchasePrice(aggregatePurchasePrice);
            deckEntity.getDeckSnapshotEntities().get(deckSnapshotEntities.size() - 1).setValue(aggregateValue);
        }
        else {
            deckEntity.getDeckSnapshotEntities().add(DeckSnapshotEntity.builder()
                    .purchasePrice(aggregatePurchasePrice)
                    .value(aggregateValue)
                    .timestamp(LocalDateTime.now())
                    .deckEntity(deckEntity)
                    .build());
        }

        deckRepository.save(deckEntity);
    }
}
