package keifer.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import keifer.api.model.Login;
import keifer.api.model.PasswordChange;
import keifer.api.model.User;
import keifer.converter.UserConverter;
import keifer.persistence.UserRepository;
import keifer.persistence.model.UserEntity;
import keifer.service.model.YAMLConfig;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private YAMLConfig yamlConfig;

    public UserServiceImpl(@NonNull UserRepository userRepository,
                           @NonNull UserConverter userConverter,
                           YAMLConfig yamlConfig) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.yamlConfig = yamlConfig;
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll().stream().map(userConverter::convert).collect(Collectors.toList());
    }

    @Override
    public User getUser(Long userId) {
        return userConverter.convert(userRepository.findOneById(userId));
    }

    @SneakyThrows
    @Override
    public User login(Login login) {

        if (login.getUsername() == null || login.getPassword() == null) {
            throw new ServletException("Invalid login.");
        }

        String username = login.getUsername();
        String password = login.getPassword();

        UserEntity userEntity = userRepository.findOneByUsername(username);

        if (userEntity == null) {
            throw new ServletException("Username not found.");
        }

        if (!passwordMatches(userEntity, password)) {
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

    @SneakyThrows
    @Override
    public User saveUser(User user) {

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

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(salt);

        UserEntity userEntity = UserEntity.builder()
                .username(username)
                .password(md.digest(password.getBytes(StandardCharsets.UTF_8)))
                .salt(salt)
                .email(email)
                .build();
        return userConverter.convert(userRepository.save(userEntity));
    }

    /*
     * Proves the current password before setting a new one. This used to take a username and a
     * new password and nothing else, and sits on a route no token guards, so anyone who knew a
     * username could take over the account.
     *
     * The same answer for an unknown user as for a wrong password, so this cannot be used to
     * find out which usernames exist.
     */
    @SneakyThrows
    @Override
    public User resetPassword(PasswordChange passwordChange) {

        String username = passwordChange.getUsername();
        String currentPassword = passwordChange.getCurrentPassword();
        String password = passwordChange.getNewPassword();

        if (StringUtils.isEmpty(username) ||
                StringUtils.isEmpty(currentPassword) ||
                StringUtils.isEmpty(password)) {
            throw new ServletException("Username, current password and new password are all required.");
        }

        UserEntity userEntity = userRepository.findOneByUsername(username);
        if (userEntity == null || !passwordMatches(userEntity, currentPassword)) {
            throw new ServletException("Invalid username or password.");
        }

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(salt);

        userEntity.setPassword(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        userEntity.setSalt(salt);

        return userConverter.convert(userRepository.save(userEntity));

    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    /** Whether a password, salted as this user's was, digests to the one stored. */
    private static boolean passwordMatches(UserEntity userEntity, String password) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(userEntity.getSalt());

        // Constant time, so how long a wrong guess takes says nothing about how close it was
        return MessageDigest.isEqual(md.digest(password.getBytes(StandardCharsets.UTF_8)), userEntity.getPassword());
    }

}
