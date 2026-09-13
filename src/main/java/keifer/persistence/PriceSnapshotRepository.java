package keifer.persistence;

import keifer.persistence.model.PriceSnapshotEntity;
import keifer.service.model.PriceKind;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceSnapshotRepository extends CrudRepository<PriceSnapshotEntity, Long> {

    List<PriceSnapshotEntity> findByKindAndDate(PriceKind kind, LocalDate date);

    /*
     * The latest day on or before a cutoff that actually has prices. A window's start date is
     * arithmetic - today minus ninety - and there is no row on it if the refresh did not run that
     * night, so every lookup snaps to a day that was recorded rather than assuming one.
     */
    PriceSnapshotEntity findTopByKindAndDateLessThanEqualOrderByDateDesc(PriceKind kind, LocalDate date);

    PriceSnapshotEntity findTopByKindOrderByDateAsc(PriceKind kind);

}
