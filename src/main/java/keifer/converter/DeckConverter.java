package keifer.converter;

import keifer.api.model.Deck;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

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

        return Deck.builder()
                .id(source.getId())
                .name(source.getName())
                .format(source.getDeckFormat().toString())
                .sortOrder(source.getSortOrder())
                .cards(source.getCardEntities().stream()
                        .map(cardConverter::convert).collect(Collectors.toList()))
                .deckSnapshots(source.getDeckSnapshotEntities().stream()
                        .map(deckSnapshotConverter::convert).collect(Collectors.toList()))
                .build();
    }
}
