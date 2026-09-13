package keifer.persistence.model;

import keifer.service.model.PriceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * One day's market price for one TCG product.
 *
 * Kept per product rather than per owned card: the same printing sits in several binders and
 * belongs to several users, and its price is a fact about the product, not about anyone's copy
 * of it. One row a day per product held, against one row per card per day, is the difference
 * between a table that grows with the catalogue and one that grows with everybody's shelves.
 *
 * Deck and collection snapshots carry the totals; this carries the parts, so the dashboard can
 * say which individual cards moved over a window and not just which decks did.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        // One price per product per day. The refresh overwrites today's row rather than appending.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_price_snapshot_product_day",
                columnNames = {"kind", "product_key", "snapshot_date"}),
        // Baselines are read a whole day at a time: every price recorded on the window's start date
        indexes = @Index(
                name = "idx_price_snapshot_kind_date",
                columnList = "kind, snapshot_date"))
public class PriceSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    /*
     * Product-condition id for a card, product id for a sealed product.
     *
     * Every column below is named outright. The unique key and the index are resolved against
     * logical column names, which are the property names until a name is given here - naming
     * only some of them leaves the constraint looking for a column that does not exist yet.
     *
     * Both key columns are also given a length. This schema is MyISAM - MySQL5Dialect, not the
     * InnoDB one - where a key may total 1000 bytes, and a text column left to default to
     * varchar(255) is 1020 of them on its own once the charset is counted. TCG ids are short.
     */
    @Column(name = "product_key", nullable = false, length = 40)
    private String productKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private PriceKind kind;

    /*
     * The day the price was read, in Eastern time like every other snapshot here - the deployed
     * server runs on UTC, where an evening refresh would be dated tomorrow.
     */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate date;

    /** What one copy was worth. Quantities belong to the card rows, not here. */
    @Column(name = "market_price", nullable = false)
    private Double marketPrice;

}
