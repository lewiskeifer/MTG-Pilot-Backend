package keifer.service;

import keifer.api.model.Card;

public interface TcgService {

    String fetchProductConditionId(Card card);

    double fetchMarketPrice(String productConditionId);

}
