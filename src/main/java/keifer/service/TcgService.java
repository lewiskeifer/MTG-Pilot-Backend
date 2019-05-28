package keifer.service;

import keifer.api.model.Card;
import org.springframework.data.util.Pair;

import java.util.Map;

public interface TcgService {

    Map<String, String> fetchProductConditionIdAndUrl(Card card);

    double fetchMarketPrice(String productConditionId);

}
