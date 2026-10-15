package keifer.service.scryfall;

import com.fasterxml.jackson.annotation.JsonProperty;
import keifer.service.model.CardCondition;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market prices from Scryfall, which is free, needs no credentials, and sources its dollar
 * prices from TCGplayer.
 *
 * Two differences from the TCGplayer client this replaces, both of which shape the code below:
 *
 * Scryfall has never heard of a product-condition id, so a card is found by name and set. Set
 * codes are its own, so the catalogue's set names are matched against them once and cached.
 *
 * Scryfall prices a printing, near mint, and nothing else. A played card is priced from the near
 * mint figure through the multipliers in CONDITION_MULTIPLIER, which are a convention rather
 * than an observed market, and are logged as such the first time they are used.
 */
@Slf4j
@Service
public class ScryfallServiceImpl implements ScryfallService {

    private static final String SCRYFALL_URL = "https://api.scryfall.com";

    /*
     * Scryfall ask for an identifying User-Agent and about a tenth of a second between calls.
     * The default one a RestTemplate sends is refused.
     */
    private static final String USER_AGENT = "MTGPilot/1.0 (https://mtgpilot.com)";
    private static final long PAUSE_BETWEEN_CALLS_MS = 100;

    /** The most identifiers one /cards/collection call will take. */
    private static final int BATCH_SIZE = 75;

    /*
     * What a played copy is worth against a near mint one.
     *
     * Scryfall prices near mint alone, so these carry that figure down to the condition actually
     * held. They are a convention - the shape of the discount, not a measurement of it - and the
     * one place to change if they read wrong against what cards actually sell for.
     */
    private static final Map<CardCondition, Double> CONDITION_MULTIPLIER;

    static {
        Map<CardCondition, Double> multipliers = new HashMap<>();
        multipliers.put(CardCondition.NEAR_MINT, 1.00);
        multipliers.put(CardCondition.LIGHT_PLAY, 0.85);
        multipliers.put(CardCondition.MODERATE_PLAY, 0.70);
        multipliers.put(CardCondition.HEAVY_PLAY, 0.50);
        multipliers.put(CardCondition.DAMAGED, 0.35);
        CONDITION_MULTIPLIER = Collections.unmodifiableMap(multipliers);
    }

    /** Set name, flattened for matching, against Scryfall's own code for it. */
    private final Map<String, String> setCodesByName = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Double> fetchMarketPrices(Collection<ScryfallPriceRequest> cards) {

        Map<String, Double> prices = new HashMap<>();

        if (cards == null || cards.isEmpty()) {
            return prices;
        }

        loadSetCodes();

        /*
         * One lookup per printing, however many copies in however many conditions are held of
         * it. A collection with four binders of commons asks after a few thousand cards and a
         * good deal fewer printings.
         */
        Map<String, Printing> printings = new LinkedHashMap<>();
        Map<String, List<ScryfallPriceRequest>> requests = new HashMap<>();

        for (ScryfallPriceRequest card : cards) {

            String setCode = setCodesByName.get(flatten(card.getSetName()));

            if (setCode == null) {
                log.warn("No Scryfall set is named {}, so {} cannot be priced",
                        card.getSetName(), card.getName());
                continue;
            }

            String key = printingKey(card.getName(), setCode);
            printings.putIfAbsent(key, new Printing(card.getName(), setCode));
            requests.computeIfAbsent(key, any -> new ArrayList<>()).add(card);
        }

        List<String> keys = new ArrayList<>(printings.keySet());

        for (int start = 0; start < keys.size(); start += BATCH_SIZE) {

            List<String> batch = keys.subList(start, Math.min(start + BATCH_SIZE, keys.size()));
            priceBatch(batch, printings, requests, prices);

            pause();
        }

        log.info("Scryfall priced {} of {} cards, over {} printings", prices.size(), cards.size(), keys.size());

        return prices;
    }

    private void priceBatch(List<String> keys,
                            Map<String, Printing> printings,
                            Map<String, List<ScryfallPriceRequest>> requests,
                            Map<String, Double> prices) {

        List<Map<String, String>> identifiers = new ArrayList<>();
        for (String key : keys) {
            Printing printing = printings.get(key);
            Map<String, String> identifier = new HashMap<>();
            identifier.put("name", printing.getName());
            identifier.put("set", printing.getSetCode());
            identifiers.add(identifier);
        }

        CollectionResponse response = post(identifiers);

        if (response == null) {
            return;
        }

        for (ScryfallCard card : response.cardsFound()) {

            // Matched back by what came home rather than by position: the order is not promised
            List<ScryfallPriceRequest> matching = requests.get(printingKey(card.getName(), card.getSet()));

            if (matching == null) {
                log.debug("Scryfall returned {} ({}), which nothing asked for", card.getName(), card.getSet());
                continue;
            }

            for (ScryfallPriceRequest request : matching) {
                priceOf(card, request).ifPresent(price -> prices.put(request.getKey(), price));
            }
        }

        for (Map<String, String> missing : response.cardsNotFound()) {
            log.warn("Scryfall holds no card named {} in set {}", missing.get("name"), missing.get("set"));
        }
    }

