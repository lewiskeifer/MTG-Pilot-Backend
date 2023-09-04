package keifer.persistence.model;

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
public class SealedCollectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer sortOrder;

    @ManyToOne
    @JoinColumn(name = "user_entity_id")
    private UserEntity userEntity;

    @Builder.Default
    @Fetch(value = FetchMode.SUBSELECT)
    @OneToMany(mappedBy = "sealedCollectionEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<SealedEntity> sealedEntities = new ArrayList<>();

    @Builder.Default
    @Fetch(value = FetchMode.SUBSELECT)
    @OneToMany(mappedBy = "sealedCollectionEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<SealedCollectionSnapshotEntity> sealedCollectionSnapshotEntities = new ArrayList<>();

}
