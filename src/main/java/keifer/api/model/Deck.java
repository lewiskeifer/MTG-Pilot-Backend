package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deck {

    private Long id;

    private String name;

    private String format;

    private List<Card> cards;

    private List<DeckSnapshot> deckSnapshots;

}
