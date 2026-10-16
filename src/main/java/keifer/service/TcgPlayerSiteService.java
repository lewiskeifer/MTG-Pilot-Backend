package keifer.service;

import keifer.api.model.Card;
import keifer.persistence.model.CardEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * TCGplayer prices read from the public website rather than the API, which is closed to us.
 *
 * A stop-gap only: these are the endpoints the website itself calls, unpublished and free to
 * change or start refusing without notice.
 */
public interface TcgPlayerSiteService {

    /** The best matching sealed product for a name, as its "productId" and image "url". */
    Map<String, String> fetchProductIdAndUrl(String name);

    /**
     * 0 where TCGplayer holds no market price for the product, which the refresh reads as
     * "keep the stored price".
     *
     * @param description names the product for the log, since a bare product id says nothing.
     */
    double fetchMarketPriceByProductId(String productId, String description);

    /**
     * The SKU for a card's printing, condition and finish, as "productConditionId", with its
     * "image", and the set it belongs to as "groupId", "setName" and "abbreviation".
     */
    Map<String, String> fetchProductConditionIdAndUrl(Card card);

    /** The names of the sets a card was printed in. */
    List<String> fetchVersions(String cardName);

    /**
     * Market prices for these cards, against their product-condition ids. A card that cannot be
     * priced is left out, which the refresh reads as "keep the stored price".
     */
    Map<String, Double> fetchCardMarketPrices(Collection<CardEntity> cards);

}
