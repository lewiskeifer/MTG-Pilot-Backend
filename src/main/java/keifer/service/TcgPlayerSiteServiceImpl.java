package keifer.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableMap;
import keifer.api.model.Card;
import keifer.persistence.model.CardEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * Three endpoints, all found by watching the website work: the search behind its search bar,
 * the product details behind a product page, and the price history behind that page's chart.
 * None needs a key. All answer in the same product and SKU ids the API used, so the ids already
 * stored against cards and sealed products carry over as they are.
 */
@Slf4j
@Service
public class TcgPlayerSiteServiceImpl implements TcgPlayerSiteService {

    private static final String SEARCH_URL = "https://mp-search-api.tcgplayer.com/v1/search/request";
    private static final String DETAILS_URL = "https://mp-search-api.tcgplayer.com/v1/product/{productId}/details";
    private static final String HISTORY_URL = "https://infinite-api.tcgplayer.com/price/history/{productId}/detailed?range=month";
    private static final String IMAGE_URL = "https://tcgplayer-cdn.tcgplayer.com/product/%s_200w.jpg";

    private static final String SEALED = "Sealed Products";
    private static final String CARDS = "Cards";

    // These endpoints serve a browser, so ask like one
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private static final int SEALED_SEARCH_SIZE = 10;
    private static final int CARD_SEARCH_SIZE = 50;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /*
     * The least time between any two requests, however many refreshes are running. This is a
     * website, not an API, and a burst of requests is the quickest way to be turned away by it.
     */
    private static final long MIN_MS_BETWEEN_REQUESTS = 250;
    private long lastRequestAt;

    private final RestTemplate restTemplate = buildRestTemplate();

    /*
     * Cards are stored by SKU, but prices are asked for by product, and the site offers no way
     * from one to the other except searching by name and set. So each SKU's product is found
     * once and remembered here, and a refresh after the first asks once per product held. Kept
     * in memory rather than on the card so this stop-gap leaves the schema as it found it.
     */
    private final Map<String, Long> productIdsBySku = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> fetchProductIdAndUrl(String name) {

        List<Product> products = search(name, SEALED, null, SEALED_SEARCH_SIZE);

        if (products.isEmpty()) {
            log.warn("TCGplayer's site found no sealed product named {}", name);
            throw new TcgServiceException(HttpStatus.BAD_REQUEST, "Failed to find sealed product with name: " + name + ".");
        }

        // The exact name where the search holds it, which is usually first; the top hit otherwise
        Product match = products.stream()
                .filter(product -> name.trim().equalsIgnoreCase(product.getProductName()))
                .findFirst()
                .orElse(products.get(0));

        if (!name.trim().equalsIgnoreCase(match.getProductName())) {
            log.info("No sealed product is named exactly {}; taking the closest, {} [productId {}]",
                    name, match.getProductName(), match.getProductId());
        }

        String productId = String.valueOf(match.getProductId());

        return ImmutableMap.of("productId", productId, "url", String.format(IMAGE_URL, productId));
    }

    @Override
    public double fetchMarketPriceByProductId(String productId, String description) {

        if (productId == null || productId.isEmpty()) {
            return 0;
        }

        throttle();

        Product product;
        try {
            product = restTemplate.exchange(DETAILS_URL, HttpMethod.GET, new HttpEntity<>(headers()),
                    Product.class, productId).getBody();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            // An id the site does not know comes back a 400, not a 404
            log.warn("TCGplayer's site holds no product for {} [productId {}]; leaving it unpriced",
                    description, productId);
            return 0;
        } catch (RestClientException e) {
            throw failure(e, "price of " + description + " [productId " + productId + "]");
        }

        if (product == null || product.getMarketPrice() == null) {
            log.warn("TCGplayer's site has no market price for {} [productId {}]; leaving it unpriced",
                    description, productId);
            return 0;
        }

        return product.getMarketPrice();
    }

    @Override
    public Map<String, String> fetchProductConditionIdAndUrl(Card card) {

        String variant = variantOf(card.getIsFoil());

        for (Product product : candidates(card.getName(), card.getSet())) {
            for (SkuHistory sku : history(product.getProductId())) {

                if (variant.equalsIgnoreCase(sku.getVariant())
                        && card.getCardCondition().equalsIgnoreCase(sku.getCondition())
                        && "English".equalsIgnoreCase(sku.getLanguage())) {

                    return ImmutableMap.of(
                            "productConditionId", sku.getSkuId(),
                            "image", String.format(IMAGE_URL, product.getProductId()),
                            "groupId", String.valueOf(product.getSetId()),
                            "setName", product.getSetName(),
                            "abbreviation", product.getSetCode() == null ? "" : product.getSetCode());
                }
            }
        }

        throw new TcgServiceException(HttpStatus.BAD_REQUEST,
                "Failed to find card with name: " + card.getName() + " and set: " + card.getSet());
    }

