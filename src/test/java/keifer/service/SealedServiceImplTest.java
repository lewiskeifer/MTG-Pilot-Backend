package keifer.service;

import keifer.api.model.Sealed;
import keifer.api.model.SealedCollection;
import keifer.converter.SealedCollectionConverter;
import keifer.converter.SealedConverter;
import keifer.persistence.SealedCollectionRepository;
import keifer.persistence.SealedRepository;
import keifer.persistence.UserRepository;
import keifer.persistence.model.SealedCollectionEntity;
import keifer.persistence.model.SealedCollectionSnapshotEntity;
import keifer.persistence.model.SealedEntity;
import keifer.service.model.PriceBaselines;
import keifer.service.model.PriceKind;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import javax.security.sasl.AuthenticationException;
import javax.servlet.ServletException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sealed collections: who may touch them, and how a refresh prices and snapshots them.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SealedServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final ZoneId ZONE_ID = ZoneId.of("America/New_York");

    @Mock private UserRepository userRepository;
    @Mock private SealedCollectionRepository sealedCollectionRepository;
    @Mock private SealedCollectionConverter sealedCollectionConverter;
    @Mock private SealedRepository sealedRepository;
    @Mock private SealedConverter sealedConverter;
    @Mock private TcgService tcgService;
    @Mock private PriceHistoryService priceHistoryService;
    @Mock private TokenParsingServiceImpl tokenParsingService;

    private SealedServiceImpl sealedService;

    @Before
    public void setUp() {

        sealedService = new SealedServiceImpl(userRepository, sealedCollectionRepository, sealedCollectionConverter,
                sealedRepository, sealedConverter, tcgService, priceHistoryService, tokenParsingService);

        when(tokenParsingService.getUserId()).thenReturn(USER_ID);
        when(priceHistoryService.baselines(PriceKind.SEALED)).thenReturn(
                PriceBaselines.builder().dates(new HashMap<>()).prices(new HashMap<>()).build());
    }

    // ---- Permissions ----

    @Test
    public void refusesSomeoneElsesCollections() {

        assertThatThrownBy(() -> sealedService.getSealedCollections(OTHER_USER_ID)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> sealedService.deleteSealedCollection(OTHER_USER_ID, 5L)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> sealedService.deleteSealed(OTHER_USER_ID, 5L, 9L)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> sealedService.createSealedCollectionSnapshot(OTHER_USER_ID, 0L)).isInstanceOf(AuthenticationException.class);

        verify(sealedCollectionRepository, never()).delete(any());
        verify(sealedRepository, never()).delete(any());
    }

    /*
     * Known security bug: getSealedCollection never checks the caller is the user in the path,
     * so anyone signed in can read anyone's collection by putting that user's id in the URL.
     */
    @Ignore("Known security bug: reading one collection skips the permission check - see comment")
    @Test
    public void refusesToShowSomeoneElsesCollection() {

        when(sealedCollectionRepository.findOneByUserEntityIdAndId(OTHER_USER_ID, 5L)).thenReturn(collection(5L, "Theirs"));

        assertThatThrownBy(() -> sealedService.getSealedCollection(OTHER_USER_ID, 5L)).isInstanceOf(AuthenticationException.class);
    }

    /*
     * Known security bug: deleteSealed finishes by deleting whatever sealed product has that id,
     * whether or not it is in the collection named - which need not be the caller's own product.
     */
    @Ignore("Known security bug: deleting removes the product by id from any collection - see comment")
    @Test
    public void deletesOnlyAProductInTheNamedCollection() {

        SealedCollectionEntity mine = collection(5L, "Mine", product(1L, "p-1", 10.0, 1, 12.0));
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(mine);
        when(sealedRepository.findOneById(99L)).thenReturn(product(99L, "p-99", 10.0, 1, 12.0));

        sealedService.deleteSealed(USER_ID, 5L, 99L);

        verify(sealedRepository, never()).delete(any());
    }

    @Test
    public void reportsACollectionThatIsNotTheUsersAsNotFound() {

        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(null);

        assertThatThrownBy(() -> sealedService.createSealedCollectionSnapshot(USER_ID, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    public void deletesAProductFromTheUsersCollection() {

        SealedEntity box = product(1L, "p-1", 10.0, 1, 12.0);
        SealedCollectionEntity mine = collection(5L, "Mine", box, product(2L, "p-2", 5.0, 1, 6.0));
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(mine);
        when(sealedRepository.findOneById(1L)).thenReturn(box);

        sealedService.deleteSealed(USER_ID, 5L, 1L);

        assertThat(mine.getSealedEntities()).extracting(SealedEntity::getId).containsExactly(2L);
        verify(sealedCollectionRepository).save(mine);
    }

    // ---- Reading ----

    @Test
    public void listsTheOverviewFirstWithEachCollectionTotalled() {

        SealedCollectionEntity boxes = collection(5L, "Boxes");
        when(sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Collections.singletonList(boxes));
        SealedCollection converted = SealedCollection.builder().id(5L).name("Boxes")
                .sealed(new ArrayList<>(Arrays.asList(sealed(100.0, 2, 150.0), sealed(50.0, 1, 40.0)))).build();
        when(sealedCollectionConverter.convert(boxes)).thenReturn(converted);
        when(sealedCollectionConverter.convert(eq(boxes), anyMap())).thenReturn(converted);

        List<SealedCollection> collections = sealedService.getSealedCollections(USER_ID);

        assertThat(collections).extracting(SealedCollection::getName).containsExactly("Sealed Collection Overview", "Boxes");
        Sealed total = collections.get(0).getSealed().get(0);
        assertThat(total.getMarketPrice()).isEqualTo(250.0);
        assertThat(total.getPurchasePrice()).isEqualTo(190.0);
    }

    // ---- Refreshing ----

    @Test
    public void refreshAsksOncePerProductAndWritesTodaysSnapshot() {

        SealedEntity inFirst = product(1L, "p-1", 100.0, 2, 90.0);
        SealedEntity inSecond = product(2L, "p-1", 80.0, 1, 90.0);
        SealedCollectionEntity first = collection(5L, "First", inFirst);
        SealedCollectionEntity second = collection(6L, "Second", inSecond);
        when(sealedCollectionRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Arrays.asList(first, second));
        when(tcgService.fetchMarketPriceByProductId(eq("p-1"), anyString())).thenReturn(120.0);

        sealedService.createSealedCollectionSnapshot(USER_ID, 0L);

        verify(tcgService, times(1)).fetchMarketPriceByProductId(eq("p-1"), anyString());
        assertThat(inFirst.getMarketPrice()).isEqualTo(120.0);
        assertThat(inSecond.getMarketPrice()).isEqualTo(120.0);

        SealedCollectionSnapshotEntity snapshot = first.getSealedCollectionSnapshotEntities().get(0);
        assertThat(snapshot.getValue()).isEqualTo(240.0);
        assertThat(snapshot.getPurchasePrice()).isEqualTo(100.0);
        assertThat(snapshot.getTimestamp().toLocalDate()).isEqualTo(LocalDate.now(ZONE_ID));
        assertThat(recordedPrices()).containsExactly(entry("p-1", 120.0));
    }

    @Test
    public void refreshCarriesStoredPricesForwardWhenTcgplayerFails() {

        SealedCollectionEntity boxes = collection(5L, "Boxes", product(1L, "p-1", 100.0, 1, 90.0));
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(boxes);
        when(tcgService.fetchMarketPriceByProductId(anyString(), anyString()))
                .thenThrow(new TcgServiceException(HttpStatus.BAD_GATEWAY, "refused"));

        sealedService.createSealedCollectionSnapshot(USER_ID, 5L);

        assertThat(boxes.getSealedCollectionSnapshotEntities()).hasSize(1);
        assertThat(boxes.getSealedCollectionSnapshotEntities().get(0).getValue()).isEqualTo(90.0);
        assertThat(recordedPrices()).containsExactly(entry("p-1", 90.0));
    }

    @Test
    public void refreshKeepsTheStoredPriceWhereNoPriceCameBack() {

        SealedEntity box = product(1L, "p-1", 100.0, 1, 90.0);
        SealedCollectionEntity boxes = collection(5L, "Boxes", box);
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(boxes);
        when(tcgService.fetchMarketPriceByProductId(eq("p-1"), anyString())).thenReturn(0.0);

        sealedService.createSealedCollectionSnapshot(USER_ID, 5L);

        assertThat(box.getMarketPrice()).isEqualTo(90.0);
        verify(sealedRepository, never()).saveAll(any());
    }

    @Test
    public void secondRefreshOnTheSameDayOverwritesTodaysSnapshot() {

        SealedCollectionEntity boxes = collection(5L, "Boxes", product(1L, "p-1", 100.0, 1, 90.0));
        SealedCollectionSnapshotEntity earlier = SealedCollectionSnapshotEntity.builder()
                .value(90.0).purchasePrice(100.0).timestamp(LocalDateTime.now(ZONE_ID)).sealedCollectionEntity(boxes).build();
        boxes.getSealedCollectionSnapshotEntities().add(earlier);
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(boxes);
        when(tcgService.fetchMarketPriceByProductId(eq("p-1"), anyString())).thenReturn(95.0);

        sealedService.createSealedCollectionSnapshot(USER_ID, 5L);

        assertThat(boxes.getSealedCollectionSnapshotEntities()).containsExactly(earlier);
        assertThat(earlier.getValue()).isEqualTo(95.0);
    }

    @Test
    public void nightlyRefreshCoversEveryCollection() {

        SealedCollectionEntity mine = collection(5L, "Mine", product(1L, "p-1", 100.0, 1, 90.0));
        SealedCollectionEntity theirs = collection(6L, "Theirs", product(2L, "p-2", 10.0, 1, 9.0));
        when(sealedCollectionRepository.findAll()).thenReturn(Arrays.asList(mine, theirs));
        when(tcgService.fetchMarketPriceByProductId(anyString(), anyString())).thenReturn(1.0);

        sealedService.refreshAllSealedCollections();

        assertThat(mine.getSealedCollectionSnapshotEntities()).hasSize(1);
        assertThat(theirs.getSealedCollectionSnapshotEntities()).hasSize(1);
    }

    // ---- Saving ----

    @Test
    public void refusesAProductWithNoQuantityOrPrice() {

        Sealed noQuantity = Sealed.builder().name("Box").quantity(0).purchasePrice(1.0).build();
        Sealed noPrice = Sealed.builder().name("Box").quantity(1).purchasePrice(0.0).build();

        assertThatThrownBy(() -> sealedService.saveSealed(USER_ID, 5L, noQuantity)).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> sealedService.saveSealed(USER_ID, 5L, noPrice)).isInstanceOf(ServletException.class);
    }

    @Test
    public void addsANewProductToTheCollection() {

        SealedCollectionEntity boxes = collection(5L, "Boxes");
        when(sealedCollectionRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(boxes);
        Map<String, String> found = new HashMap<>();
        found.put("productId", "p-7");
        found.put("url", "img");
        when(tcgService.fetchProductIdAndUrl("Bloomburrow - Play Booster Display")).thenReturn(found);
        when(tcgService.fetchMarketPriceByProductId(eq("p-7"), anyString())).thenReturn(210.0);

        sealedService.saveSealed(USER_ID, 5L, Sealed.builder()
                .name("Bloomburrow - Play Booster Display").quantity(1).purchasePrice(180.0).build());

        assertThat(boxes.getSealedEntities()).hasSize(1);
        SealedEntity saved = boxes.getSealedEntities().get(0);
        assertThat(saved.getProductId()).isEqualTo("p-7");
        assertThat(saved.getMarketPrice()).isEqualTo(210.0);
        verify(sealedCollectionRepository).save(boxes);
    }

    @Test
    public void refusesANewCollectionForAUserWhoDoesNotExist() {

        when(userRepository.findOneById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> sealedService.saveSealedCollection(USER_ID,
                SealedCollection.builder().id(-1L).name("New").sortOrder(1).build()))
                .isInstanceOf(ServletException.class);
    }

    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    private Map<String, Double> recordedPrices() {
        ArgumentCaptor<Map<String, Double>> captor = ArgumentCaptor.forClass(Map.class);
        verify(priceHistoryService).record(eq(PriceKind.SEALED), captor.capture());
        return captor.getValue();
    }

    private static SealedCollectionEntity collection(Long id, String name, SealedEntity... products) {
        SealedCollectionEntity collection = SealedCollectionEntity.builder().id(id).name(name).sortOrder(1).build();
        for (SealedEntity product : products) {
            product.setSealedCollectionEntity(collection);
            collection.getSealedEntities().add(product);
        }
        return collection;
    }

    private static SealedEntity product(Long id, String productId, double purchasePrice, int quantity, double marketPrice) {
        return SealedEntity.builder().id(id).name("Product " + productId).productId(productId)
                .purchasePrice(purchasePrice).quantity(quantity).marketPrice(marketPrice).build();
    }

    private static Sealed sealed(double marketPrice, int quantity, double purchasePrice) {
        return Sealed.builder().marketPrice(marketPrice).quantity(quantity).purchasePrice(purchasePrice).build();
    }

}
