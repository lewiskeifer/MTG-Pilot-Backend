package keifer.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableMap;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Two endpoints, both found by watching the website work: the search behind its search bar, and
 * the product details behind a product page. Neither needs a key. Both answer in the same
 * product ids the API used, so the ids already stored against sealed products carry over as is.
 */
@Slf4j
@Service
public class TcgPlayerSiteServiceImpl implements TcgPlayerSiteService {

    private static final String SEARCH_URL = "https://mp-search-api.tcgplayer.com/v1/search/request";
    private static final String DETAILS_URL = "https://mp-search-api.tcgplayer.com/v1/product/{productId}/details";
    private static final String IMAGE_URL = "https://tcgplayer-cdn.tcgplayer.com/product/%s_200w.jpg";

    // These endpoints serve a browser, so ask like one
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private static final int SEARCH_SIZE = 10;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final RestTemplate restTemplate = buildRestTemplate();

    @Override
    public Map<String, String> fetchProductIdAndUrl(String name) {

        URI uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("q", "{q}")
                .queryParam("isList", false)
                .encode()
                .buildAndExpand(name)
                .toUri();

        Map<String, Object> term = new HashMap<>();
        term.put("productLineName", Collections.singletonList("magic"));
        term.put("productTypeName", Collections.singletonList("Sealed Products"));

        Map<String, Object> body = new HashMap<>();
        body.put("from", 0);
        body.put("size", SEARCH_SIZE);
        body.put("filters", Collections.singletonMap("term", term));

        SearchResponse response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers()),
                    SearchResponse.class).getBody();
        } catch (RestClientException e) {
            throw failure(e, "sealed product named " + name);
        }

        List<Product> products = response == null ? null : response.products();

        if (products == null || products.isEmpty()) {
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
    }

}
