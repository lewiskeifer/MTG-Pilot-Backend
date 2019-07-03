package keifer.service;

import keifer.api.model.Card;

import java.util.List;
import java.util.Map;

public interface TcgService {

    Map<String, String> fetchProductConditionIdAndUrl(Card card);

    List<String> fetchVersions(String cardName);

    double fetchMarketPrice(String productConditionId);

    void syncVersions();

}
