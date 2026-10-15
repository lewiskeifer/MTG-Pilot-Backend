package keifer.service.scryfall;

import keifer.persistence.CardRepository;
import keifer.persistence.ConditionMultiplierRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.ConditionMultiplierEntity;
import keifer.service.model.CardCondition;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out what a played card is worth against a near mint one, from the collection itself.
 *
 * Every held card carries the TCGplayer price for its own condition, from while that API was
 * open. Scryfall prices the same printing near mint. The ratio between them is the condition
 * discount - except that the stored prices are from then and Scryfall's are from now, so the
 * ratio also carries however far the market has moved in between.
 *
 * Near mint cards are the control. Their ratio would be 1.0 if nothing had drifted, so whatever
 * it actually is, is the drift - plus any standing difference between what TCGplayer called a
 * market price and what Scryfall calls one. Dividing every condition's ratio by the near mint
 * ratio cancels both and leaves the discount on its own.
 *
 * Medians throughout, not averages: one misidentified card or one spiking reprint would drag an
 * average a long way, and a collection has a few of both.
 */
@Slf4j
@Service
@Transactional
public class ConditionMultipliersImpl implements ConditionMultipliers {

    /*
     * Where a figure falls back to when the collection cannot answer. A convention, in the shape
     * the market usually takes, and plainly labelled as unmeasured wherever it is reported.
     */
    private static final Map<CardCondition, Double> CONVENTION = new EnumMap<>(CardCondition.class);

    static {
        CONVENTION.put(CardCondition.NEAR_MINT, 1.00);
        CONVENTION.put(CardCondition.LIGHT_PLAY, 0.85);
        CONVENTION.put(CardCondition.MODERATE_PLAY, 0.70);
        CONVENTION.put(CardCondition.HEAVY_PLAY, 0.50);
        CONVENTION.put(CardCondition.DAMAGED, 0.35);
    }

    /*
     * A cheap card's price is quantised - a quarter here is a quarter there - so its ratio is
     * noise rather than signal. Only cards with some room to discount are measured.
     */
    private static final double MIN_PRICE_TO_MEASURE = 1.00;

    /** Below this, a group is too thin to say anything, and the convention is kept instead. */
    private static final int MIN_SAMPLE = 15;

    /** The control group carries every other figure, so it is held to more. */
    private static final int MIN_CONTROL_SAMPLE = 30;

    /** Nothing sane lies outside this, and a figure that does says the measurement went wrong. */
    private static final double LOWEST_SENSIBLE = 0.05;
    private static final double HIGHEST_SENSIBLE = 1.00;

    private final CardRepository cardRepository;
    private final ConditionMultiplierRepository conditionMultiplierRepository;
    private final ScryfallService scryfallService;

    public ConditionMultipliersImpl(@NonNull CardRepository cardRepository,
                                    @NonNull ConditionMultiplierRepository conditionMultiplierRepository,
                                    @NonNull ScryfallService scryfallService) {
        this.cardRepository = cardRepository;
        this.conditionMultiplierRepository = conditionMultiplierRepository;
        this.scryfallService = scryfallService;
    }

    @Override
    public double multiplierFor(CardCondition condition, boolean foil) {

        ConditionMultiplierEntity measured =
                conditionMultiplierRepository.findOneByCardConditionAndFoil(condition, foil);

        if (measured != null) {
            return measured.getMultiplier();
        }

        // Foils fall back to the plain card's measured figure before falling back to convention
        ConditionMultiplierEntity plain =
                foil ? conditionMultiplierRepository.findOneByCardConditionAndFoil(condition, false) : null;

        if (plain != null) {
            return plain.getMultiplier();
        }

        return CONVENTION.getOrDefault(condition, 1.00);
    }

    @Override
    public void calibrateIfUnmeasured() {

        if (conditionMultiplierRepository.findAll().isEmpty()) {
            log.info("No condition multipliers measured yet. Measuring now, while the prices held"
                    + " against these cards are still TCGplayer's.");
            calibrate();
        }
    }

