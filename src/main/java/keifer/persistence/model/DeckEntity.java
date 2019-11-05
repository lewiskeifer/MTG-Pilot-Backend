package keifer.persistence.model;

import keifer.service.model.DeckFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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

    @Column(nullable = false)
    private Integer sortOrder;

    @ManyToOne
    @JoinColumn(name = "user_entity_id")
    private UserEntity userEntity;

    @Builder.Default
    @Fetch(value = FetchMode.SUBSELECT)
    @OneToMany(mappedBy = "deckEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CardEntity> cardEntities = new ArrayList<>();

    @Builder.Default
    @Fetch(value = FetchMode.SUBSELECT)
    @OneToMany(mappedBy = "deckEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DeckSnapshotEntity> deckSnapshotEntities = new ArrayList<>();

}
