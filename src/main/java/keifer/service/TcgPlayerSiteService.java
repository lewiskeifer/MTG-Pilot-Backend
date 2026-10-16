package keifer.service;

import java.util.Map;

/**
 * TCGplayer prices read from the public website rather than the API, which is closed to us.
 *
 * A stop-gap only: these are the endpoints the website itself calls, unpublished and free to
 * change or start refusing without notice. Sealed products only, for now.
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

}
