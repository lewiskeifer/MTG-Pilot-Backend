package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Card;
import keifer.api.model.Deck;
import keifer.api.model.Sealed;
import keifer.api.model.SealedCollection;
import keifer.service.*;
import lombok.NonNull;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import java.util.List;
import java.util.Map;

@Api
@RequestMapping("/sealed")
@RestController
public class SealedController {

    private final UserService userService;
    private final SealedService sealedService;
    private final TcgService tcgService;

    public SealedController(@NonNull UserService userService,
                            @NonNull SealedService deckService,
                            @NonNull TcgService tcgService) {
        this.userService = userService;
        this.sealedService = deckService;
        this.tcgService = tcgService;
    }

    @GetMapping("/{userId}")
    public List<SealedCollection> getSealedCollections(@PathVariable("userId") Long userId) {
        return sealedService.getSealedCollections(userId);
    }

    @GetMapping("/{userId}/collection/{sealedId}")
    public SealedCollection getSealedCollection(@PathVariable("userId") Long userId,
                                                @PathVariable("sealedId") Long sealedId) {
        return sealedService.getSealedCollection(userId, sealedId);
    }

    @PutMapping("/{userId}")
    public SealedCollection saveSealedCollection(@PathVariable("userId") Long userId,
                                                 @RequestBody SealedCollection sealedCollection) throws ServletException {
        return sealedService.saveSealedCollection(userId, sealedCollection);
    }

    @PutMapping("/{userId}/collection/{sealedId}/sealed")
    public Sealed saveSealed(@PathVariable("userId") Long userId,
                             @PathVariable("sealedId") Long sealedId,
                             @RequestBody Sealed sealed) {
        return sealedService.saveSealed(userId, sealedId, sealed);
    }

    @PutMapping("/{userId}/collection/{sealedId}/ordering")
    public SealedCollection saveSealedCollectionOrdering(@PathVariable("userId") Long userId,
                                                         @PathVariable("sealedId") Long sealedId,
                                                         @RequestBody Integer order) {
        return sealedService.saveSealedCollectionOrdering(userId, sealedId, order);
    }

    @PutMapping("/{userId}/collection/{sealedId}/refresh")
    public void createSealedCollectionSnapshot(@PathVariable("userId") Long userId,
                                               @PathVariable("sealedId") Long sealedId) {
        sealedService.createSealedCollectionSnapshot(userId, sealedId);
    }

}
