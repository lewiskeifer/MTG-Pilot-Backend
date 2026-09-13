package keifer.service;

import keifer.persistence.PriceSnapshotRepository;
import keifer.persistence.model.PriceSnapshotEntity;
import keifer.service.model.PriceBaselines;
import keifer.service.model.PriceKind;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The price of every held product, one reading a day.
 *
 * The nightly refresh already asks TCG for each product's price to total up the decks; this
 * keeps those readings so the dashboard can answer what a single card did over a window instead
 * of only what its deck did. Nothing here is backfilled - the series starts the first night this
 * runs, and windows longer than the table is old have no baseline to offer.
 */
@Slf4j
@Service
@Transactional
public class PriceHistoryServiceImpl implements PriceHistoryService {

    /*
     * A price belongs to a day in this zone, not in whatever zone the server happens to run in,
     * matching how the deck and collection snapshots beside it are dated.
     */
    private static final String TIME_ZONE = "America/New_York";
    private static final ZoneId ZONE_ID = ZoneId.of(TIME_ZONE);

    /*
     * The windows the dashboard's range buttons offer, named by the same day counts the frontend
     * sends. A preset added there needs adding here or its button finds no baseline.
     *
     * The "All" button is absent on purpose: over an item's whole life the honest starting point
     * is what was paid for it, which the card already carries and which reaches back further than
     * this table ever will. The dashboard measures that button against the purchase price.
     */
    private static final List<Integer> WINDOW_DAYS = Arrays.asList(30, 90, 365);

    private final PriceSnapshotRepository priceSnapshotRepository;

    public PriceHistoryServiceImpl(@NonNull PriceSnapshotRepository priceSnapshotRepository) {
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    @Override
    public void record(PriceKind kind, Map<String, Double> marketPrices) {

        if (marketPrices == null || marketPrices.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(ZONE_ID);

        // Today's rows, so a second refresh on the same day overwrites rather than appends - the
        // same rule the deck snapshots follow, and the reason for the unique key on the table
        Map<String, PriceSnapshotEntity> todays = new HashMap<>();
        for (PriceSnapshotEntity snapshot : priceSnapshotRepository.findByKindAndDate(kind, today)) {
            todays.put(snapshot.getProductKey(), snapshot);
        }

        List<PriceSnapshotEntity> toSave = new ArrayList<>();

        for (Map.Entry<String, Double> entry : marketPrices.entrySet()) {

            Double marketPrice = entry.getValue();

            /*
             * A TCG read that failed comes back as 0.0, and is already ignored when the decks are
             * totalled. Recording it would leave a hole in the series that reads as the product
             * crashing to nothing and back, and would rank it the biggest loser in the collection.
             */
            if (marketPrice == null || marketPrice == 0.0) {
                continue;
            }

            PriceSnapshotEntity existing = todays.get(entry.getKey());

            if (existing != null) {
                existing.setMarketPrice(marketPrice);
                toSave.add(existing);
            } else {
                toSave.add(PriceSnapshotEntity.builder()
                        .productKey(entry.getKey())
                        .kind(kind)
                        .date(today)
                        .marketPrice(marketPrice)
                        .build());
            }
        }

        priceSnapshotRepository.saveAll(toSave);
    }

    /*
     * Read a whole day at a time rather than a row at a time: one query per window returns every
     * product's price on that day, which is a few thousand rows against the thousands of single
     * lookups the same answer would otherwise cost.
     */
    @Override
    public PriceBaselines baselines(PriceKind kind) {

        Map<String, LocalDate> dates = new HashMap<>();
        Map<String, Map<String, Double>> prices = new HashMap<>();

        LocalDate today = LocalDate.now(ZONE_ID);
        LocalDate earliest = earliestRecordedDate(kind);

        for (Integer days : WINDOW_DAYS) {

            LocalDate date = baselineDate(kind, today.minusDays(days), earliest);

            if (date == null) {
                continue;
            }

            dates.put(String.valueOf(days), date);
            collectPrices(kind, prices, String.valueOf(days), date);
        }

        return PriceBaselines.builder().dates(dates).prices(prices).build();
    }

    /*
     * The day a window measures from: the last one recorded on or before its start, or the oldest
     * day on record where the history does not reach that far back. Returning the oldest rather
     * than nothing keeps a young table useful - the figures are real, they just cover less than
     * the button says, and the date comes back with them so the screen can name the shorter span.
     */
    private LocalDate baselineDate(PriceKind kind, LocalDate cutoff, LocalDate earliest) {

        PriceSnapshotEntity snapshot =
                priceSnapshotRepository.findTopByKindAndDateLessThanEqualOrderByDateDesc(kind, cutoff);

        return snapshot != null ? snapshot.getDate() : earliest;
    }

    private void collectPrices(PriceKind kind, Map<String, Map<String, Double>> prices,
                               String window, LocalDate date) {

        for (PriceSnapshotEntity snapshot : priceSnapshotRepository.findByKindAndDate(kind, date)) {
            prices.computeIfAbsent(snapshot.getProductKey(), key -> new HashMap<>())
                    .put(window, snapshot.getMarketPrice());
        }
    }

    private LocalDate earliestRecordedDate(PriceKind kind) {

        PriceSnapshotEntity snapshot = priceSnapshotRepository.findTopByKindOrderByDateAsc(kind);

        return snapshot == null ? null : snapshot.getDate();
    }

}