    @Override
    public List<String> fetchVersions(String cardName) {

        Set<String> versions = search(cardName, CARDS, null, CARD_SEARCH_SIZE).stream()
                .filter(product -> isPrintingOf(product, cardName))
                .map(Product::getSetName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (versions.isEmpty()) {
            log.warn("TCGplayer's site found no card named {}", cardName);
            throw new TcgServiceException(HttpStatus.BAD_REQUEST, "Failed to find card with name: " + cardName + ".");
        }

        return new ArrayList<>(versions);
    }

    @Override
    public Map<String, Double> fetchCardMarketPrices(Collection<CardEntity> cards) {

        // One card per SKU: the same printing in the same condition sits in several decks
        Map<String, CardEntity> wanted = new LinkedHashMap<>();
        for (CardEntity card : cards) {
            if (card.getProductConditionId() != null) {
                wanted.putIfAbsent(card.getProductConditionId(), card);
            }
        }

        Map<String, Double> prices = new HashMap<>();
        Set<Long> fetched = new HashSet<>();

        try {

            // Printings already known first: one request prices every condition and finish of one
            Set<Long> known = wanted.keySet().stream()
                    .map(productIdsBySku::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (Long productId : known) {
                readPrices(productId, prices);
                fetched.add(productId);
            }

            // The rest are found by name and set, once per printing, and remembered for next time
            Map<String, List<CardEntity>> unresolved = new LinkedHashMap<>();
            for (CardEntity card : wanted.values()) {
                if (!prices.containsKey(card.getProductConditionId())
                        && !productIdsBySku.containsKey(card.getProductConditionId())) {
                    unresolved.computeIfAbsent(card.getName().toLowerCase() + "|" + card.getVersion().toLowerCase(),
                            any -> new ArrayList<>()).add(card);
                }
            }

            for (List<CardEntity> printing : unresolved.values()) {

                CardEntity first = printing.get(0);

                for (Product product : candidates(first.getName(), first.getVersion())) {

                    if (fetched.add(product.getProductId())) {
                        readPrices(product.getProductId(), prices);
                    }

                    if (printing.stream().allMatch(card -> prices.containsKey(card.getProductConditionId()))) {
                        break;
                    }
                }
            }

        } catch (TcgServiceException e) {
            /*
             * Most likely the site turning us away part of the way through. What was priced
             * before that is still good, so it is kept; everything else keeps its stored price.
             */
            log.error("Stopped pricing cards from TCGplayer's site after {} of {}; the rest keep their stored prices",
                    prices.size(), wanted.size());
        }

        // Only what was asked for. A product's history prices conditions nobody holds, too
        prices.keySet().retainAll(wanted.keySet());

        for (CardEntity card : wanted.values()) {
            if (!prices.containsKey(card.getProductConditionId())) {
                log.warn("TCGplayer's site gave no price for {} [productConditionId {}]; keeping its stored price",
                        card.describe(), card.getProductConditionId());
            }
        }

        log.info("Priced {} of {} cards from TCGplayer's site over {} products", prices.size(), wanted.size(), fetched.size());

        return prices;
    }

    /*
     * The products in a set that could be this card, best first: the exact name, then its
     * variants - "Forest (276)", "Sheoldred, the Apocalypse (Textured Foil)" - in search order.
     */
    private List<Product> candidates(String name, String setName) {

        List<Product> printings = search(name, CARDS, setName, CARD_SEARCH_SIZE).stream()
                .filter(product -> isPrintingOf(product, name))
                .collect(Collectors.toList());

        printings.sort(Comparator.comparing(product -> !name.trim().equalsIgnoreCase(product.getProductName())));

        return printings;
    }

    private static boolean isPrintingOf(Product product, String name) {

        String productName = product.getProductName() == null ? "" : product.getProductName().toLowerCase();
        String wanted = name.trim().toLowerCase();

        return productName.equals(wanted) || productName.startsWith(wanted + " (");
    }

    /** Every SKU of a product with its latest price, remembering which product each SKU is of. */
    private void readPrices(Long productId, Map<String, Double> prices) {

        for (SkuHistory sku : history(productId)) {

            productIdsBySku.put(sku.getSkuId(), productId);

            Double price = sku.latestMarketPrice();
            if (price != null) {
                prices.put(sku.getSkuId(), price);
            }
        }
    }

    private List<SkuHistory> history(Long productId) {

        throttle();

        HistoryResponse response;
        try {
            response = restTemplate.exchange(HISTORY_URL, HttpMethod.GET, new HttpEntity<>(headers()),
                    HistoryResponse.class, productId).getBody();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            log.warn("TCGplayer's site holds no price history for productId {}", productId);
            return Collections.emptyList();
        } catch (RestClientException e) {
            throw failure(e, "price history of productId " + productId);
        }

        return response == null || response.getResult() == null ? Collections.emptyList() : response.getResult();
    }

    private List<Product> search(String query, String productType, String setName, int size) {

        URI uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("q", "{q}")
                .queryParam("isList", false)
                .encode()
                .buildAndExpand(query)
                .toUri();

        Map<String, Object> term = new HashMap<>();
        term.put("productLineName", Collections.singletonList("magic"));
        term.put("productTypeName", Collections.singletonList(productType));
        if (setName != null) {
            term.put("setName", Collections.singletonList(setName));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("from", 0);
        body.put("size", size);
        body.put("filters", Collections.singletonMap("term", term));

        throttle();

        SearchResponse response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers()),
                    SearchResponse.class).getBody();
        } catch (RestClientException e) {
            throw failure(e, productType + " named " + query + (setName == null ? "" : " in " + setName));
        }

        List<Product> products = response == null ? null : response.products();

        return products == null ? Collections.emptyList() : products;
    }

