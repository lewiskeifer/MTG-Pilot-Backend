package keifer.service;

import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.UserRepository;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.DeckSnapshotEntity;
import keifer.persistence.model.UserEntity;
import keifer.persistence.model.VersionEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.PriceBaselines;
import keifer.service.model.PriceKind;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
 * Decks: who may touch them, how a refresh prices and snapshots them, and how they are ordered.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class DeckServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final ZoneId ZONE_ID = ZoneId.of("America/New_York");

    @Mock private UserRepository userRepository;
    @Mock private DeckRepository deckRepository;
    @Mock private VersionRepository versionRepository;
    @Mock private DeckConverter deckConverter;
    @Mock private CardRepository cardRepository;
    @Mock private CardConverter cardConverter;
    @Mock private TcgService tcgService;
    @Mock private PriceHistoryService priceHistoryService;
    @Mock private TokenParsingServiceImpl tokenParsingService;

    private DeckServiceImpl deckService;

    @Before
    public void setUp() {

        deckService = new DeckServiceImpl(userRepository, deckRepository, versionRepository, deckConverter,
                cardRepository, cardConverter, tcgService, priceHistoryService, tokenParsingService);

        when(tokenParsingService.getUserId()).thenReturn(USER_ID);
        when(priceHistoryService.baselines(PriceKind.CARD)).thenReturn(
                PriceBaselines.builder().dates(new HashMap<>()).prices(new HashMap<>()).build());
    }

    // ---- Permissions ----

    @Test
    public void refusesSomeoneElsesDecks() {

        assertThatThrownBy(() -> deckService.getDecks(OTHER_USER_ID)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> deckService.getDeck(OTHER_USER_ID, 5L)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> deckService.deleteDeck(OTHER_USER_ID, 5L)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> deckService.createDeckSnapshot(OTHER_USER_ID, 0L)).isInstanceOf(AuthenticationException.class);

        verify(deckRepository, never()).delete(any());
    }

    @Test
    public void reportsADeckThatIsNotTheUsersAsNotFound() {

        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(null);

        assertThatThrownBy(() -> deckService.getDeck(USER_ID, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    public void deletesACardOnlyFromTheUsersOwnDeck() {

        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 5L)).thenReturn(null);

        assertThatThrownBy(() -> deckService.deleteCard(USER_ID, 5L, 9L)).isInstanceOf(NotFoundException.class);
        verify(cardRepository, never()).delete(any());
        verify(cardRepository, never()).deleteById(any());
    }

    // Once deleted whatever card had the id, so long as the deck named was the caller's
    @Test
    public void deletesOnlyACardInTheNamedDeck() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 1, 3.0));
        elves.getCardEntities().get(0).setId(1L);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);

        assertThatThrownBy(() -> deckService.deleteCard(USER_ID, 10L, 99L)).isInstanceOf(NotFoundException.class);

        verify(cardRepository, never()).delete(any());
        verify(cardRepository, never()).deleteById(any());
        assertThat(elves.getCardEntities()).hasSize(1);
    }

    @Test
    public void deletesACardFromTheUsersDeck() {

        CardEntity going = cardEntity("sku-1", 1.0, 1, 3.0);
        going.setId(1L);
        CardEntity staying = cardEntity("sku-2", 1.0, 1, 3.0);
        staying.setId(2L);
        DeckEntity elves = deck(10L, "Elves", going, staying);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);

        deckService.deleteCard(USER_ID, 10L, 1L);

        verify(cardRepository).delete(going);
        assertThat(elves.getCardEntities()).containsExactly(staying);
    }

    // ---- Reading ----

    @Test
    public void listsTheOverviewFirstThenEachDeckWithTheBaselineDates() {

        DeckEntity first = deck(10L, "Elves");
        DeckEntity second = deck(11L, "Goblins");
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Arrays.asList(first, second));
        when(deckConverter.convert(eq(first), anyMap())).thenReturn(apiDeck(10L, "Elves", card(2.0, 3, 1.0)));
        when(deckConverter.convert(eq(second), anyMap())).thenReturn(apiDeck(11L, "Goblins", card(5.0, 1, 4.0)));

        Map<String, java.time.LocalDate> dates = new HashMap<>();
        dates.put("30", LocalDate.of(2026, 9, 20));
        when(priceHistoryService.baselines(PriceKind.CARD))
                .thenReturn(PriceBaselines.builder().dates(dates).prices(new HashMap<>()).build());

        List<Deck> decks = deckService.getDecks(USER_ID);

        assertThat(decks).extracting(Deck::getName).containsExactly("Deck Overview", "Elves", "Goblins");
        assertThat(decks).allSatisfy(deck -> assertThat(deck.getBaselineDates()).containsEntry("30", "2026-09-20"));
    }

    @Test
    public void totalsEachDeckInTheOverview() {

        DeckEntity entity = deck(10L, "Elves");
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Collections.singletonList(entity));
        // Value is price times quantity; purchase price is already the total for the line
        when(deckConverter.convert(entity)).thenReturn(apiDeck(10L, "Elves", card(2.0, 3, 1.5), card(10.0, 1, 8.0)));

        Deck overview = deckService.getDeck(USER_ID, 0L);

        assertThat(overview.getId()).isEqualTo(0L);
        assertThat(overview.getCards()).hasSize(1);
        Card elves = overview.getCards().get(0);
        assertThat(elves.getName()).isEqualTo("Elves");
        assertThat(elves.getMarketPrice()).isEqualTo(16.0);
        assertThat(elves.getPurchasePrice()).isEqualTo(9.5);
    }

    @Test
    public void listsSetsAlphabetically() {

        when(versionRepository.findAll()).thenReturn(Arrays.asList(
                VersionEntity.builder().name("Zendikar").build(),
                VersionEntity.builder().name("Alpha Edition").build(),
                VersionEntity.builder().name("Mirage").build()));

        assertThat(deckService.getVersions()).containsExactly("Alpha Edition", "Mirage", "Zendikar");
    }

    // ---- Refreshing ----

    @Test
    public void refreshAsksOncePerPrintingAndUpdatesEveryCardHoldingIt() {

        CardEntity inElves = cardEntity("sku-1", 1.0, 2, 3.0);
        CardEntity inGoblins = cardEntity("sku-1", 1.0, 1, 1.5);
        DeckEntity elves = deck(10L, "Elves", inElves);
        DeckEntity goblins = deck(11L, "Goblins", inGoblins);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Arrays.asList(elves, goblins));
        when(tcgService.fetchMarketPrice(eq("sku-1"), anyString())).thenReturn(4.0);

        deckService.createDeckSnapshot(USER_ID, 0L);

        verify(tcgService, times(1)).fetchMarketPrice(eq("sku-1"), anyString());
        assertThat(inElves.getMarketPrice()).isEqualTo(4.0);
        assertThat(inGoblins.getMarketPrice()).isEqualTo(4.0);
        assertThat(recordedPrices()).containsExactly(entry("sku-1", 4.0));
    }

    @Test
    public void refreshWritesTodaysSnapshotWithTheDecksTotals() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 2, 3.0), cardEntity("sku-2", 5.0, 1, 4.0));
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(tcgService.fetchMarketPrice(eq("sku-1"), anyString())).thenReturn(2.0);
        when(tcgService.fetchMarketPrice(eq("sku-2"), anyString())).thenReturn(6.0);

        deckService.createDeckSnapshot(USER_ID, 10L);

        assertThat(elves.getDeckSnapshotEntities()).hasSize(1);
        DeckSnapshotEntity snapshot = elves.getDeckSnapshotEntities().get(0);
        assertThat(snapshot.getValue()).isEqualTo(2.0 * 2 + 6.0);
        assertThat(snapshot.getPurchasePrice()).isEqualTo(6.0);
        assertThat(snapshot.getTimestamp().toLocalDate()).isEqualTo(LocalDate.now(ZONE_ID));
        verify(deckRepository).save(elves);
    }

    @Test
    public void refreshKeepsTheStoredPriceWhereNoPriceCameBack() {

        CardEntity unpriced = cardEntity("sku-1", 1.0, 1, 3.0);
        DeckEntity elves = deck(10L, "Elves", unpriced);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(tcgService.fetchMarketPrice(eq("sku-1"), anyString())).thenReturn(0.0);

        deckService.createDeckSnapshot(USER_ID, 10L);

        assertThat(unpriced.getMarketPrice()).isEqualTo(3.0);
        assertThat(elves.getDeckSnapshotEntities().get(0).getValue()).isEqualTo(3.0);
        verify(cardRepository, never()).saveAll(any());
    }

    @Test
    public void refreshCarriesStoredPricesForwardWhenTcgplayerFails() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 2, 3.0));
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(tcgService.fetchMarketPrice(anyString(), anyString()))
                .thenThrow(new TcgServiceException(org.springframework.http.HttpStatus.BAD_GATEWAY, "refused"));

        deckService.createDeckSnapshot(USER_ID, 10L);

        // Still a snapshot for today, flat, rather than a gap in the series
        assertThat(elves.getDeckSnapshotEntities()).hasSize(1);
        assertThat(elves.getDeckSnapshotEntities().get(0).getValue()).isEqualTo(6.0);
        assertThat(recordedPrices()).containsExactly(entry("sku-1", 3.0));
    }

    @Test
    public void secondRefreshOnTheSameDayOverwritesTodaysSnapshot() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 1, 3.0));
        DeckSnapshotEntity earlier = DeckSnapshotEntity.builder()
                .value(3.0).purchasePrice(1.0).timestamp(LocalDateTime.now(ZONE_ID)).deckEntity(elves).build();
        elves.getDeckSnapshotEntities().add(earlier);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(tcgService.fetchMarketPrice(eq("sku-1"), anyString())).thenReturn(5.0);

        deckService.createDeckSnapshot(USER_ID, 10L);

        assertThat(elves.getDeckSnapshotEntities()).containsExactly(earlier);
        assertThat(earlier.getValue()).isEqualTo(5.0);
    }

    @Test
    public void refreshAfterYesterdaysSnapshotAddsANewOne() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 1, 3.0));
        elves.getDeckSnapshotEntities().add(DeckSnapshotEntity.builder()
                .value(3.0).purchasePrice(1.0).timestamp(LocalDateTime.now(ZONE_ID).minusDays(1)).deckEntity(elves).build());
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(tcgService.fetchMarketPrice(eq("sku-1"), anyString())).thenReturn(5.0);

        deckService.createDeckSnapshot(USER_ID, 10L);

        assertThat(elves.getDeckSnapshotEntities()).hasSize(2);
    }

    @Test
    public void nightlyRefreshCoversEveryDeck() {

        DeckEntity elves = deck(10L, "Elves", cardEntity("sku-1", 1.0, 1, 3.0));
        DeckEntity someoneElses = deck(20L, "Burn", cardEntity("sku-2", 1.0, 1, 2.0));
        when(deckRepository.findAll()).thenReturn(Arrays.asList(elves, someoneElses));
        when(tcgService.fetchMarketPrice(anyString(), anyString())).thenReturn(1.0);

        deckService.refreshAllDecks();

        assertThat(elves.getDeckSnapshotEntities()).hasSize(1);
        assertThat(someoneElses.getDeckSnapshotEntities()).hasSize(1);
    }

    // ---- Saving cards ----

    @Test
    public void refusesACardWithNoQuantityOrPrice() {

        Card noQuantity = Card.builder().quantity(0).purchasePrice(1.0).build();
        Card noPrice = Card.builder().quantity(1).purchasePrice(0.0).build();

        assertThatThrownBy(() -> deckService.saveCard(USER_ID, 10L, noQuantity)).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> deckService.saveCard(USER_ID, 10L, noPrice)).isInstanceOf(ServletException.class);
    }

    @Test
    public void addsANewCardToTheDeckWithItsNameTitleCased() {

        DeckEntity elves = deck(10L, "Elves");
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(versionRepository.findTopByName("Lorwyn")).thenReturn(VersionEntity.builder().groupId(77).name("Lorwyn").build());
        when(tcgService.fetchProductConditionIdAndUrl(any())).thenReturn(sku("sku-9", "img"));
        when(tcgService.fetchMarketPrice(eq("sku-9"), anyString())).thenReturn(2.5);

        deckService.saveCard(USER_ID, 10L, newCard("llanowar elves"));

        assertThat(elves.getCardEntities()).hasSize(1);
        CardEntity saved = elves.getCardEntities().get(0);
        assertThat(saved.getName()).isEqualTo("Llanowar Elves");
        assertThat(saved.getGroupId()).isEqualTo(77);
        assertThat(saved.getProductConditionId()).isEqualTo("sku-9");
        assertThat(saved.getMarketPrice()).isEqualTo(2.5);
        assertThat(saved.getCardCondition()).isEqualTo(CardCondition.LIGHT_PLAY);
        verify(deckRepository).save(elves);
    }

    @Test
    public void updatesAnExistingCardInPlace() {

        DeckEntity elves = deck(10L, "Elves");
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 10L)).thenReturn(elves);
        when(versionRepository.findTopByName("Lorwyn")).thenReturn(VersionEntity.builder().groupId(77).name("Lorwyn").build());
        when(tcgService.fetchProductConditionIdAndUrl(any())).thenReturn(sku("sku-9", "img"));

        Card existing = newCard("Llanowar Elves");
        existing.setId(42L);
        deckService.saveCard(USER_ID, 10L, existing);

        ArgumentCaptor<CardEntity> saved = ArgumentCaptor.forClass(CardEntity.class);
        verify(cardRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(42L);
        assertThat(elves.getCardEntities()).isEmpty();
    }

    // ---- Saving decks and their order ----

    @Test
    public void refusesANewDeckForAUserWhoDoesNotExist() {

        when(userRepository.findOneById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> deckService.saveDeck(USER_ID, Deck.builder().id(-1L).name("New").sortOrder(1).build()))
                .isInstanceOf(ServletException.class);
    }

    // Once took the highest position across every user's decks, and shared it rather than followed it
    @Test
    public void putsANewDeckAfterTheUsersLastOne() throws Exception {

        when(userRepository.findOneById(USER_ID)).thenReturn(UserEntity.builder().id(USER_ID).build());
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID))
                .thenReturn(Arrays.asList(deckAt(1), deckAt(2), deckAt(3)));
        when(deckRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deckService.saveDeck(USER_ID, Deck.builder().id(-1L).name("New").format("Modern").build());

        assertThat(savedDeck().getSortOrder()).isEqualTo(4);
    }

    // The screen sends whatever position was showing for the deck viewed before
    @Test
    public void putsANewDeckAtTheEndWhateverPositionIsSent() throws Exception {

        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3));
        when(userRepository.findOneById(USER_ID)).thenReturn(UserEntity.builder().id(USER_ID).build());
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deckService.saveDeck(USER_ID, Deck.builder().id(-1L).name("New").format("Modern").sortOrder(2).build());

        assertThat(savedDeck().getSortOrder()).isEqualTo(4);
        assertThat(decks).extracting(DeckEntity::getSortOrder).containsExactly(1, 2, 3);
    }

    @Test
    public void putsAUsersFirstDeckFirst() throws Exception {

        when(userRepository.findOneById(USER_ID)).thenReturn(UserEntity.builder().id(USER_ID).build());
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(Collections.emptyList());
        when(deckRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deckService.saveDeck(USER_ID, Deck.builder().id(-1L).name("First").format("Modern").build());

        assertThat(savedDeck().getSortOrder()).isEqualTo(1);
    }

    // Once left two decks at the new position: 1, 4, 2, 3, 4
    @Test
    public void movingADeckDownShiftsOnlyTheDecksItPasses() throws Exception {

        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3), deckAt(4), deckAt(5));
        DeckEntity moving = decks.get(1);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, moving.getId())).thenReturn(moving);

        deckService.saveDeck(USER_ID, Deck.builder().id(moving.getId()).name("Moving").format("Modern").sortOrder(4).build());

        // 1, [2 -> 4], 3 -> 2, 4 -> 3, and 5 stays where it was
        assertThat(decks).extracting(DeckEntity::getSortOrder).containsExactly(1, 4, 2, 3, 5);
    }

    @Test
    public void movingADeckUpShiftsOnlyTheDecksItPasses() throws Exception {

        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3), deckAt(4), deckAt(5));
        DeckEntity moving = decks.get(3);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, moving.getId())).thenReturn(moving);

        deckService.saveDeck(USER_ID, Deck.builder().id(moving.getId()).name("Moving").format("Modern").sortOrder(2).build());

        // 1, 2 -> 3, 3 -> 4, [4 -> 2], 5
        assertThat(decks).extracting(DeckEntity::getSortOrder).containsExactly(1, 3, 4, 2, 5);
    }

    @Test
    public void savingADeckWithoutAPositionLeavesItWhereItIs() throws Exception {

        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3));
        DeckEntity renamed = decks.get(1);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, renamed.getId())).thenReturn(renamed);

        deckService.saveDeck(USER_ID, Deck.builder().id(renamed.getId()).name("Renamed").format("Modern").build());

        assertThat(decks).extracting(DeckEntity::getSortOrder).containsExactly(1, 2, 3);
        assertThat(renamed.getName()).isEqualTo("Renamed");
    }

    @Test
    public void movingADeckCopesWithPositionsTheOldBugDuplicated() throws Exception {

        // Left by the old bug: two decks at 4. The moving one is told apart by id, not position
        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3), deckAt(4), deckAt(4));
        DeckEntity moving = decks.get(4);
        moving.setId(999L);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, 999L)).thenReturn(moving);

        deckService.saveDeck(USER_ID, Deck.builder().id(999L).name("Moving").format("Modern").sortOrder(1).build());

        assertThat(decks).extracting(DeckEntity::getSortOrder).containsExactly(2, 3, 4, 4, 1);
    }

    @Test
    public void deletingADeckClosesTheGapItLeaves() {

        List<DeckEntity> decks = Arrays.asList(deckAt(1), deckAt(2), deckAt(3), deckAt(4));
        DeckEntity deleting = decks.get(1);
        when(deckRepository.findByUserEntityIdOrderBySortOrderAsc(USER_ID)).thenReturn(decks);
        when(deckRepository.findOneByUserEntityIdAndId(USER_ID, deleting.getId())).thenReturn(deleting);

        deckService.deleteDeck(USER_ID, deleting.getId());

        verify(deckRepository).delete(deleting);
        assertThat(Arrays.asList(decks.get(0), decks.get(2), decks.get(3)))
                .extracting(DeckEntity::getSortOrder).containsExactly(1, 2, 3);
    }

    // ---- Helpers ----

    private DeckEntity savedDeck() {
        ArgumentCaptor<DeckEntity> saved = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).save(saved.capture());
        return saved.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> recordedPrices() {
        ArgumentCaptor<Map<String, Double>> captor = ArgumentCaptor.forClass(Map.class);
        verify(priceHistoryService).record(eq(PriceKind.CARD), captor.capture());
        return captor.getValue();
    }

    private static DeckEntity deck(Long id, String name, CardEntity... cards) {
        DeckEntity deck = DeckEntity.builder().id(id).name(name).sortOrder(1).build();
        for (CardEntity card : cards) {
            card.setDeckEntity(deck);
            deck.getCardEntities().add(card);
        }
        return deck;
    }

    private static DeckEntity deckAt(int sortOrder) {
        return DeckEntity.builder().id(100L + sortOrder).name("Deck " + sortOrder).sortOrder(sortOrder).build();
    }

    private static CardEntity cardEntity(String sku, double purchasePrice, int quantity, double marketPrice) {
        return CardEntity.builder().name("Card " + sku).version("Set").isFoil(false)
                .cardCondition(CardCondition.NEAR_MINT).productConditionId(sku)
                .purchasePrice(purchasePrice).quantity(quantity).marketPrice(marketPrice).build();
    }

    private static Deck apiDeck(Long id, String name, Card... cards) {
        return Deck.builder().id(id).name(name).cards(new ArrayList<>(Arrays.asList(cards))).build();
    }

    private static Card card(double marketPrice, int quantity, double purchasePrice) {
        return Card.builder().marketPrice(marketPrice).quantity(quantity).purchasePrice(purchasePrice).build();
    }

    private static Card newCard(String name) {
        return Card.builder().name(name).set("Lorwyn").cardCondition("Lightly Played").isFoil(false)
                .quantity(1).purchasePrice(1.0).build();
    }

    private static Map<String, String> sku(String productConditionId, String image) {
        Map<String, String> results = new HashMap<>();
        results.put("productConditionId", productConditionId);
        results.put("image", image);
        return results;
    }

}
