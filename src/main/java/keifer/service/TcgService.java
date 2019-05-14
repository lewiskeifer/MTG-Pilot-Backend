package keifer.service;

import keifer.api.model.Card;

public interface TcgService {

    void test();

    String fetchProductConditionId(Card card);

    double fetchMarketPrice(String productConditionId);

}
