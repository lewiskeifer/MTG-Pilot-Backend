package keifer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import keifer.api.model.Login;
import keifer.api.model.PasswordChange;
import keifer.api.model.User;
import keifer.converter.UserConverter;
import keifer.persistence.UserRepository;
import keifer.persistence.model.UserEntity;
import keifer.service.model.YAMLConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registering, signing in and resetting a password.
 *
 * Passwords are stored as salted SHA-512 digests, so these go through saveUser to produce a
 * stored user rather than hand-building digests, and then sign in against what was stored.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class UserServiceImplTest {

    private static final String SECRET = "test-secret";

    @Mock private UserRepository userRepository;
    @Mock private UserConverter userConverter;

    private UserServiceImpl userService;

    @Before
    public void setUp() {

        YAMLConfig yamlConfig = new YAMLConfig();
        yamlConfig.setSecretKey(SECRET);

        userService = new UserServiceImpl(userRepository, userConverter, yamlConfig);

        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userConverter.convert(any())).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            return User.builder().id(entity.getId()).username(entity.getUsername()).email(entity.getEmail()).build();
        });
    }

    // ---- Registering ----

    @Test
    public void refusesARegistrationMissingAnything() {

        assertThatThrownBy(() -> userService.saveUser(user(null, "pw", "a@b.c"))).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> userService.saveUser(user("ann", "", "a@b.c"))).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> userService.saveUser(user("ann", "pw", null))).isInstanceOf(ServletException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    public void refusesATakenUsernameOrEmail() {

        when(userRepository.findOneByUsername("ann")).thenReturn(UserEntity.builder().username("ann").build());
        when(userRepository.findOneByEmail("taken@b.c")).thenReturn(UserEntity.builder().email("taken@b.c").build());

        assertThatThrownBy(() -> userService.saveUser(user("ann", "pw", "new@b.c")))
                .isInstanceOf(ServletException.class).hasMessageContaining("ann");
        assertThatThrownBy(() -> userService.saveUser(user("bob", "pw", "taken@b.c")))
                .isInstanceOf(ServletException.class).hasMessageContaining("taken@b.c");

        verify(userRepository, never()).save(any());
    }

    @Test
    public void neverStoresThePasswordAsGiven() {

        UserEntity stored = register("ann", "hunter2");

        assertThat(stored.getSalt()).hasSize(16);
        assertThat(stored.getPassword()).hasSize(64);
        assertThat(new String(stored.getPassword(), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("hunter2");
    }

    @Test
    public void saltsEveryUserDifferently() {

        UserEntity first = register("ann", "same");
        UserEntity second = register("bob", "same");

        assertThat(first.getSalt()).isNotEqualTo(second.getSalt());
        assertThat(first.getPassword()).isNotEqualTo(second.getPassword());
    }

    // ---- Signing in ----

    @Test
    public void signsInWithTheRightPasswordAndIssuesATokenNamingTheUser() {

        UserEntity stored = register("ann", "hunter2");
        stored.setId(42L);
        when(userRepository.findOneByUsername("ann")).thenReturn(stored);

        User user = userService.login(new Login("ann", "hunter2"));

        Claims claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(user.getToken()).getBody();
        assertThat(claims.getSubject()).isEqualTo("ann");
        assertThat(((Number) claims.get("id")).longValue()).isEqualTo(42L);
    }

    @Test
    public void refusesTheWrongPassword() {

        UserEntity stored = register("ann", "hunter2");
        when(userRepository.findOneByUsername("ann")).thenReturn(stored);

        assertThatThrownBy(() -> userService.login(new Login("ann", "hunter3"))).isInstanceOf(ServletException.class);
    }

    @Test
    public void refusesAnUnknownUserOrAnIncompleteLogin() {

        assertThatThrownBy(() -> userService.login(new Login("nobody", "pw"))).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> userService.login(new Login(null, "pw"))).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> userService.login(new Login("ann", null))).isInstanceOf(ServletException.class);
    }

    // ---- Resetting a password ----

    @Test
    public void resetReplacesThePasswordAndTheSalt() {

        UserEntity stored = register("ann", "old-password");
        byte[] oldSalt = stored.getSalt();
        when(userRepository.findOneByUsername("ann")).thenReturn(stored);

        userService.resetPassword(new PasswordChange("ann", "old-password", "new-password"));

        assertThat(stored.getSalt()).isNotEqualTo(oldSalt);
        assertThatThrownBy(() -> userService.login(new Login("ann", "old-password"))).isInstanceOf(ServletException.class);
        assertThat(userService.login(new Login("ann", "new-password")).getToken()).isNotEmpty();
    }

    // Once a username and a new password were enough to take over any account
    @Test
    public void refusesAResetWithoutTheCurrentPassword() {

        UserEntity stored = register("ann", "old-password");
        byte[] password = stored.getPassword();
        when(userRepository.findOneByUsername("ann")).thenReturn(stored);

        assertThatThrownBy(() -> userService.resetPassword(new PasswordChange("ann", "a-guess", "taken-over")))
                .isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> userService.resetPassword(new PasswordChange("ann", null, "taken-over")))
                .isInstanceOf(ServletException.class);

        assertThat(stored.getPassword()).isEqualTo(password);
        assertThat(userService.login(new Login("ann", "old-password")).getToken()).isNotEmpty();
    }

    @Test
    public void answersAnUnknownUserTheSameAsAWrongPassword() {

        UserEntity stored = register("ann", "old-password");
        when(userRepository.findOneByUsername("ann")).thenReturn(stored);

        // Different answers would let anyone check which usernames exist
        assertThatThrownBy(() -> userService.resetPassword(new PasswordChange("nobody", "pw", "new")))
                .isInstanceOf(ServletException.class).hasMessage("Invalid username or password.");
        assertThatThrownBy(() -> userService.resetPassword(new PasswordChange("ann", "wrong", "new")))
                .isInstanceOf(ServletException.class).hasMessage("Invalid username or password.");
    }

    @Test
    public void refusesAResetWithNoNewPassword() {

        assertThatThrownBy(() -> userService.resetPassword(new PasswordChange("ann", "old-password", "")))
                .isInstanceOf(ServletException.class);

        verify(userRepository, never()).save(any());
    }

    // ---- Helpers ----

    /** A user as saveUser stores them, captured on its way to the repository. */
    private UserEntity register(String username, String password) {

        userService.saveUser(user(username, password, username + "@example.com"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());

        return saved.getValue();
    }

    private static User user(String username, String password, String email) {
        return User.builder().username(username).password(password).email(email).build();
    }

}
