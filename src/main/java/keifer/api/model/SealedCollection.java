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
public class SealedCollection {

    private Long id;

    private String name;

    private Integer sortOrder;

    private List<Sealed> sealed;

    private List<SealedCollectionSnapshot> sealedCollectionSnapshots;

    /** As on a deck: the day each of the products' baseline prices was read. */
    private Map<String, String> baselineDates;

}
