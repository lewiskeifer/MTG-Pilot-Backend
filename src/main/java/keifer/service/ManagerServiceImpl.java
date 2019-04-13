package keifer.service;

import org.springframework.stereotype.Service;

@Service
public class ManagerServiceImpl implements ManagerService {

    @Override
    public String returnData() {
        return "Data";
    }
}
