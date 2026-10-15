package keifer.service.scryfall;

import java.util.Collection;
import java.util.Map;

public interface ScryfallService {

    /**
     * Prices every card given, keyed by whatever key each one carried in.
     *
     * A card Scryfall cannot place is left out rather than returned as zero, so a caller can
     * tell "worth nothing" from "nobody knows", and is named in the log either way.
     */
    Map<String, Double> fetchMarketPrices(Collection<ScryfallPriceRequest> cards);

}
