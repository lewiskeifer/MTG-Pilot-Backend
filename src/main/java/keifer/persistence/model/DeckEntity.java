package keifer.persistence.model;

import keifer.service.model.DeckFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private DeckFormat deckFormat;

    @Builder.Default
    @OneToMany(mappedBy = "deckEntity", cascade = CascadeType.ALL)
    private List<CardEntity> cardEntities = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "deckEntity", cascade = CascadeType.ALL)
    private List<DeckSnapshotEntity> deckSnapshotEntities = new ArrayList<>();

}
