package keifer.converter;

import keifer.api.model.Sealed;
import keifer.persistence.model.SealedEntity;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SealedConverter {

    public Sealed convert(SealedEntity source) {

        return Sealed.builder()
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
