package keifer.service.scryfall;

import keifer.persistence.CardRepository;
import keifer.persistence.ConditionMultiplierRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.ConditionMultiplierEntity;
import keifer.service.model.CardCondition;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Measuring what a played card is worth from the collection rather than assuming it.
 *
 * The case that matters is the one in the middle: the stored TCGplayer prices are from months
 * ago and Scryfall's are from today, so a raw ratio carries the market's drift as well as the
 * condition's discount. Near mint cards are the control that separates the two, and if that
 * correction were dropped every multiplier would be wrong by however far the market has moved -
 * quietly, and in a way that looks entirely reasonable on screen.
 */
public class ConditionMultipliersTest {

    /** Scryfall's near mint price for every card in these tests. */
    private static final double SCRYFALL_NEAR_MINT = 10.00;

    /** The market rose 20% between the stored prices being taken and Scryfall being asked. */
    private static final double DRIFT = 1.20;

    private CardRepository cardRepository;
    private ConditionMultiplierRepository multiplierRepository;
    private ScryfallService scryfallService;
    private ConditionMultipliersImpl multipliers;

    private final List<CardEntity> cards = new ArrayList<>();
    private long nextKey = 1;

    @Before
    public void setUp() {

        cardRepository = mock(CardRepository.class);
        multiplierRepository = mock(ConditionMultiplierRepository.class);
        scryfallService = mock(ScryfallService.class);

        when(cardRepository.findAll()).thenReturn(cards);
        when(multiplierRepository.findAll()).thenReturn(Collections.emptyList());
        when(multiplierRepository.findOneByCardConditionAndFoil(any(), any())).thenReturn(null);

        // Every card in these tests is the same printing, worth the same near mint
        scryfallPricesEverythingAt(SCRYFALL_NEAR_MINT);

        multipliers = new ConditionMultipliersImpl(cardRepository, multiplierRepository, scryfallService);
    }

    /*
     * Lightly played cards really are worth 80% of near mint, but every stored price also sits
     * 20% above today's market. Measured raw they would come out at 0.96; measured against the
     * near mint control they come out at the 0.80 that is actually true.
     */
    @Test
    public void theMarketsDriftIsDividedOut() {

        held(CardCondition.NEAR_MINT, false, 40, 1.00);
        held(CardCondition.LIGHT_PLAY, false, 20, 0.80);

        assertEquals(0.80, measured(CardCondition.LIGHT_PLAY, false).getMultiplier(), 0.01);
    }

    @Test
    public void nearMintMeasuresAsItself() {

        held(CardCondition.NEAR_MINT, false, 40, 1.00);

        assertEquals(1.00, measured(CardCondition.NEAR_MINT, false).getMultiplier(), 0.01);
    }

    @Test
    public void foilsAreMeasuredApartFromPlainCards() {

        held(CardCondition.NEAR_MINT, false, 40, 1.00);
        held(CardCondition.MODERATE_PLAY, false, 20, 0.70);
        held(CardCondition.MODERATE_PLAY, true, 20, 0.55);

        assertEquals(0.70, measured(CardCondition.MODERATE_PLAY, false).getMultiplier(), 0.01);
        assertEquals(0.55, measured(CardCondition.MODERATE_PLAY, true).getMultiplier(), 0.01);
    }

    /** Four damaged cards cannot settle what damaged cards are worth. */
    @Test
    public void aThinSampleKeepsTheConvention() {

        held(CardCondition.NEAR_MINT, false, 40, 1.00);
        held(CardCondition.DAMAGED, false, 4, 0.10);

        MeasuredMultiplier damaged = measured(CardCondition.DAMAGED, false);

        assertFalse("four cards should not count as a measurement", damaged.isMeasured());
        assertEquals("falls back to the convention", 0.35, damaged.getMultiplier(), 0.001);
        assertTrue(damaged.getNote().contains("under the"));
    }

