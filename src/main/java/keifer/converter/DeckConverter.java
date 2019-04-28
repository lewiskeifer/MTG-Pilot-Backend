package keifer.converter;

import keifer.api.model.Deck;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
public class DeckConverter {

    private final CardConverter cardConverter;

    public DeckConverter(@NonNull CardConverter cardConverter) {
        this.cardConverter = cardConverter;
    }

    public Deck convert(DeckEntity source) {

        return Deck.builder().name(source.getName())
                .format(source.getFormat())
                .cards(source.getCardEntities().stream().map(cardConverter::convert).collect(Collectors.toList()))
                .build();

    }

}
