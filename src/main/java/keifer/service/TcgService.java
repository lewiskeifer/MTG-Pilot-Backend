package keifer.service;

import keifer.api.model.Card;

import java.util.List;
import java.util.Map;

public interface TcgService {

    Map<String, String> fetchProductConditionIdAndUrl(Card card);

    List<String> fetchVersions(String cardName);

    Map<String, String> fetchProductIdAndUrl(String name);

    /**
     * @param description names the card for the log, since a bare product-condition id says
     *                    nothing about which card in which set failed to price.
     */
    double fetchMarketPrice(String productConditionId, String description);

    /** @param description names the product, for the same reason. */
    double fetchMarketPriceByProductId(String productId, String description);

    void syncVersions();

}
