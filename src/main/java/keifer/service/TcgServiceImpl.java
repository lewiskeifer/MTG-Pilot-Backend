package keifer.service;


import com.google.common.collect.ImmutableMap;
import keifer.api.model.Card;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.VersionEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.YAMLConfig;
import lombok.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TcgServiceImpl implements TcgService {

    private YAMLConfig yamlConfig;
    private String token;
    private VersionRepository versionRepository;

    private static final String tcgUrlPrefix = "https://api.tcgplayer.com";

    public TcgServiceImpl(@NonNull YAMLConfig yamlConfig, @NonNull VersionRepository versionRepository) {
        this.yamlConfig = yamlConfig;
        token = getToken();
        this.versionRepository = versionRepository;
    }

    // Fires at 3 AM every day
    @Scheduled(cron="0 0 3 * * *", zone="America/New_York")
    private String getToken() {

        RestTemplate restTemplate = new RestTemplate();
        String body = "grant_type=client_credentials&client_id=" + yamlConfig.getPublicKey()
                + "&client_secret=" + yamlConfig.getPrivateKey();
        String url = "https://api.tcgplayer.com/token";
        HttpEntity<String> requestEntity = new HttpEntity<>(body);

        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        String token = responseEntity.getBody().substring(17, 343);

        this.token = token;
        return token;
    }

    // TODO language support
    @SneakyThrows
    public Map<String, String> fetchProductConditionIdAndUrl(Card card) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&productTypes=Cards&Limit=50&includeSkus=true&productName="
                + card.getName();

        try {
            ResponseEntity<ProductConditionIdResponse> responseEntity =
                    restTemplate.exchange(url, HttpMethod.GET, requestEntity, ProductConditionIdResponse.class);

            for (ProductConditionIdResult productConditionIdResult : responseEntity.getBody().getResults()) {

                if (!Integer.valueOf(productConditionIdResult.getGroupId()).equals(card.getGroupId())) {
                    continue;
                }

                for (Sku sku : productConditionIdResult.getSkus()) {

                    String conditionId = mapConditionId(card);
                    String printingId = mapPrintingId(card);
                    if (conditionId.equals(sku.getConditionId()) && printingId.equals(sku.getPrintingId())) {
                        return ImmutableMap.of("productConditionId", sku.getSkuId(), "image", productConditionIdResult.getImageUrl());
                    }
                }
            }
        }

        catch (HttpClientErrorException e) {
            throw new ServletException("Failed to find card with name: " + card.getName() + " and set: " + card.getSet());
        }

        throw new ServletException("Failed to find card with name: " + card.getName() + " and set: " + card.getSet());
    }

    @Override
    @SneakyThrows
    public List<String> fetchVersions(String cardName) {

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&productTypes=Cards&Limit=50&productName="
                + cardName;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        ResponseEntity<ProductConditionIdResponse> responseEntity = null;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, ProductConditionIdResponse.class);
        } catch (HttpClientErrorException e) {
            throw new ServletException("Failed to find card with name: " + cardName);
        }

        List<String> versions = new ArrayList<>();
        for (ProductConditionIdResult productConditionIdResult : responseEntity.getBody().getResults()) {
            versions.add(versionRepository.findTopByGroupId(Integer.valueOf(productConditionIdResult.groupId)).getName());
        }

        return versions;
    }

    @Override
    @SneakyThrows
    public Map<String, String> fetchProductIdAndUrl(String name) {

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&Limit=50&productName=" + name;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        ResponseEntity<ProductConditionIdResponse> responseEntity = null;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, ProductConditionIdResponse.class);
        } catch (HttpClientErrorException e) {
            throw new ServletException("Failed to find card with name: " + name);
        }

        ProductConditionIdResult productConditionIdResult = responseEntity.getBody().getResults().get(0);
        return ImmutableMap.of("productId", productConditionIdResult.getProductId(), "url", productConditionIdResult.getImageUrl());
    }

    @Override
    @SneakyThrows
    public double fetchMarketPrice(String productConditionId) {

        if (productConditionId == null || productConditionId.equals("")) {
            return 0;
        }

        String url = tcgUrlPrefix + "/pricing/marketprices/" + productConditionId;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        ResponseEntity<MarketPriceResponse> responseEntity = null;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, MarketPriceResponse.class);
        }
        catch (HttpClientErrorException e) {
            System.out.println("Failed to find card with productConditionId: " + productConditionId);
            return 0;
        }

        List<MarketPriceResult> results = responseEntity.getBody().getResults();

        // TODO I believe this always returns list of size 1
        for (MarketPriceResult marketPriceResult : results) {
            return marketPriceResult.getPrice();
        }

        return 0;
    }

    @Override
    @SneakyThrows
    public double fetchMarketPriceByProductId(String productId) {

        if (productId == null || productId.equals("")) {
            return 0;
        }

        String url = tcgUrlPrefix + "/pricing/product/" + productId;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        ResponseEntity<ProductMarketPriceResponse> responseEntity = null;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, ProductMarketPriceResponse.class);
        }
        catch (HttpClientErrorException e) {
            System.out.println("Failed to find card with productConditionId: " + productId);
            return 0;
        }

        List<ProductMarketPriceResult> results = responseEntity.getBody().getResults();

        for (ProductMarketPriceResult marketPriceResult : results) {
            if (marketPriceResult.getSubTypeName().equals("Normal")) {
                return marketPriceResult.getMarketPrice();
            }
        }

        return 0;
    }

    // Fires at 8 AM every day
    @Scheduled(cron="0 0 8 * * *", zone="America/New_York")
    @Override
    public void syncVersions() {

        for (int i = 0; i < 5; ++i) {

            int offset = i * 100;
            String url = tcgUrlPrefix + "/v1.39.0/catalog/categories/1/groups?Limit=100" + "&offset=" + offset;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

            ResponseEntity<GroupResponse> responseEntity =
                    restTemplate.exchange(url, HttpMethod.GET, requestEntity, GroupResponse.class);

            for (GroupResult groupResult : responseEntity.getBody().getResults()) {
                if (versionRepository.findTopByName(groupResult.getName()) == null) {
                    versionRepository.save(VersionEntity.builder()
                            .groupId(Integer.valueOf(groupResult.getGroupId()))
                            .name(groupResult.getName())
                            .abbreviation(groupResult.getAbbreviation() != null ? groupResult.getAbbreviation() : "")
                            .build());
                }
            }
        }
    }

    private String mapConditionId(Card card) {
        String cardCondition = card.getCardCondition();
        if (cardCondition.equals(CardCondition.NEAR_MINT.toString())) return "1";
        if (cardCondition.equals(CardCondition.LIGHT_PLAY.toString())) return "2";
        if (cardCondition.equals(CardCondition.MODERATE_PLAY.toString())) return "3";
        if (cardCondition.equals(CardCondition.HEAVY_PLAY.toString())) return "4";
        return "5";
    }

    private String mapPrintingId(Card card) {
        return card.getIsFoil() ? "2" : "1";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductConditionIdResponse {
        private String totalItems;
        private String success;
        private List<String> errors;
        private List<ProductConditionIdResult> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductConditionIdResult {
        private String productId;
        private String name;
        private String cleanName;
        private String imageUrl;
        private String categoryId;
        private String groupId;
        private String url;
        private String modifiedOn;
        private List<Sku> skus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Sku {
        private String skuId;
        private String productId;
        private String languageId;
        private String printingId;
        private String conditionId;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductMarketPriceResponse {
        private Boolean success;
        private List<String> errors;
        private List<ProductMarketPriceResult> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductMarketPriceResult {
        private String productId;
        private Double lowPrice;
        private Double midPrice;
        private Double highPrice;
        private Double marketPrice;
        private Double directLowPrice;
        private String subTypeName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GroupResponse {
        private String totalItems;
        private String success;
        private List<String> errors;
        private List<GroupResult> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GroupResult {
        private String groupId;
        private String name;
        private String abbreviation;
        private String supplemental;
        private String publishedOn;
        private String modifiedOn;
    }
}
