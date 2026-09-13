package keifer.converter;

import keifer.api.model.Deck;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class DeckConverter {

    private final CardConverter cardConverter;
    private final DeckSnapshotConverter deckSnapshotConverter;

    public DeckConverter(@NonNull CardConverter cardConverter, @NonNull DeckSnapshotConverter deckSnapshotConverter) {
        this.cardConverter = cardConverter;
        this.deckSnapshotConverter = deckSnapshotConverter;
    }

    public Deck convert(DeckEntity source) {
        return convert(source, Collections.emptyMap());
    }

    /** Converts a deck, giving each of its cards the price history recorded for its printing. */
    public Deck convert(DeckEntity source, Map<String, Map<String, Double>> cardBaselines) {

        return Deck.builder()
                .id(source.getId())
                .name(source.getName())
                .format(source.getDeckFormat().toString())
                .sortOrder(source.getSortOrder())
                .cards(source.getCardEntities().stream()
                        .map(card -> cardConverter.convert(card, cardBaselines)).collect(Collectors.toList()))
                .deckSnapshots(source.getDeckSnapshotEntities().stream()
                        .map(deckSnapshotConverter::convert).collect(Collectors.toList()))
                .build();
    }
}
