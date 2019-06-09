package keifer.converter;

import keifer.api.model.DeckSnapshot;
import keifer.persistence.model.DeckSnapshotEntity;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeckSnapshotConverter {

    public DeckSnapshot convert(DeckSnapshotEntity source) {

        return DeckSnapshot.builder()
                .purchasePrice(source.getPurchasePrice())
                .value(source.getValue())
                .timestamp(source.getTimestamp())
                .build();

    }

}
