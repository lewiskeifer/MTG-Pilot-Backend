package keifer.service.scryfall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Matching this catalogue's names against Scryfall's.
 *
 * A set name that does not flatten to the same text as Scryfall's spelling of it leaves every
 * card in that set unpriced, and says so only as one warning per card. The condition multipliers
 * these prices are then put through are measured and tested in ConditionMultipliersTest.
 */
public class ScryfallPricingTest {

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