    @Override
    public List<MeasuredMultiplier> calibrate() {

        List<CardEntity> cards = cardRepository.findAll();

        // One Scryfall call for the lot, near mint and unadjusted - the multipliers are what is
        // being worked out here, so applying them would be circular
        Map<String, Double> nearMintPrices = scryfallService.fetchNearMintPrices(priceRequests(cards));

        Map<Group, List<Double>> ratios = collectRatios(cards, nearMintPrices);

        Double control = controlRatio(ratios);

        if (control == null) {
            log.warn("Not enough near mint cards priced to measure against, so the condition"
                    + " multipliers stay as they are");
            return current();
        }

        log.info("Near mint cards price at {} of Scryfall's figure today, which is the drift"
                + " every other condition is measured against", round(control));

        List<MeasuredMultiplier> results = measureGroups(ratios, control);
        store(results);

        return results;
    }

    @Override
    public List<MeasuredMultiplier> current() {

        List<MeasuredMultiplier> results = new ArrayList<>();

        for (CardCondition condition : CardCondition.values()) {
            for (boolean foil : new boolean[]{false, true}) {

                ConditionMultiplierEntity stored =
                        conditionMultiplierRepository.findOneByCardConditionAndFoil(condition, foil);

                results.add(MeasuredMultiplier.builder()
                        .condition(condition)
                        .foil(foil)
                        .multiplier(multiplierFor(condition, foil))
                        .sampleSize(stored == null ? 0 : stored.getSampleSize())
                        .measured(stored != null)
                        .note(stored == null ? "convention, not measured" : "measured " + stored.getCalculatedOn())
                        .build());
            }
        }

        return results;
    }

    private List<ScryfallPriceRequest> priceRequests(List<CardEntity> cards) {

        List<ScryfallPriceRequest> requests = new ArrayList<>();

        for (CardEntity card : cards) {

            if (card.getProductConditionId() == null || card.getMarketPrice() == null) {
                continue;
            }

            requests.add(ScryfallPriceRequest.builder()
                    .key(card.getProductConditionId())
                    .name(card.getName())
                    .setName(card.getVersion())
                    .foil(Boolean.TRUE.equals(card.getIsFoil()))
                    .condition(card.getCardCondition())
                    .build());
        }

        return requests;
    }

    private Map<Group, List<Double>> collectRatios(List<CardEntity> cards, Map<String, Double> nearMintPrices) {

        Map<Group, List<Double>> ratios = new HashMap<>();

        for (CardEntity card : cards) {

            Double nearMint = card.getProductConditionId() == null
                    ? null : nearMintPrices.get(card.getProductConditionId());

            if (nearMint == null || nearMint < MIN_PRICE_TO_MEASURE) {
                continue;
            }

            Double held = card.getMarketPrice();

            if (held == null || held <= 0 || card.getCardCondition() == null) {
                continue;
            }

            Group group = new Group(card.getCardCondition(), Boolean.TRUE.equals(card.getIsFoil()));
            ratios.computeIfAbsent(group, any -> new ArrayList<>()).add(held / nearMint);
        }

        return ratios;
    }

    /*
     * Near mint of either finish, pooled: the drift being corrected for is the market's, and it
     * moved the same amount under both.
     */
    private Double controlRatio(Map<Group, List<Double>> ratios) {

        List<Double> nearMint = new ArrayList<>();

        ratios.forEach((group, values) -> {
            if (group.condition == CardCondition.NEAR_MINT) {
                nearMint.addAll(values);
            }
        });

        if (nearMint.size() < MIN_CONTROL_SAMPLE) {
            log.warn("Only {} near mint cards could be priced, short of the {} needed to measure"
                    + " the market's drift", nearMint.size(), MIN_CONTROL_SAMPLE);
            return null;
        }

        double median = median(nearMint);

        return median <= 0 ? null : median;
    }

