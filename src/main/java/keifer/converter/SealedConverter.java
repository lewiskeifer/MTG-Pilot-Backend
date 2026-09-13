package keifer.converter;

import keifer.api.model.Sealed;
import keifer.persistence.model.SealedEntity;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;

@Configuration
public class SealedConverter {

    public Sealed convert(SealedEntity source) {
        return convert(source, Collections.emptyMap());
    }

    /** Converts a product and hangs its recorded prices off it, keyed by TCG product id. */
    public Sealed convert(SealedEntity source, Map<String, Map<String, Double>> baselines) {

        return Sealed.builder()
                .baselinePrices(baselines.get(source.getProductId()))
                .id(source.getId())
                .name(source.getName())
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .url(source.getUrl())
                .marketPrice(source.getMarketPrice())
                .build();
    }

    public SealedEntity convert(Sealed source) {

        return SealedEntity.builder()
                .name(source.getName())
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .url(source.getUrl())
                .marketPrice(source.getMarketPrice())
                .build();
    }

}
