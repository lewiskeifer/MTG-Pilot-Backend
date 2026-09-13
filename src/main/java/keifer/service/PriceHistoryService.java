package keifer.service;

import keifer.service.model.PriceBaselines;
import keifer.service.model.PriceKind;

import java.util.Map;

public interface PriceHistoryService {

    /** Records today's market price for every product priced by a refresh. */
    void record(PriceKind kind, Map<String, Double> marketPrices);

    /** What each product was worth at the start of each window the dashboard offers. */
    PriceBaselines baselines(PriceKind kind);

}
