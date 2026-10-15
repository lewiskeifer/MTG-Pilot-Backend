package keifer.service.scryfall;

import java.util.Collection;
import java.util.Map;

public interface ScryfallService {

    /**
     * Prices every card given, keyed by whatever key each one carried in.
     *
     * Near mint, as Scryfall gives them, with no condition applied - that is a policy of this
     * application rather than a fact from Scryfall, so the caller applies it. It also has to
     * stay out of here so the condition multipliers have something to be measured against.
     *
     * A card Scryfall cannot place is left out rather than returned as zero, so a caller can
     * tell "worth nothing" from "nobody knows". It is named in the log either way.
     */
    Map<String, Double> fetchNearMintPrices(Collection<ScryfallPriceRequest> cards);

}
