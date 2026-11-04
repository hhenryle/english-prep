package com.englishprep.api.identity.application;

import com.englishprep.api.identity.domain.Person;
import com.englishprep.api.identity.domain.User;
import com.englishprep.api.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new account. The steps: normalize email and user name, refuse if either is already
 * taken, hash the password, build the User, save it. Everything runs in one transaction.
 */
@Service
public class RegisterUserUseCase {

    static final String DEFAULT_TIME_ZONE = "UTC";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public RegisterUserUseCase(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public User register(RegisterUserCommand command) {
        String email = Person.normalizeLogin(command.email(), "email");
        String userName = Person.normalizeLogin(command.userName(), "userName");

        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        if (users.existsByUserName(userName)) {
            throw new UserNameAlreadyUsedException();
        }

        String timeZone = isBlank(command.timeZone()) ? DEFAULT_TIME_ZONE : command.timeZone();
        User user = User.register(command.firstName(), command.lastName(), userName, email,
                passwordHasher.hash(command.password()), timeZone);
        return users.save(user);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
