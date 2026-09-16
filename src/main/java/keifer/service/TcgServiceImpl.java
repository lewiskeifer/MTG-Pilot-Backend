package keifer.service;


import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import keifer.api.model.Card;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.VersionEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.YAMLConfig;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TcgServiceImpl implements TcgService {

    private YAMLConfig yamlConfig;
    /*
     * Read by the parallel streams that price a refresh and replaced by whichever of them first
     * meets a refusal, so every thread has to see the new one rather than a cached field.
     */
    private volatile String token;
    private VersionRepository versionRepository;

    private static final String tcgUrlPrefix = "https://api.tcgplayer.com";

    public TcgServiceImpl(@NonNull YAMLConfig yamlConfig, @NonNull VersionRepository versionRepository) {
        this.yamlConfig = yamlConfig;
        token = getToken();
        this.versionRepository = versionRepository;
    }

    /*
     * Every call below goes through here, so a token that has gone stale between the 3 AM
     * renewal and now costs one extra round trip instead of a day of failures. One retry only:
     * if TCGplayer refuses a freshly minted token, the keys are wrong and trying again is just a
     * way of being refused repeatedly.
     */
    private <T> ResponseEntity<T> getAuthorized(String url, Class<T> responseType) {

        String tokenUsed = token;

        try {
            return exchange(url, responseType, tokenUsed);
        } catch (HttpClientErrorException e) {

            if (!isAuthFailure(e)) {
                throw e;
            }

            log.warn("TCGplayer refused a call with {}; renewing the token and trying once more",
                    e.getStatusCode());

            renewToken(tokenUsed);

            return exchange(url, responseType, token);
        }
    }

    private <T> ResponseEntity<T> exchange(String url, Class<T> responseType, String bearerToken) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", headers);

        return restTemplate.exchange(url, HttpMethod.GET, requestEntity, responseType);
    }

    /*
     * A refresh prices thousands of cards in parallel, so a stale token is met by many threads at
     * once. Whichever arrives first renews it; the rest find their own token already replaced and
     * take the new one, rather than each asking TCGplayer for one of their own.
     */
    private synchronized void renewToken(String tokenUsed) {

        if (!tokenUsed.equals(token)) {
            log.debug("Token already renewed by another caller.");
            return;
        }

        getToken();
    }

    // Fires at 3 AM every day
    @Scheduled(cron="0 0 3 * * *", zone="America/New_York")
    private synchronized String getToken() {

        RestTemplate restTemplate = new RestTemplate();
        String body = "grant_type=client_credentials&client_id=" + yamlConfig.getPublicKey()
                + "&client_secret=" + yamlConfig.getPrivateKey();
        String url = "https://api.tcgplayer.com/token";
        HttpEntity<String> requestEntity = new HttpEntity<>(body);

        try {

            ResponseEntity<String> responseEntity =
                    restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            this.token = readAccessToken(responseEntity.getBody());
            log.info("TCGplayer token refreshed.");

            return this.token;

        } catch (RestClientException e) {
            /*
             * Worth shouting about: every price and every card lookup uses this token, so a
             * failure here is the reason for a day of 401s everywhere else.
             */
            log.error("Could not get a TCGplayer token. Every price and card lookup will fail until this succeeds.", e);
            throw new TcgServiceException(HttpStatus.BAD_GATEWAY, "Could not authenticate with TCGplayer.", e);
        }
    }

    /*
     * The token used to be cut out of the response by character offset - substring(17, 343) -
     * which is correct only while access_token stays the first field and stays exactly 326
     * characters long. Anything else yields a plausible looking string that TCGplayer then
     * rejects on every later call, putting a 401 a very long way from its cause. Read the field.
     *
     * Static and package private so it can be tested on its own: the RestTemplate around it is
     * built inline and cannot be stood in for, and this is the one piece of the auth path worth
     * pinning down.
     */
    static String readAccessToken(String body) {

        if (body == null || body.isEmpty()) {
            throw new TcgServiceException(HttpStatus.BAD_GATEWAY, "TCGplayer returned an empty token response.");
        }

        String accessToken;

        try {
            JsonElement field = new JsonParser().parse(body).getAsJsonObject().get("access_token");
            accessToken = field == null || field.isJsonNull() ? null : field.getAsString();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new TcgServiceException(HttpStatus.BAD_GATEWAY,
                    "Could not read TCGplayer's token response: " + summarize(body), e);
        }

        if (accessToken == null || accessToken.isEmpty()) {
            // No token in there to leak, so the body is safe to quote back
            throw new TcgServiceException(HttpStatus.BAD_GATEWAY,
                    "TCGplayer's token response carried no access_token: " + summarize(body));
        }

        return accessToken;
    }

    /** Enough of a response to recognise it, without pasting a whole page into the log. */
    private static String summarize(String body) {
        return body.length() <= 120 ? body : body.substring(0, 120) + "...";
    }

    // TODO language support
    @SneakyThrows
    public Map<String, String> fetchProductConditionIdAndUrl(Card card) {

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&productTypes=Cards&Limit=50&includeSkus=true&productName="
                + card.getName();

        try {
            ResponseEntity<ProductConditionIdResponse> responseEntity =
                    getAuthorized(url, ProductConditionIdResponse.class);

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
            throw lookupFailure(e, "card with name: " + card.getName() + " and set: " + card.getSet());
        }

        // Answered, but nothing in the catalogue matched this printing and condition
        throw new TcgServiceException(HttpStatus.BAD_REQUEST,
                "Failed to find card with name: " + card.getName() + " and set: " + card.getSet());
    }

    @Override
    @SneakyThrows
    public List<String> fetchVersions(String cardName) {

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&productTypes=Cards&Limit=50&productName="
                + cardName;

        ResponseEntity<ProductConditionIdResponse> responseEntity;
        try {
            responseEntity = getAuthorized(url, ProductConditionIdResponse.class);
        } catch (HttpClientErrorException e) {
            throw lookupFailure(e, "card with name: " + cardName);
        }

        List<String> versions = new ArrayList<>();
        for (ProductConditionIdResult productConditionIdResult : results(responseEntity, "card with name: " + cardName)) {

            VersionEntity version = versionRepository.findTopByGroupId(Integer.valueOf(productConditionIdResult.groupId));

            /*
             * A set this server has never synced. Skipping it lists the printings we can name
             * instead of failing the whole lookup on a null, which is what used to happen and
             * surfaced as an unexplained 500.
             */
            if (version == null) {
                log.warn("No synced set for groupId {}, leaving it out of the versions for {}",
                        productConditionIdResult.groupId, cardName);
                continue;
            }

            versions.add(version.getName());
        }

        return versions;
    }

    @Override
    @SneakyThrows
    public Map<String, String> fetchProductIdAndUrl(String name) {

        String url
                = tcgUrlPrefix + "/v1.39.0/catalog/products?categoryId=1&Limit=50&productName=" + name;

        ResponseEntity<ProductConditionIdResponse> responseEntity;
        try {
            responseEntity = getAuthorized(url, ProductConditionIdResponse.class);
        } catch (HttpClientErrorException e) {
            throw lookupFailure(e, "product with name: " + name);
        }

        // An empty result list used to come back out of here as an index out of bounds, which
        // reached the screen as a 500 with nothing in it about the name that was not found
        ProductConditionIdResult productConditionIdResult =
                results(responseEntity, "product with name: " + name).get(0);

        return ImmutableMap.of("productId", productConditionIdResult.getProductId(), "url", productConditionIdResult.getImageUrl());
    }

    @Override
    @SneakyThrows
    public double fetchMarketPrice(String productConditionId, String description) {

        if (productConditionId == null || productConditionId.equals("")) {
            return 0;
        }

        String url = tcgUrlPrefix + "/pricing/marketprices/" + productConditionId;

        ResponseEntity<MarketPriceResponse> responseEntity;
        try {
            responseEntity = getAuthorized(url, MarketPriceResponse.class);
        }
        catch (HttpClientErrorException e) {
            /*
             * An auth failure says nothing about this card - every call after it fails the same
             * way. Returning 0 would price the whole collection at nothing, and a 0 is skipped
             * further down, so the refresh would report success having quietly changed nothing
             * and written a snapshot identical to yesterday's. Fail where it can be seen.
             */
            if (isAuthFailure(e)) {
                throw lookupFailure(e, "price of " + description + " [productConditionId " + productConditionId + "]");
            }

            /*
             * Names the card as well as the id. A 404 here means TCGplayer no longer knows this
             * printing and condition, which is fixed by re-picking the set on that card - and
             * nobody can do that from an id alone.
             */
            log.warn("TCGplayer answered {} for the price of {} [productConditionId {}]; leaving it unpriced",
                    e.getStatusCode(), description, productConditionId);
            return 0;
        }

        MarketPriceResponse body = responseEntity.getBody();
        List<MarketPriceResult> results = body == null ? null : body.getResults();

        if (results == null) {
            log.warn("TCGplayer returned no price results for {} [productConditionId {}]; leaving it unpriced",
                    description, productConditionId);
            return 0;
        }

        // TODO I believe this always returns list of size 1
        for (MarketPriceResult marketPriceResult : results) {
            return marketPriceResult.getPrice();
        }

        return 0;
    }

    @Override
    @SneakyThrows
    public double fetchMarketPriceByProductId(String productId, String description) {

        if (productId == null || productId.equals("")) {
            return 0;
        }

        String url = tcgUrlPrefix + "/pricing/product/" + productId;

        ResponseEntity<ProductMarketPriceResponse> responseEntity;
        try {
            responseEntity = getAuthorized(url, ProductMarketPriceResponse.class);
        }
        catch (HttpClientErrorException e) {
            // As above: a refusal is about us, not about this product
            if (isAuthFailure(e)) {
                throw lookupFailure(e, "price of " + description + " [productId " + productId + "]");
            }

            log.warn("TCGplayer answered {} for the price of {} [productId {}]; leaving it unpriced",
                    e.getStatusCode(), description, productId);
            return 0;
        }

        ProductMarketPriceResponse body = responseEntity.getBody();
        List<ProductMarketPriceResult> results = body == null ? null : body.getResults();

        if (results == null) {
            log.warn("TCGplayer returned no price results for {} [productId {}]; leaving it unpriced",
                    description, productId);
            return 0;
        }

        for (ProductMarketPriceResult marketPriceResult : results) {
            if (marketPriceResult.getSubTypeName().equals("Normal") && marketPriceResult.getMarketPrice() != null) {
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

            ResponseEntity<GroupResponse> responseEntity = getAuthorized(url, GroupResponse.class);

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

    /*
     * TCGplayer refusing our credentials and TCGplayer not holding a card arrive here looking
     * alike, and have to be told apart: the first is true of every call being made and wants
     * fixing at the token, the second is true of one card and wants fixing at the card. The
     * original exception is always chained on, so the status behind the message survives.
     */
    private TcgServiceException lookupFailure(HttpClientErrorException e, String what) {

        if (isAuthFailure(e)) {

            log.error("TCGplayer rejected the request for {} with {}. The token is stale or the API keys are wrong.",
                    what, e.getStatusCode(), e);

            return new TcgServiceException(HttpStatus.BAD_GATEWAY,
                    "TCGplayer rejected our credentials (" + e.getRawStatusCode() + " " + e.getStatusText()
                            + "). Nothing can be looked up or priced until the API token is renewed.", e);
        }

        log.warn("TCGplayer answered {} for {}", e.getStatusCode(), what, e);

        return new TcgServiceException(HttpStatus.BAD_REQUEST,
                "Failed to find " + what + " (TCGplayer answered " + e.getRawStatusCode() + ").", e);
    }

    /*
     * A 200 carrying nothing usable is still a failure, and one that used to surface as a null
     * pointer or an index out of bounds - neither of which says which card was being looked up.
     */
    private List<ProductConditionIdResult> results(ResponseEntity<ProductConditionIdResponse> responseEntity,
                                                   String what) {

        ProductConditionIdResponse body = responseEntity.getBody();
        List<ProductConditionIdResult> results = body == null ? null : body.getResults();

        if (results == null || results.isEmpty()) {
            log.warn("TCGplayer returned no results for {}", what);
            throw new TcgServiceException(HttpStatus.BAD_REQUEST, "Failed to find " + what + ".");
        }

        return results;
    }

    /** True where TCGplayer is turning us away rather than answering about a card. */
    private boolean isAuthFailure(HttpClientErrorException e) {
        return e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN;
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
