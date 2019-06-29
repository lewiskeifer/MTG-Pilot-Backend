package keifer.persistence.model;

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
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Lob
    @Column(columnDefinition = "BINARY(64)", nullable = false)
    private byte[] password;

    @Lob
    @Column(columnDefinition = "BINARY(16)", nullable = false)
    private byte[] salt;

    @Column(nullable = false, unique = true)
    private String email;

    @Builder.Default
    @OneToMany(mappedBy = "userEntity", cascade = CascadeType.ALL)
    private List<DeckEntity> deckEntities = new ArrayList<>();

}
