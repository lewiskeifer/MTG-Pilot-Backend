package keifer.service;

import keifer.api.model.Sealed;
import keifer.api.model.SealedCollection;
import keifer.converter.SealedCollectionConverter;
import keifer.converter.SealedConverter;
import keifer.persistence.*;
import keifer.persistence.model.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import javax.security.sasl.AuthenticationException;
import javax.servlet.ServletException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SealedServiceImpl implements SealedService {

    private final UserRepository userRepository;
    private final SealedCollectionRepository sealedCollectionRepository;
    private final SealedCollectionConverter sealedCollectionConverter;
    private final SealedRepository sealedRepository;
    private final SealedConverter sealedConverter;
    private final TcgService tcgService;
    private final TokenParsingServiceImpl tokenParsingServiceImpl;

    public SealedServiceImpl(@NonNull UserRepository userRepository,
                             @NonNull SealedCollectionRepository sealedCollectionRepository,
                             @NonNull SealedCollectionConverter sealedCollectionConverter,
                             @NonNull SealedRepository sealedRepository,
                             @NonNull SealedConverter sealedConverter,
                             @NonNull TcgService tcgService,
                             @NonNull TokenParsingServiceImpl tokenParsingServiceImpl) {
        this.userRepository = userRepository;
        this.sealedCollectionRepository = sealedCollectionRepository;
        this.sealedCollectionConverter = sealedCollectionConverter;
        this.sealedRepository = sealedRepository;
        this.sealedConverter = sealedConverter;
        this.tcgService = tcgService;
        this.tokenParsingServiceImpl = tokenParsingServiceImpl;
    }

    public List<SealedCollection> getSealedCollections(@PathVariable("userId") Long userId) {

        checkPermissions(userId);

        List<SealedCollection> sealedCollections = new ArrayList<>();
        sealedCollections.add(getSealedCollectionOverview(userId));
        sealedCollections.addAll(sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(userId).stream()
                .map(sealedCollectionConverter::convert).collect(Collectors.toList()));

        return sealedCollections;
    }

    public SealedCollection getSealedCollection(@PathVariable("userId") Long userId, @PathVariable("sealedId") Long sealedId) {

        return sealedCollectionConverter.convert(sealedCollectionRepository.findOneByUserEntityIdAndId(userId, sealedId));
    }

    public SealedCollection saveSealedCollection(Long userId, SealedCollection sealedCollection) throws ServletException {

        checkPermissions(userId);

        SealedCollectionEntity sealedCollectionEntity = null;

        // ID with val -1 == new deck
        if (sealedCollection.getId() == null || sealedCollection.getId() == -1) {
            UserEntity userEntity = userRepository.findOneById(userId);
            if (userEntity == null) {
                throw new ServletException("User with id " + userId + " does not exist.");
            }

            Integer sortOrder = sealedCollection.getSortOrder();
            if (sortOrder == null) {
                sortOrder = sealedCollectionRepository.findMaxSortOrder();
                if (sortOrder == null) {
                    sortOrder = 1;
                }
            }

            sealedCollectionEntity = SealedCollectionEntity.builder()
                    .name(sealedCollection.getName())
                    .userEntity(userEntity)
                    .sortOrder(sortOrder)
                    .build();
        } else {
            sealedCollectionEntity = fetchSealedCollectionEntity(userId, sealedCollection.getId());
            sealedCollectionEntity.setName(sealedCollection.getName());
            cascadeDeckOrdering(userId, sealedCollectionEntity.getSortOrder(), sealedCollection.getSortOrder());
            sealedCollectionEntity.setSortOrder(sealedCollection.getSortOrder());
        }

        return sealedCollectionConverter.convert(sealedCollectionRepository.save(sealedCollectionEntity));
    }

    public Sealed saveSealed(Long userId, Long deckId, Sealed sealed) {

        checkPermissions(userId);

        checkSealed(sealed);

        SealedCollectionEntity sealedCollectionEntity = fetchSealedCollectionEntity(userId, deckId);

        Map<String, String> results = tcgService.fetchProductIdAndUrl(sealed.getName());
        double marketPrice = tcgService.fetchMarketPriceByProductId(results.get("productId"));

        SealedEntity sealedEntity = SealedEntity.builder()
                .name(sealed.getName())
                .purchasePrice(sealed.getPurchasePrice())
                .quantity(sealed.getQuantity())
                .productId(results.get("productId"))
                .url(results.get("url"))
                .marketPrice(marketPrice)
                .sealedCollectionEntity(sealedCollectionEntity)
                .build();

        // Save new card
        if (sealed.getId() == null) {
            sealedCollectionEntity.getSealedEntities().add(sealedEntity);
            sealedCollectionRepository.save(sealedCollectionEntity);
        }
        // Update old card
        else {
            sealedEntity.setId(sealed.getId());
            sealedRepository.save(sealedEntity);
        }

        return sealedConverter.convert(sealedEntity);
    }

    public SealedCollection saveSealedCollectionOrdering(Long userId, Long sealedCollectionId, Integer order) {

        checkPermissions(userId);

        SealedCollectionEntity sealedCollectionEntity = fetchSealedCollectionEntity(userId, sealedCollectionId);
        sealedCollectionEntity.setSortOrder(order);

        return sealedCollectionConverter.convert(sealedCollectionRepository.save(sealedCollectionEntity));
    }

    public void createSealedCollectionSnapshot(Long userId, Long sealedCollectionId) {

        checkPermissions(userId);

        // Deck Overview
        if (sealedCollectionId == 0) {
            List<SealedCollectionEntity> sealedCollectionEntities = sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(userId);
            sealedCollectionEntities.parallelStream().forEach(this::updateDeckMarketPrice);

            return;
        }

        SealedCollectionEntity sealedCollectionEntity = fetchSealedCollectionEntity(userId, sealedCollectionId);
        double aggregatePurchasePrice = 0;
        double aggregateValue = 0;

        for (SealedEntity sealedEntity : sealedCollectionEntity.getSealedEntities()) {
            aggregatePurchasePrice += sealedEntity.getPurchasePrice();
            aggregateValue += (saveSealedEntity(sealedEntity) * sealedEntity.getQuantity());
        }

        saveSealedCollectionEntitySnapshot(sealedCollectionEntity, aggregatePurchasePrice, aggregateValue);
    }

    private void updateDeckMarketPrice(SealedCollectionEntity sealedCollectionEntity) {

        double aggregatePurchasePrice = 0;
        double aggregateValue = 0;
        for (SealedEntity sealedEntity : sealedCollectionEntity.getSealedEntities()) {
            aggregatePurchasePrice += sealedEntity.getPurchasePrice();
            aggregateValue += (saveSealedEntity(sealedEntity) * sealedEntity.getQuantity());
        }

        saveSealedCollectionEntitySnapshot(sealedCollectionEntity, aggregatePurchasePrice, aggregateValue);
    }

    @SneakyThrows
    private void checkPermissions(Long id) {

        if (!id.equals(tokenParsingServiceImpl.getUserId())) {
            throw new AuthenticationException("User is not authorized.");
        }
    }

    @SneakyThrows
    private void checkSealed(Sealed sealed) {

        if (sealed.getQuantity() < 1) {
            throw new ServletException("Quantity must be greater than 0.");
        }
        if (sealed.getPurchasePrice() <= 0) {
            throw new ServletException("Purchase Price must be greater than 0.");
        }
    }

    private SealedCollection getSealedCollectionOverview(Long userId) {

        checkPermissions(userId);

        SealedCollection sealedCollection = SealedCollection.builder().id(0L).name("Sealed Collection Overview").sealed(new ArrayList<>()).build();

        List<SealedCollection> decks = sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(userId).stream().map(sealedCollectionConverter::convert)
                .collect(Collectors.toList());

        long count = 0L;
        for (SealedCollection newSealedCollection : decks) {
            double deckValue = 0;
            double purchasePrice = 0;
            for (Sealed card : newSealedCollection.getSealed()) {
                deckValue += (card.getMarketPrice() * card.getQuantity());
                purchasePrice += card.getPurchasePrice();
            }

            sealedCollection.getSealed().add(Sealed.builder()
                    .id(count++)
                    .name(newSealedCollection.getName())
                    .purchasePrice(purchasePrice)
                    .quantity(1)
                    .url("")
                    .marketPrice(deckValue)
                    .build());
        }

        return sealedCollection;
    }

    private SealedCollectionEntity fetchSealedCollectionEntity(Long userId, Long deckId) {

        SealedCollectionEntity sealedCollectionEntity = sealedCollectionRepository.findOneByUserEntityIdAndId(userId, deckId);
        if (sealedCollectionEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        return sealedCollectionEntity;
    }

    private void cascadeDeckOrdering(Long userId, int oldOrder, int newOrder) {
        List<SealedCollectionEntity> sealedCollectionEntities = sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(userId);
        // Sift down
        if (newOrder > oldOrder) {
            for (int i = oldOrder; i < sealedCollectionEntities.size(); ++i) {
                SealedCollectionEntity sealedCollectionEntity = sealedCollectionEntities.get(i);
                sealedCollectionEntity.setSortOrder(i);
                sealedCollectionConverter.convert(sealedCollectionRepository.save(sealedCollectionEntity));
            }
        }
        // Sift up
        else if (oldOrder > newOrder) {
            for (int i = newOrder; i < oldOrder; ++i) {
                SealedCollectionEntity sealedCollectionEntity = sealedCollectionEntities.get(i - 1);
                sealedCollectionEntity.setSortOrder(i + 1);
                sealedCollectionConverter.convert(sealedCollectionRepository.save(sealedCollectionEntity));
            }
        }
    }

    private class SortByName implements Comparator<String> {
        public int compare(String a, String b) {
            return a.compareTo(b);
        }
    }

    private double saveSealedEntity(SealedEntity sealedEntity) {

        double newValue = tcgService.fetchMarketPriceByProductId(sealedEntity.getProductId());
        if (newValue != 0.0) {
            sealedEntity.setMarketPrice(newValue);
            sealedRepository.save(sealedEntity);
        }

        return sealedEntity.getMarketPrice();
    }

    // Fires at 8 AM every day
    @Scheduled(cron="0 0 8 * * *", zone="America/New_York")
    public void refreshAllSealedCollections() {

        System.out.println("Scheduled task running.");

        List<SealedCollectionEntity> sealedCollectionEntities = sealedCollectionRepository.findAll();
        for (SealedCollectionEntity sealedCollectionEntity : sealedCollectionEntities) {

            double aggregatePurchasePrice = 0;
            double aggregateValue = 0;
            for (SealedEntity sealedEntity : sealedCollectionEntity.getSealedEntities()) {
                aggregatePurchasePrice += sealedEntity.getPurchasePrice();
                aggregateValue += (saveSealedEntity(sealedEntity) * sealedEntity.getQuantity());
            }

            saveSealedCollectionEntitySnapshot(sealedCollectionEntity, aggregatePurchasePrice, aggregateValue);
        }
    }

    private void saveSealedCollectionEntitySnapshot(SealedCollectionEntity sealedCollectionEntity, double aggregatePurchasePrice, double aggregateValue) {

        LocalDateTime localDateTime = LocalDateTime.now();

        List<SealedCollectionSnapshotEntity> sealedCollectionSnapshotEntities = sealedCollectionEntity.getSealedCollectionSnapshotEntities();

        if (!sealedCollectionSnapshotEntities.isEmpty() && localDateTime.getDayOfYear() ==
                sealedCollectionSnapshotEntities.get(sealedCollectionEntity.getSealedCollectionSnapshotEntities().size() - 1).getTimestamp().getDayOfYear()) {

            System.out.println("Snapshot found for today, overwriting.");

            sealedCollectionEntity.getSealedCollectionSnapshotEntities().get(sealedCollectionSnapshotEntities.size() - 1).setPurchasePrice(aggregatePurchasePrice);
            sealedCollectionEntity.getSealedCollectionSnapshotEntities().get(sealedCollectionSnapshotEntities.size() - 1).setValue(aggregateValue);
        } else {
            sealedCollectionEntity.getSealedCollectionSnapshotEntities().add(SealedCollectionSnapshotEntity.builder()
                    .purchasePrice(aggregatePurchasePrice)
                    .value(aggregateValue)
                    .timestamp(LocalDateTime.now())
                    .sealedCollectionEntity(sealedCollectionEntity)
                    .build());
        }

        sealedCollectionRepository.save(sealedCollectionEntity);
    }
}
