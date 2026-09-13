package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deck {

    private Long id;

    private String name;

    private String format;

    private Integer sortOrder;

    private List<Card> cards;

    private List<DeckSnapshot> deckSnapshots;

    /*
     * The day each of the cards' baseline prices was read, keyed by the same range day counts.
     * A window whose date is later than it asked for is one the price history does not reach
     * back far enough to cover, and the dashboard names the shorter span it really got.
     *
     * A property of the response rather than of this deck, and repeated on each of them.
     */
    private Map<String, String> baselineDates;

}
