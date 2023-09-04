package keifer.converter;

import keifer.api.model.SealedCollectionSnapshot;
import keifer.persistence.model.SealedCollectionSnapshotEntity;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SealedCollectionSnapshotConverter {

    public SealedCollectionSnapshot convert(SealedCollectionSnapshotEntity source) {

        return SealedCollectionSnapshot.builder()
                .purchasePrice(source.getPurchasePrice())
                .value(source.getValue())
                .timestamp(source.getTimestamp())
                .build();
    }

    public SealedCollectionSnapshotEntity convert(SealedCollectionSnapshot source) {

        return SealedCollectionSnapshotEntity.builder()
                .purchasePrice(source.getPurchasePrice())
                .value(source.getValue())
                .timestamp(source.getTimestamp())
                .build();
    }

}
