package keifer.controller;

import io.swagger.annotations.Api;
import keifer.service.ManagerService;
import lombok.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@Api
@RequestMapping("/manager")
@RestController
public class ManagerController {

    private final ManagerService managerService;

    public ManagerController(@NonNull ManagerService managerService) {
        this.managerService = managerService;
    }

    @GetMapping
    public String index() {
        return managerService.returnData();
    }

}
