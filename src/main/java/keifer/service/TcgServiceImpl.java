package keifer.service;


import keifer.api.model.Card;
import keifer.api.model.CardCondition;
import keifer.service.model.YAMLConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class TcgServiceImpl implements TcgService {

    private YAMLConfig yamlConfig;
    private String token;

    public TcgServiceImpl(YAMLConfig yamlConfig) {
        this.yamlConfig = yamlConfig;
        token = getToken();
    }

    @Override
    public void test() {

        String productConditionId = fetchProductConditionId(Card.builder()
                .name("Arcbound Ravager")
                .version("Darksteel")
                .cardCondition(CardCondition.NEAR_MINT)
                .isFoil(true).build());

        double marketPrice = fetchMarketPrice(productConditionId);

        int x = 0;
    }

    private String getToken() {

        RestTemplate restTemplate = new RestTemplate();
        String body = "grant_type=client_credentials&client_id=" + yamlConfig.getPublicKey() + "&client_secret=" + yamlConfig.getPrivateKey();
        String url = "https://api.tcgplayer.com/token";
        HttpEntity<String> requestEntity = new HttpEntity<>(body);

        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        return responseEntity.getBody().substring(17, 343);
    }

    // TODO language support
    public String fetchProductConditionId(Card card) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        String url = "http://api.tcgplayer.com/v1.14.0/catalog/products?categoryId=1&productTypes=Cards&Limit=50&productName=" + card.getName();

        try {
            ResponseEntity<ProductConditionIdResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, ProductConditionIdResponse.class);

            String version = card.getVersion();
            for (Result result : responseEntity.getBody().getResults()) {

                if (!result.getUrl().contains(version.toLowerCase())) {
                    continue;
                }

                for (ProductCondition productCondition : result.getProductConditions()) {

                    String condition = card.getIsFoil() ? card.getCardCondition().toString() + " Foil" : card.getCardCondition().toString();
                    if (productCondition.getName().equals(condition)) {
                        return productCondition.getProductConditionId();
                    }
                }
            }
        } catch (HttpClientErrorException e) {
            return "Not found";
        }

        return "Not found";
    }

    public double fetchMarketPrice(String productConditionId) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        String url = "http://api.tcgplayer.com/pricing/marketprices/" + productConditionId;

        ResponseEntity<MarketPriceResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, MarketPriceResponse.class);

        List<MarketPriceResult> results = responseEntity.getBody().getResults();

        // TODO I believe this always returns list of size 1
        for (MarketPriceResult marketPriceResult : results) {
            return marketPriceResult.getPrice();
        }

        return -1;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductConditionIdResponse {
        private String totalItems;
        private String success;
        private List<String> errors;
        private List<Result> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Result {
        private String productId;
        private String productName;
        private String image;
        private String categoryId;
        private String groupId;
        private String url;
        private String modifiedOn;
        private List<ProductCondition> productConditions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductCondition {
        private String productConditionId;
        private String name; //condition
        private String language;
        private Boolean isFoil;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MarketPriceResponse {
        private Boolean success;
        private List<String> errors;
        private List<MarketPriceResult> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MarketPriceResult {
        private String productConditionId;
        private Double price;
        private Double lowestRange;
        private Double highestRange;
    }
}
