package keifer.service;

import keifer.api.model.Card;

import java.util.List;
import java.util.Map;

public interface TcgService {

    Map<String, String> fetchProductConditionIdAndUrl(Card card);

    List<String> fetchVersions(String cardName);

    Map<String, String> fetchProductIdAndUrl(String name);

    double fetchMarketPrice(String productConditionId);

    double fetchMarketPriceByProductId(String productId);

    void syncVersions();

}
