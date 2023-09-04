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
public class SealedCollection {

    private Long id;

    private String name;

    private Integer sortOrder;

    private List<Sealed> sealed;

    private List<SealedCollectionSnapshot> sealedCollectionSnapshots;

}
