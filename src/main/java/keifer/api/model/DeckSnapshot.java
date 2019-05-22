package keifer.api.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DeckSnapshot {

    private Double value;
    private LocalDateTime timestamp;
}