    private List<MeasuredMultiplier> measureGroups(Map<Group, List<Double>> ratios, double control) {

        List<MeasuredMultiplier> results = new ArrayList<>();

        for (CardCondition condition : CardCondition.values()) {
            for (boolean foil : new boolean[]{false, true}) {

                List<Double> values = ratios.getOrDefault(new Group(condition, foil), Collections.emptyList());

                if (values.size() < MIN_SAMPLE) {
                    results.add(MeasuredMultiplier.builder()
                            .condition(condition).foil(foil)
                            .multiplier(CONVENTION.getOrDefault(condition, 1.00))
                            .sampleSize(values.size())
                            .measured(false)
                            .note(values.size() + " cards is under the " + MIN_SAMPLE + " needed to measure")
                            .build());
                    continue;
                }

                double measured = median(values) / control;

                if (measured < LOWEST_SENSIBLE || measured > HIGHEST_SENSIBLE) {
                    log.warn("{}{} measured at {} of near mint, which is outside anything sensible;"
                                    + " keeping the convention", condition, foil ? " foil" : "", round(measured));
                    results.add(MeasuredMultiplier.builder()
                            .condition(condition).foil(foil)
                            .multiplier(CONVENTION.getOrDefault(condition, 1.00))
                            .sampleSize(values.size())
                            .measured(false)
                            .note("measured " + round(measured) + ", outside the sensible range")
                            .build());
                    continue;
                }

                results.add(MeasuredMultiplier.builder()
                        .condition(condition).foil(foil)
                        .multiplier(round(measured))
                        .sampleSize(values.size())
                        .measured(true)
                        .note("measured from " + values.size() + " cards")
                        .build());
            }
        }

        warnIfOutOfOrder(results);

        return results;
    }

    /*
     * A worse condition worth more than a better one is not impossible in a small sample, but it
     * is a sign the sample is small or the data is dirty. Said out loud rather than smoothed
     * over, because quietly reordering measured figures would be inventing them again.
     */
    private void warnIfOutOfOrder(List<MeasuredMultiplier> results) {

        CardCondition[] descending = {CardCondition.NEAR_MINT, CardCondition.LIGHT_PLAY,
                CardCondition.MODERATE_PLAY, CardCondition.HEAVY_PLAY, CardCondition.DAMAGED};

        for (boolean foil : new boolean[]{false, true}) {
            for (int i = 1; i < descending.length; ++i) {

                double worse = multiplierIn(results, descending[i], foil);
                double better = multiplierIn(results, descending[i - 1], foil);

                if (worse > better) {
                    log.warn("{}{} measured at {}, above {} at {}. The sample is probably thin.",
                            descending[i], foil ? " foil" : "", round(worse), descending[i - 1], round(better));
                }
            }
        }
    }

    private double multiplierIn(List<MeasuredMultiplier> results, CardCondition condition, boolean foil) {

        return results.stream()
                .filter(result -> result.getCondition() == condition && result.isFoil() == foil)
                .findFirst()
                .map(MeasuredMultiplier::getMultiplier)
                .orElse(CONVENTION.getOrDefault(condition, 1.00));
    }

    private void store(List<MeasuredMultiplier> results) {

        LocalDate today = LocalDate.now();

        for (MeasuredMultiplier result : results) {

            // Only measurements are kept. A convention stored would read, later, as a finding.
            if (!result.isMeasured()) {
                continue;
            }

            ConditionMultiplierEntity stored = conditionMultiplierRepository
                    .findOneByCardConditionAndFoil(result.getCondition(), result.isFoil());

            if (stored == null) {
                stored = ConditionMultiplierEntity.builder()
                        .cardCondition(result.getCondition())
                        .foil(result.isFoil())
                        .build();
            }

            stored.setMultiplier(result.getMultiplier());
            stored.setSampleSize(result.getSampleSize());
            stored.setCalculatedOn(today);

            conditionMultiplierRepository.save(stored);

            log.info("{}{} is worth {} of near mint, from {} cards",
                    result.getCondition(), result.isFoil() ? " foil" : "",
                    result.getMultiplier(), result.getSampleSize());
        }
    }

    private static double median(List<Double> values) {

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int middle = sorted.size() / 2;

        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** One condition in one finish - the grain the discount is measured at. */
    private static final class Group {

        private final CardCondition condition;
        private final boolean foil;

        private Group(CardCondition condition, boolean foil) {
            this.condition = condition;
            this.foil = foil;
        }

        @Override
        public boolean equals(Object other) {

            if (this == other) {
                return true;
            }
            if (!(other instanceof Group)) {
                return false;
            }

            Group that = (Group) other;
            return foil == that.foil && condition == that.condition;
        }

        @Override
        public int hashCode() {
            return (condition == null ? 0 : condition.hashCode()) * 31 + (foil ? 1 : 0);
        }
    }

}
