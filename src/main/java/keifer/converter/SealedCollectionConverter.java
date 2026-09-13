package keifer.converter;

import keifer.api.model.SealedCollection;
import keifer.persistence.model.SealedCollectionEntity;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SealedCollectionConverter {

    private final SealedConverter sealedConverter;
    private final SealedCollectionSnapshotConverter sealedCollectionSnapshotConverter;

    public SealedCollectionConverter(@NonNull SealedConverter sealedConverter, @NonNull SealedCollectionSnapshotConverter sealedCollectionSnapshotConverter) {
        this.sealedConverter = sealedConverter;
        this.sealedCollectionSnapshotConverter = sealedCollectionSnapshotConverter;
    }

    public SealedCollection convert(SealedCollectionEntity source) {
        return convert(source, Collections.emptyMap());
    }

    /** Converts a collection, giving each product the price history recorded for it. */
    public SealedCollection convert(SealedCollectionEntity source,
                                    Map<String, Map<String, Double>> productBaselines) {

        return SealedCollection.builder()
                .id(source.getId())
                .name(source.getName())
                .sortOrder(source.getSortOrder())
                .sealed(source.getSealedEntities().stream()
                        .map(sealed -> sealedConverter.convert(sealed, productBaselines)).collect(Collectors.toList()))
                .sealedCollectionSnapshots(source.getSealedCollectionSnapshotEntities().stream()
                        .map(sealedCollectionSnapshotConverter::convert).collect(Collectors.toList()))
                .build();
    }
}
