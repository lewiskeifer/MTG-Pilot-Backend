package keifer.converter;

import keifer.api.model.User;
import keifer.persistence.model.UserEntity;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
public class UserConverter {

    private final DeckConverter deckConverter;

    public UserConverter(@NonNull DeckConverter deckConverter) {
        this.deckConverter = deckConverter;
    }

    public User convert(UserEntity source) {

        return User.builder()
                .id(source.getId())
                .username(source.getUsername())
                .email(source.getEmail())
                .decks(source.getDeckEntities().stream().map(deckConverter::convert).collect(Collectors.toList()))
                .build();
    }
}
