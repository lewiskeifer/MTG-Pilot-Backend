package keifer.api.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @ApiModelProperty(hidden = true)
    Long id;

    String username;
    String password;
    String email;
    String token;

    @ApiModelProperty(hidden = true)
    List<Deck> decks;

}
