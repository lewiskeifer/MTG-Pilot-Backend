package keifer.service.scryfall;

import keifer.service.model.CardCondition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The two pieces of the Scryfall pricing that fail quietly.
 *
 * A set name that does not flatten to the same text as Scryfall's spelling of it leaves every
 * card in that set unpriced, and a condition missing from the multiplier table prices a damaged
 * card as though it were near mint. Neither shows up as an error anywhere.
 */
public class ScryfallPricingTest {

    @Test
    public void everyConditionIsPriced() {

        for (CardCondition condition : CardCondition.values()) {
            double multiplier = ScryfallServiceImpl.conditionMultiplier(condition);
            assertTrue(condition + " is priced at " + multiplier + " of near mint",
                    multiplier > 0 && multiplier <= 1.0);
        }
    }

    @Test
    public void nearMintIsTheFullPrice() {
        // Scryfall quotes near mint, so that one is the figure itself and not a fraction of it
        assertEquals(1.0, ScryfallServiceImpl.conditionMultiplier(CardCondition.NEAR_MINT), 0.0001);
    }

    @Test
    public void worseConditionsAreWorthLess() {

        CardCondition[] descending = {CardCondition.NEAR_MINT, CardCondition.LIGHT_PLAY,
                CardCondition.MODERATE_PLAY, CardCondition.HEAVY_PLAY, CardCondition.DAMAGED};

        for (int i = 1; i < descending.length; ++i) {
            assertTrue(descending[i] + " is not worth less than " + descending[i - 1],
                    ScryfallServiceImpl.conditionMultiplier(descending[i])
                            < ScryfallServiceImpl.conditionMultiplier(descending[i - 1]));
        }
    }

    /** An unknown condition must not be charged full price on the quiet. */
    @Test
    public void anUnknownConditionFallsBackToNearMint() {
        assertEquals(1.0, ScryfallServiceImpl.conditionMultiplier(null), 0.0001);
    }

    /*
     * The two catalogues punctuate differently. These are real cases: TCGplayer writes "The
     * Brothers War" where Scryfall writes "The Brothers' War", and both spellings have to land
     * on the same text or every card in that set goes unpriced.
     */
    @Test
    public void setNamesMatchAcrossPunctuation() {
        assertEquals(ScryfallServiceImpl.flatten("The Brothers' War"),
                ScryfallServiceImpl.flatten("The Brothers War"));
    }

    @Test
    public void setNamesMatchAcrossCaseAndSpacing() {
        assertEquals(ScryfallServiceImpl.flatten("Kamigawa: Neon Dynasty"),
                ScryfallServiceImpl.flatten("kamigawa neon dynasty"));
    }

    @Test
    public void cardNamesMatchAcrossAccents() {
        // Scryfall spells it Lim-Dûl's Vault; a catalogue without the accent still has to match
        assertEquals(ScryfallServiceImpl.flatten("Lim-Dûl's Vault"),
                ScryfallServiceImpl.flatten("Lim-Dul's Vault"));
    }

    @Test
    public void differentSetsStayApart() {
        assertTrue(!ScryfallServiceImpl.flatten("Modern Horizons 2")
                .equals(ScryfallServiceImpl.flatten("Modern Horizons 3")));
    }

    @Test
    public void nothingFlattensToNothing() {
        assertEquals("", ScryfallServiceImpl.flatten(null));
    }

}