    /*
     * The near mint figure for the finish held, brought down to the condition held. Absent where
     * Scryfall carries no dollar price for that finish - a card that only ever existed in foil
     * has no plain price, and a zero there would read as worthless rather than unknown.
     */
    private java.util.Optional<Double> priceOf(ScryfallCard card, ScryfallPriceRequest request) {

        Prices prices = card.getPrices();
        String nearMint = prices == null ? null : (request.isFoil() ? prices.getUsdFoil() : prices.getUsd());

        if (nearMint == null || nearMint.isEmpty()) {
            log.warn("Scryfall has no {} price for {} ({})",
                    request.isFoil() ? "foil" : "non-foil", card.getName(), card.getSet());
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(Double.parseDouble(nearMint) * conditionMultiplier(request.getCondition()));
    }

    private CollectionResponse post(List<Map<String, String>> identifiers) {

        Map<String, Object> body = new HashMap<>();
        body.put("identifiers", identifiers);

        try {

            ResponseEntity<CollectionResponse> response = restTemplate.exchange(
                    SCRYFALL_URL + "/cards/collection", HttpMethod.POST,
                    new HttpEntity<>(body, headers()), CollectionResponse.class);

            return response.getBody();

        } catch (RestClientException e) {
            /*
             * One batch of seventy five, not the whole refresh. Logged and skipped so a blip
             * costs those cards their update rather than costing every card its update.
             */
            log.error("Scryfall refused a batch of {} printings; leaving them unpriced", identifiers.size(), e);
            return null;
        }
    }

    /*
     * Scryfall's set codes against the names this application knows sets by. Matched on the name
     * flattened to letters and digits, because the two catalogues punctuate differently - one
     * writes "The Brothers' War" where the other writes "The Brothers War".
     */
    @Scheduled(cron = "0 30 2 * * *", zone = "America/New_York")
    public void loadSetCodes() {

        if (!setCodesByName.isEmpty()) {
            return;
        }

        try {

            ResponseEntity<SetsResponse> response = restTemplate.exchange(
                    SCRYFALL_URL + "/sets", HttpMethod.GET, new HttpEntity<>(headers()), SetsResponse.class);

            SetsResponse body = response.getBody();

            if (body == null || body.getData() == null) {
                log.error("Scryfall returned no sets, so no card can be placed in one");
                return;
            }

            body.getData().forEach(set -> setCodesByName.put(flatten(set.getName()), set.getCode()));

            log.info("Loaded {} Scryfall sets", setCodesByName.size());

        } catch (RestClientException e) {
            log.error("Could not load Scryfall's sets; cards cannot be priced until this succeeds", e);
        }
    }

    private HttpHeaders headers() {

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        return headers;
    }

    /** The courtesy Scryfall ask for between calls, since this makes a run of them. */
    private void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_CALLS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /*
     * Package private, and tested: a condition missing from the table would quietly price a
     * damaged card as though it were near mint, which no log line would ever mention.
     */
    static double conditionMultiplier(CardCondition condition) {
        return CONDITION_MULTIPLIER.getOrDefault(condition, 1.00);
    }

    private static String printingKey(String name, String setCode) {
        return flatten(name) + "|" + (setCode == null ? "" : setCode.toLowerCase());
    }

    /*
     * Down to letters and digits, so punctuation, case and accents cannot keep two spellings of
     * one name apart. Taken off both sides of every comparison.
     */
    static String flatten(String text) {

        if (text == null) {
            return "";
        }

        return Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{Alnum}]", "")
                .toLowerCase();
    }

    @Data
    private static class Printing {
        private final String name;
        private final String setCode;
    }

    @Data
    @NoArgsConstructor
    private static class CollectionResponse {

        private List<ScryfallCard> data;

        @JsonProperty("not_found")
        private List<Map<String, String>> notFound;

        List<ScryfallCard> cardsFound() {
            return data == null ? Collections.emptyList() : data;
        }

        List<Map<String, String>> cardsNotFound() {
            return notFound == null ? Collections.emptyList() : notFound;
        }
    }

    @Data
    @NoArgsConstructor
    private static class ScryfallCard {
        private String name;
        private String set;
        private Prices prices;
    }

    @Data
    @NoArgsConstructor
    private static class Prices {

        /** Dollar prices arrive as strings, and are null where Scryfall has none. */
        private String usd;

        @JsonProperty("usd_foil")
        private String usdFoil;
    }

    @Data
    @NoArgsConstructor
    private static class SetsResponse {
        private List<ScryfallSet> data;
    }

    @Data
    @NoArgsConstructor
    private static class ScryfallSet {
        private String code;
        private String name;
    }

}
