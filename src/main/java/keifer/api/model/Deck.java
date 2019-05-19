package keifer.api.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Deck {

    private Long id;

    private String name;

    private String format;

    private List<Card> cards;

}
