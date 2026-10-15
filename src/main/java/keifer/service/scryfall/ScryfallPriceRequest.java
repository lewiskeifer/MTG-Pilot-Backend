package keifer.service.scryfall;

import keifer.service.model.CardCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One card for Scryfall to price, and what to file the answer under.
 *
 * Deliberately not one of the TCGplayer models, and not interchangeable with them: Scryfall
 * knows a card by name and set, where TCGplayer knew it by a product-condition id that Scryfall
 * has never heard of. The key below is whatever the caller uses for cards itself, handed back
 * with the price so this package never has to know what that is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScryfallPriceRequest {

    /** Whatever the caller wants the returned price filed under. */
    private String key;

    private String name;

    /** The set's full name, as the catalogue spells it, rather than a code. */
    private String setName;

    private boolean foil;

    /** Scryfall prices near mint only, so the condition is applied on this side of the call. */
    private CardCondition condition;

}
