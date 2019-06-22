package keifer.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import keifer.api.model.Login;
import keifer.api.model.User;
import keifer.converter.UserConverter;
import keifer.persistence.UserRepository;
import keifer.persistence.model.UserEntity;
import keifer.service.model.YAMLConfig;
import lombok.NonNull;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private final BasicTextEncryptor passwordEncoder;
    private YAMLConfig yamlConfig;

    public UserServiceImpl(@NonNull UserRepository userRepository,
                           @NonNull UserConverter userConverter,
                           YAMLConfig yamlConfig) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.yamlConfig = yamlConfig;
        this.passwordEncoder = new BasicTextEncryptor();
        this.passwordEncoder.setPasswordCharArray(yamlConfig.getSecretKey().toCharArray());
    }

    @Override
    public User login(Login login) throws ServletException {

        if (login.getUsername() == null || login.getPassword() == null) {
            throw new ServletException("Invalid login.");
        }

        String username = login.getUsername();
        String password = login.getPassword();

        UserEntity userEntity = userRepository.findOneByUsername(username);

        if (userEntity == null) {
            throw new ServletException("Username not found.");
        }

        if (!password.equals(passwordEncoder.decrypt(userEntity.getPassword()))) {
            throw new ServletException("Invalid login.");
        }

        String token = Jwts.builder()
                .setSubject(username)
                .claim("id", userEntity.getId())
                .setIssuedAt(new Date())
                .signWith(SignatureAlgorithm.HS256, yamlConfig.getSecretKey())
                .compact();

        User user = userConverter.convert(userEntity);
        user.setToken(token);

        return user;
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
    public User saveUser(User user) throws ServletException {

        String username = user.getUsername();
        String password = user.getPassword();
        String email = user.getEmail();

        if (StringUtils.isEmpty(username) ||
                StringUtils.isEmpty(password) ||
                StringUtils.isEmpty(email)) {
            throw new ServletException("Invalid registration.");
        }

        if (userRepository.findOneByUsername(username) != null) {
            throw new ServletException("Username: " + username + " already exists.");
        }

        if (userRepository.findOneByEmail(email) != null) {
            throw new ServletException("Email: " + email + " is already in use.");
        }

        UserEntity userEntity = UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encrypt(password))
                .email(email)
                .build();
        return userConverter.convert(userRepository.save(userEntity));
    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

}
