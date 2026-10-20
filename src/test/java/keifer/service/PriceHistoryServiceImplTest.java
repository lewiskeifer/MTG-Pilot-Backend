package keifer.service;

import keifer.persistence.PriceSnapshotRepository;
import keifer.persistence.model.PriceSnapshotEntity;
import keifer.service.model.PriceBaselines;
import keifer.service.model.PriceKind;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * The daily price readings, and the baselines the dashboard's range buttons measure from.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class PriceHistoryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/New_York"));

    @Mock
    private PriceSnapshotRepository priceSnapshotRepository;

    private PriceHistoryServiceImpl priceHistoryService;

    @Before
    public void setUp() {
        priceHistoryService = new PriceHistoryServiceImpl(priceSnapshotRepository);
        when(priceSnapshotRepository.findByKindAndDate(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    public void recordsNothingForNoPrices() {

        priceHistoryService.record(PriceKind.CARD, null);
        priceHistoryService.record(PriceKind.CARD, Collections.emptyMap());

        verifyZeroInteractions(priceSnapshotRepository);
    }

    @Test
    public void recordsEachPriceAsTodaysRow() {

        priceHistoryService.record(PriceKind.CARD, prices("101", 2.5, "102", 10.0));

        List<PriceSnapshotEntity> saved = saved();

        assertThat(saved).extracting(PriceSnapshotEntity::getProductKey).containsExactlyInAnyOrder("101", "102");
        assertThat(saved).allSatisfy(snapshot -> {
            assertThat(snapshot.getKind()).isEqualTo(PriceKind.CARD);
            assertThat(snapshot.getDate()).isEqualTo(TODAY);
        });
        assertThat(saved).filteredOn(snapshot -> snapshot.getProductKey().equals("102"))
                .extracting(PriceSnapshotEntity::getMarketPrice).containsExactly(10.0);
    }

    @Test
    public void skipsPricesThatFailedToRead() {

        // A failed read comes back as 0.0; recorded, it would look like a crash to nothing
        Map<String, Double> marketPrices = prices("101", 0.0, "102", 4.0);
        marketPrices.put("103", null);

        priceHistoryService.record(PriceKind.SEALED, marketPrices);

        assertThat(saved()).extracting(PriceSnapshotEntity::getProductKey).containsExactly("102");
    }

    @Test
    public void overwritesTodaysRowRatherThanAppending() {

        PriceSnapshotEntity morning = snapshot("101", PriceKind.CARD, TODAY, 2.0);
        morning.setId(7L);
        when(priceSnapshotRepository.findByKindAndDate(PriceKind.CARD, TODAY))
                .thenReturn(Collections.singletonList(morning));

        priceHistoryService.record(PriceKind.CARD, prices("101", 3.0));

        List<PriceSnapshotEntity> saved = saved();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0)).isSameAs(morning);
        assertThat(saved.get(0).getId()).isEqualTo(7L);
        assertThat(saved.get(0).getMarketPrice()).isEqualTo(3.0);
    }

    @Test
    public void hasNoBaselinesBeforeAnythingIsRecorded() {

        PriceBaselines baselines = priceHistoryService.baselines(PriceKind.CARD);

        assertThat(baselines.getDates()).isEmpty();
        assertThat(baselines.getPrices()).isEmpty();
    }

    @Test
    public void measuresEachWindowFromTheLastDayRecordedOnOrBeforeItsStart() {

        LocalDate earliest = TODAY.minusDays(400);
        LocalDate near30 = TODAY.minusDays(31);
        LocalDate near90 = TODAY.minusDays(92);
        LocalDate near365 = TODAY.minusDays(366);

        when(priceSnapshotRepository.findTopByKindOrderByDateAsc(PriceKind.CARD))
                .thenReturn(snapshot("101", PriceKind.CARD, earliest, 1.0));
        recordedOnOrBefore(TODAY.minusDays(30), near30);
        recordedOnOrBefore(TODAY.minusDays(90), near90);
        recordedOnOrBefore(TODAY.minusDays(365), near365);

        when(priceSnapshotRepository.findByKindAndDate(PriceKind.CARD, near30))
                .thenReturn(Arrays.asList(snapshot("101", PriceKind.CARD, near30, 5.0),
                        snapshot("102", PriceKind.CARD, near30, 8.0)));
        when(priceSnapshotRepository.findByKindAndDate(PriceKind.CARD, near90))
                .thenReturn(Collections.singletonList(snapshot("101", PriceKind.CARD, near90, 4.0)));
        when(priceSnapshotRepository.findByKindAndDate(PriceKind.CARD, near365))
                .thenReturn(Collections.singletonList(snapshot("101", PriceKind.CARD, near365, 2.0)));

        PriceBaselines baselines = priceHistoryService.baselines(PriceKind.CARD);

        assertThat(baselines.getDates())
                .containsEntry("30", near30)
                .containsEntry("90", near90)
                .containsEntry("365", near365);

        assertThat(baselines.getPrices().get("101"))
                .containsEntry("30", 5.0)
                .containsEntry("90", 4.0)
                .containsEntry("365", 2.0);

        // Held for a month only: no older window to be measured against
        assertThat(baselines.getPrices().get("102")).containsOnlyKeys("30");
    }

    @Test
    public void measuresFromTheOldestDayWhereHistoryIsShorterThanTheWindow() {

        LocalDate earliest = TODAY.minusDays(12);

        when(priceSnapshotRepository.findTopByKindOrderByDateAsc(PriceKind.SEALED))
                .thenReturn(snapshot("55", PriceKind.SEALED, earliest, 100.0));
        when(priceSnapshotRepository.findByKindAndDate(PriceKind.SEALED, earliest))
                .thenReturn(Collections.singletonList(snapshot("55", PriceKind.SEALED, earliest, 100.0)));

        PriceBaselines baselines = priceHistoryService.baselines(PriceKind.SEALED);

        assertThat(baselines.getDates())
                .containsEntry("30", earliest)
                .containsEntry("90", earliest)
                .containsEntry("365", earliest);
        assertThat(baselines.getPrices().get("55"))
                .containsEntry("30", 100.0)
                .containsEntry("365", 100.0);
        assertThat(baselines.datesAsText()).containsEntry("90", earliest.toString());
    }

    private void recordedOnOrBefore(LocalDate cutoff, LocalDate recorded) {
        when(priceSnapshotRepository.findTopByKindAndDateLessThanEqualOrderByDateDesc(eq(PriceKind.CARD), eq(cutoff)))
                .thenReturn(snapshot("101", PriceKind.CARD, recorded, 1.0));
    }

    @SuppressWarnings("unchecked")
    private List<PriceSnapshotEntity> saved() {

        ArgumentCaptor<Iterable<PriceSnapshotEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(priceSnapshotRepository).saveAll(captor.capture());

        List<PriceSnapshotEntity> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);

        return saved;
    }

    private static PriceSnapshotEntity snapshot(String key, PriceKind kind, LocalDate date, double price) {
        return PriceSnapshotEntity.builder().productKey(key).kind(kind).date(date).marketPrice(price).build();
    }

    private static Map<String, Double> prices(Object... keysAndPrices) {

        Map<String, Double> prices = new HashMap<>();
        for (int i = 0; i < keysAndPrices.length; i += 2) {
            prices.put((String) keysAndPrices[i], (Double) keysAndPrices[i + 1]);
        }

        return prices;
    }

}
