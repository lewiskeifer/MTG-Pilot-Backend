package keifer.persistence.model;

import keifer.service.model.CardCondition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The label that names a card in a log line.
 *
 * All four parts earn their place: two printings of one name price differently, and so do two
 * conditions of one printing, so a line missing any of them cannot be acted on.
 */
public class CardEntityDescribeTest {

    @Test
    public void namesSetAndCondition() {

        CardEntity card = CardEntity.builder()
                .name("Hangarback Walker")
                .version("Magic Origins")
                .cardCondition(CardCondition.NEAR_MINT)
                .isFoil(false)
                .build();

        assertEquals("Hangarback Walker (Magic Origins, Near Mint)", card.describe());
    }

    @Test
    public void callsOutFoils() {

        CardEntity card = CardEntity.builder()
                .name("Hangarback Walker")
                .version("Magic Origins")
                .cardCondition(CardCondition.LIGHT_PLAY)
                .isFoil(true)
                .build();

        assertEquals("Hangarback Walker (Magic Origins, Lightly Played, foil)", card.describe());
    }

    /** A card being saved has no entity yet, and takes the same shape through the static form. */
    @Test
    public void namesACardThatIsNotSavedYet() {

        assertEquals("Sol Ring (Commander 2021, Near Mint, foil)",
                CardEntity.describe("Sol Ring", "Commander 2021", "Near Mint", true));
    }

    /** A half built card still has to produce a line, not a null pointer. */
    @Test
    public void survivesMissingParts() {

        CardEntity card = CardEntity.builder().name("Sol Ring").build();

        assertEquals("Sol Ring (null, null)", card.describe());
    }
}
