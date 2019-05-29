package keifer.api.model;

import lombok.*;

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
