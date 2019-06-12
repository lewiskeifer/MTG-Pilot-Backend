package keifer.service;

import keifer.api.model.User;
import keifer.converter.CardConverter;
import keifer.converter.DeckConverter;
import keifer.converter.UserConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.UserRepository;
import keifer.persistence.model.UserEntity;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserConverter userConverter;

    public UserServiceImpl(@NonNull UserRepository userRepository, @NonNull UserConverter userConverter) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll().stream().map(userConverter::convert).collect(Collectors.toList());
    }

    @Override
    public User getUser(Long userId) {
        return userConverter.convert(userRepository.findOneById(userId));
    }

    @Override
    public User saveUser(User user) {
        UserEntity userEntity = UserEntity.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .email(user.getEmail())
                .build();
        return userConverter.convert(userRepository.save(userEntity));
    }

    @Override
    public void deleteUser(Long userId) {
        //TODO
    }
}
