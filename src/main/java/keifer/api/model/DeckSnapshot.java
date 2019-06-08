package keifer.api.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckSnapshot {

    private Double purchasePrice;
    private Double value;
    private LocalDateTime timestamp;
}