    private static String variantOf(Boolean foil) {
        return Boolean.TRUE.equals(foil) ? "Foil" : "Normal";
    }

    private synchronized void throttle() {

        long wait = lastRequestAt + MIN_MS_BETWEEN_REQUESTS - System.currentTimeMillis();

        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastRequestAt = System.currentTimeMillis();
    }

    /*
     * Anything other than "no such product" is about us rather than the product - most likely
     * the site has started turning scripts away - so it is thrown, and a refresh stops asking.
     */
    private TcgServiceException failure(RestClientException e, String what) {

        log.error("TCGplayer's site would not answer for {}. It may have started refusing scripted requests.", what, e);

        return new TcgServiceException(HttpStatus.BAD_GATEWAY,
                "TCGplayer's website could not be read for " + what + ".", e);
    }

    private HttpHeaders headers() {

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ORIGIN, "https://www.tcgplayer.com");
        headers.set(HttpHeaders.REFERER, "https://www.tcgplayer.com/");
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        return headers;
    }

    private static RestTemplate buildRestTemplate() {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return new RestTemplate(requestFactory);
    }

    /** The search answers in results nested one level deep: one block per query, then its hits. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SearchResponse {

        private List<SearchResult> results;

        List<Product> products() {
            return results == null || results.isEmpty() ? null : results.get(0).getResults();
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SearchResult {
        private List<Product> results;
    }

    /** One shape for a search hit and a product page alike; arrives with ids as 541235.0. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Product {
        private Long productId;
        private String productName;
        private Double marketPrice;
        private Integer setId;
        private String setName;
        private String setCode;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HistoryResponse {
        private List<SkuHistory> result;
    }

    /** One SKU - a printing in one finish, condition and language - with its prices by day. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SkuHistory {

        private String skuId;
        private String variant;
        private String language;
        private String condition;
        private List<Bucket> buckets;

        /** The most recent day's market price; absent where there is none, or it is zero. */
        Double latestMarketPrice() {

            if (buckets == null) {
                return null;
            }

            // ISO dates, so the latest sorts last as a string
            return buckets.stream()
                    .filter(bucket -> bucket.getBucketStartDate() != null && bucket.getMarketPrice() != null)
                    .max(Comparator.comparing(Bucket::getBucketStartDate))
                    .map(bucket -> Double.parseDouble(bucket.getMarketPrice()))
                    .filter(price -> price > 0)
                    .orElse(null);
        }
    }

    /** Numbers arrive as strings here. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Bucket {
        private String marketPrice;
        private String bucketStartDate;
    }

}
