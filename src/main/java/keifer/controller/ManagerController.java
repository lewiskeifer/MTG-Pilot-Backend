package keifer.controller;

import io.swagger.annotations.Api;
import keifer.api.model.Deck;
import keifer.service.DataMigrationService;
import keifer.service.ManagerService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api
@RequestMapping("/manager")
@RestController
public class ManagerController {

    private final ManagerService managerService;
    private final DataMigrationService dataMigrationService;

    public ManagerController(@NonNull ManagerService managerService, @NonNull DataMigrationService dataMigrationService) {
        this.managerService = managerService;
        this.dataMigrationService = dataMigrationService;
    }

    @GetMapping
    public List<Deck> getDecks() {
        return managerService.returnData();
    }

    @GetMapping("/migrate")
    public void migrate() {
        dataMigrationService.migrateData();
    }

}
