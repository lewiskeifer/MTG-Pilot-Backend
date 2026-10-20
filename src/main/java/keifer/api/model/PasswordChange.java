package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A request to change a password, which has to prove the current one.
 *
 * Its own shape rather than a User: resetting used to take a username and a new password and
 * nothing else, so anyone who knew a username could take over the account.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChange {

    String username;
    String currentPassword;
    String newPassword;

}