    /** Nothing unmeasured is written to the database, where it would later read as a finding. */
    @Test
    public void onlyMeasurementsAreStored() {

        held(CardCondition.NEAR_MINT, false, 40, 1.00);
        held(CardCondition.DAMAGED, false, 4, 0.10);

        multipliers.calibrate();

        ArgumentCaptor<ConditionMultiplierEntity> saved = ArgumentCaptor.forClass(ConditionMultiplierEntity.class);
        verify(multiplierRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());

        saved.getAllValues().forEach(entity ->
                assertTrue("stored " + entity.getCardCondition() + " from a thin sample",
                        entity.getSampleSize() >= 15));
    }

    /** Without enough near mint cards there is no control, so nothing can be measured at all. */
    @Test
    public void tooFewNearMintCardsMeasuresNothing() {

        held(CardCondition.NEAR_MINT, false, 5, 1.00);
        held(CardCondition.LIGHT_PLAY, false, 40, 0.80);

        multipliers.calibrate();

        verify(multiplierRepository, never()).save(any(ConditionMultiplierEntity.class));
    }

    /*
     * Cheap cards price in whole quarters, so their ratios are noise. A collection of them must
     * not be mistaken for evidence.
     */
    @Test
    public void pennyCardsAreNotMeasuredFrom() {

        scryfallPricesEverythingAt(0.25);

        held(CardCondition.NEAR_MINT, false, 40, 1.00);
        held(CardCondition.LIGHT_PLAY, false, 40, 0.80);

        multipliers.calibrate();

        verify(multiplierRepository, never()).save(any(ConditionMultiplierEntity.class));
    }

    /*
     * With nothing measured, every condition still has to come back with a sensible figure: a
     * condition missing from the convention would price a damaged card as near mint, and no log
     * line anywhere would mention it.
     */
    @Test
    public void theConventionCoversEveryConditionAndDescends() {

        double previous = Double.MAX_VALUE;

        for (CardCondition condition : new CardCondition[]{CardCondition.NEAR_MINT, CardCondition.LIGHT_PLAY,
                CardCondition.MODERATE_PLAY, CardCondition.HEAVY_PLAY, CardCondition.DAMAGED}) {

            double multiplier = multipliers.multiplierFor(condition, false);

            assertTrue(condition + " is priced at " + multiplier, multiplier > 0 && multiplier <= 1.0);
            assertTrue(condition + " is not worth less than the condition above it", multiplier < previous);

            previous = multiplier;
        }
    }

    @Test
    public void anUnknownConditionIsNotChargedFullPriceQuietly() {
        assertEquals(1.0, multipliers.multiplierFor(null, false), 0.0001);
    }

    private MeasuredMultiplier measured(CardCondition condition, boolean foil) {

        return multipliers.calibrate().stream()
                .filter(result -> result.getCondition() == condition && result.isFoil() == foil)
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing reported for " + condition));
    }

    /*
     * Stubbed through doAnswer rather than when(...): when(mock.call(any())) calls the mock, so
     * restubbing this inside a test would run the previous answer against a null argument first.
     */
    private void scryfallPricesEverythingAt(double nearMint) {

        doAnswer(call -> {
            Map<String, Double> prices = new HashMap<>();
            Collection<ScryfallPriceRequest> asked = call.getArgument(0);
            asked.forEach(request -> prices.put(request.getKey(), nearMint));
            return prices;
        }).when(scryfallService).fetchNearMintPrices(any());
    }

    /** Adds cards held in one condition, priced at a share of near mint and carrying the drift. */
    private void held(CardCondition condition, boolean foil, int count, double trueMultiplier) {

        for (int i = 0; i < count; ++i) {
            cards.add(CardEntity.builder()
                    .productConditionId(String.valueOf(nextKey++))
                    .name("Test Card")
                    .version("Test Set")
                    .isFoil(foil)
                    .cardCondition(condition)
                    .marketPrice(SCRYFALL_NEAR_MINT * trueMultiplier * DRIFT)
                    .build());
        }
    }

}
