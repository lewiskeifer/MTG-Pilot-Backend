package keifer.api.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Deck {

    private String name;

    private Format format;

    private List<Card> cards;

}
