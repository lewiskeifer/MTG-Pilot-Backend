package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.UserRepository;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.*;
import keifer.service.model.CardCondition;
import keifer.service.model.DeckFormat;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.security.sasl.AuthenticationException;
import javax.servlet.ServletException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeckServiceImpl implements DeckService {

    private final UserRepository userRepository;
    private final DeckRepository deckRepository;
    private final VersionRepository versionRepository;
    private final DeckConverter deckConverter;
    private final CardRepository cardRepository;
    private final CardConverter cardConverter;
    private final TcgService tcgService;
    private final TokenParsingServiceImpl tokenParsingServiceImpl;

    public DeckServiceImpl(@NonNull UserRepository userRepository,
                           @NonNull DeckRepository deckRepository,
                           @NonNull VersionRepository versionRepository,
                           @NonNull DeckConverter deckConverter,
                           @NonNull CardRepository cardRepository,
                           @NonNull CardConverter cardConverter,
                           @NonNull TcgService tcgService,
                           @NonNull TokenParsingServiceImpl tokenParsingServiceImpl) {
        this.userRepository = userRepository;
        this.deckRepository = deckRepository;
        this.versionRepository = versionRepository;
        this.deckConverter = deckConverter;
        this.cardRepository = cardRepository;
        this.cardConverter = cardConverter;
        this.tcgService = tcgService;
        this.tokenParsingServiceImpl = tokenParsingServiceImpl;
    }

    @Override
    public List<Deck> getDecks() {

        return deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList());
    }

    @Override
    public List<Deck> getDecks(Long userId) {

        checkPermissions(userId);

        List<Deck> decks = new ArrayList<>();
        decks.add(getDeckOverview(userId));
        decks.addAll(deckRepository.findByUserEntityIdOrderBySortOrderAsc(userId).stream()
                .map(deckConverter::convert).collect(Collectors.toList()));

        return decks;
    }

    @Override
    public Deck getDeck(Long userId, Long deckId) {

        checkPermissions(userId);

        // Deck Overview
        if (deckId == 0) {
            return getDeckOverview(userId);
        }

        DeckEntity deckEntity = fetchDeck(userId, deckId);

        return deckConverter.convert(deckEntity);
    }

    @Override
    public List<String> getVersions() {
        List<String> versions = new ArrayList<>();
        versionRepository.findAll().forEach(versionEntity -> versions.add(versionEntity.getName()));
        Collections.sort(versions, new SortByName());
        return versions;
    }

    @Override
    public String getVersion(Integer groupId) {
        return versionRepository.findTopByGroupId(groupId).getName();
    }

    @Override
    public Card saveCard(Long userId, Long deckId, Card card) {

        checkPermissions(userId);

        checkCard(card);

        DeckEntity deckEntity = fetchDeck(userId, deckId);

        Integer groupId = versionRepository.findTopByName(card.getSet()).getGroupId();
        card.setGroupId(groupId);

        Map<String, String> results = tcgService.fetchProductConditionIdAndUrl(card);
        double marketPrice = tcgService.fetchMarketPrice(results.get("productConditionId"));

        CardEntity cardEntity = CardEntity.builder()
                .groupId(groupId)
                .name(card.getName())
                .version(card.getSet())
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
    public Deck saveDeck(Long userId, Deck deck) throws ServletException {

        checkPermissions(userId);

        DeckEntity deckEntity = null;

        // ID with val -1 == new deck
        if (deck.getId() == null || deck.getId() == -1) {
            UserEntity userEntity = userRepository.findOneById(userId);
            if (userEntity == null) {
                throw new ServletException("User with id " + userId + " does not exist.");
            }

            Integer sortOrder = deck.getSortOrder();
            if (sortOrder == null) {
                sortOrder = deckRepository.findMaxSortOrder();
                if (sortOrder == null) {
                    sortOrder = 1;
                }
            }

            deckEntity = DeckEntity.builder()
                    .name(deck.getName())
                    .deckFormat(DeckFormat.fromString(deck.getFormat()))
                    .userEntity(userEntity)
                    .sortOrder(sortOrder)
                    .build();
        } else {
            deckEntity = fetchDeck(userId, deck.getId());
            deckEntity.setName(deck.getName());
            deckEntity.setDeckFormat(DeckFormat.fromString(deck.getFormat()));
            cascadeDeckOrdering(userId, deckEntity.getSortOrder(), deck.getSortOrder());
            deckEntity.setSortOrder(deck.getSortOrder());
        }

        return deckConverter.convert(deckRepository.save(deckEntity));
    }

    @Deprecated
    @Override
    public Deck saveDeckOrdering(Long userId, Long deckId, Integer order) {

        checkPermissions(userId);

        DeckEntity deckEntity = fetchDeck(userId, deckId);
        deckEntity.setSortOrder(order);

        return deckConverter.convert(deckRepository.save(deckEntity));
    }

    @Override
    public void createDeckSnapshot(Long userId, Long deckId) {

        checkPermissions(userId);

        // Deck Overview
        if (deckId == 0) {
            List<DeckEntity> deckEntities = deckRepository.findByUserEntityIdOrderBySortOrderAsc(userId);
            deckEntities.parallelStream().forEach(this::updateDeckMarketPrice);

            return;
        }

        DeckEntity deckEntity = fetchDeck(userId, deckId);
        double aggregatePurchasePrice = 0;
        double aggregateValue = 0;

        for (CardEntity cardEntity : deckEntity.getCardEntities()) {
            aggregatePurchasePrice += cardEntity.getPurchasePrice();
            aggregateValue += (saveCardEntity(cardEntity) * cardEntity.getQuantity());
        }

        saveDeckEntitySnapshot(deckEntity, aggregatePurchasePrice, aggregateValue);
    }

    @Override
    public void deleteCard(Long userId, Long deckId, Long cardId) {

        checkPermissions(userId);

        DeckEntity deckEntity = fetchDeck(userId, deckId);
        int count = 0;
        for (CardEntity cardEntity : deckEntity.getCardEntities()) {
            if (cardEntity.getId().equals(cardId)) {
                deckEntity.getCardEntities().remove(count);
                break;
            }
            count++;
        }
        deckRepository.save(deckEntity);

        CardEntity cardEntity = cardRepository.findOneById(cardId);

        // TODO check if necessary
        cardRepository.delete(cardEntity);
    }

    @Override
    public void deleteDeck(Long userId, Long deckId) {

        checkPermissions(userId);

        DeckEntity deckEntity = fetchDeck(userId, deckId);

        cascadeDeckOrdering(userId, deckEntity.getSortOrder(), deckRepository.findMaxSortOrder());

        deckRepository.delete(deckEntity);
    }

    private Deck getDeckOverview(Long userId) {

        checkPermissions(userId);

        Deck deck = Deck.builder().id(0L).name("Deck Overview").cards(new ArrayList<>()).build();

        List<Deck> decks = deckRepository.findByUserEntityIdOrderBySortOrderAsc(userId).stream().map(deckConverter::convert)
                .collect(Collectors.toList());

        long count = 0L;
        for (Deck newDeck : decks) {
            double deckValue = 0;
            double purchasePrice = 0;
            for (Card card : newDeck.getCards()) {
                deckValue += (card.getMarketPrice() * card.getQuantity());
                purchasePrice += card.getPurchasePrice();
            }

            deck.getCards().add(Card.builder()
                    .id(count++)
                    .name(newDeck.getName())
                    .set("")
                    .purchasePrice(purchasePrice)
                    .quantity(1)
                    .url("")
                    .marketPrice(deckValue)
                    .build());
        }

        return deck;
    }

    private DeckEntity fetchDeck(Long userId, Long deckId) {

        DeckEntity deckEntity = deckRepository.findOneByUserEntityIdAndId(userId, deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        return deckEntity;
    }

    private void updateDeckMarketPrice(DeckEntity deckEntity) {

        double aggregatePurchasePrice = 0;
        double aggregateValue = 0;
        for (CardEntity cardEntity : deckEntity.getCardEntities()) {
            aggregatePurchasePrice += cardEntity.getPurchasePrice();
            aggregateValue += (saveCardEntity(cardEntity) * cardEntity.getQuantity());
        }

        saveDeckEntitySnapshot(deckEntity, aggregatePurchasePrice, aggregateValue);
    }

    private double saveCardEntity(CardEntity cardEntity) {

        double newValue = tcgService.fetchMarketPrice(cardEntity.getProductConditionId());
        if (newValue != 0.0) {
            cardEntity.setMarketPrice(newValue);
            cardRepository.save(cardEntity);
        }

        return cardEntity.getMarketPrice();
    }

    private void saveDeckEntitySnapshot(DeckEntity deckEntity, double aggregatePurchasePrice, double aggregateValue) {

        LocalDateTime localDateTime = LocalDateTime.now();

        List<DeckSnapshotEntity> deckSnapshotEntities = deckEntity.getDeckSnapshotEntities();

        if (!deckSnapshotEntities.isEmpty() && localDateTime.getDayOfYear() ==
                deckSnapshotEntities.get(deckEntity.getDeckSnapshotEntities().size() - 1).getTimestamp().getDayOfYear()) {

            System.out.println("Snapshot found for today, overwriting.");

            deckEntity.getDeckSnapshotEntities().get(deckSnapshotEntities.size() - 1).setPurchasePrice(aggregatePurchasePrice);
            deckEntity.getDeckSnapshotEntities().get(deckSnapshotEntities.size() - 1).setValue(aggregateValue);
        } else {
            deckEntity.getDeckSnapshotEntities().add(DeckSnapshotEntity.builder()
                    .purchasePrice(aggregatePurchasePrice)
                    .value(aggregateValue)
                    .timestamp(LocalDateTime.now())
                    .deckEntity(deckEntity)
                    .build());
        }

        deckRepository.save(deckEntity);
    }

    private void cascadeDeckOrdering(Long userId, int oldOrder, int newOrder) {
        List<DeckEntity> decks = deckRepository.findByUserEntityIdOrderBySortOrderAsc(userId);
        // Sift down
        if (newOrder > oldOrder) {
            for (int i = oldOrder; i < decks.size(); ++i) {
                DeckEntity deckEntity = decks.get(i);
                deckEntity.setSortOrder(i);
                deckConverter.convert(deckRepository.save(deckEntity));
            }
        }
        // Sift up
        else if (oldOrder > newOrder) {
            for (int i = newOrder; i < oldOrder; ++i) {
                DeckEntity deckEntity = decks.get(i - 1);
                deckEntity.setSortOrder(i + 1);
                deckConverter.convert(deckRepository.save(deckEntity));
            }
        }
    }

    private class SortByName implements Comparator<String> {
        public int compare(String a, String b) {
            return a.compareTo(b);
        }
    }

    @SneakyThrows
    private void checkPermissions(Long id) {

        if (!id.equals(tokenParsingServiceImpl.getUserId())) {
            throw new AuthenticationException("User is not authorized.");
        }
    }

    @SneakyThrows
    private void checkCard(Card card) {

        if (card.getQuantity() < 1) {
            throw new ServletException("Quantity must be greater than 0.");
        }
        if (card.getPurchasePrice() <= 0) {
            throw new ServletException("Purchase Price must be greater than 0.");
        }
    }

    // Fires at 4 AM every day
    @Scheduled(cron="0 0 4 * * *", zone="America/New_York")
    @Override
    public void refreshAllDecks() {

        System.out.println("Scheduled task running.");

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
    }
}
