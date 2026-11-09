package com.englishprep.api.identity.application;

import com.englishprep.api.identity.domain.Member;
import com.englishprep.api.identity.domain.MemberRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a login (user name or email) and a password and returns the member. It does not create
 * tokens: other login methods (for example Google) will be separate use cases that also return a
 * Member, and a shared step will turn that Member into tokens.
 */
@Service
public class LoginWithPasswordUseCase {

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;
    /** Compared against when the account does not exist, so a miss costs the same time as a wrong password. */
    private final String dummyHash;

    public LoginWithPasswordUseCase(MemberRepository members, PasswordHasher passwordHasher) {
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.dummyHash = passwordHasher.hash("not-a-real-password");
    }

    @Transactional(readOnly = true)
    public Member login(LoginCommand command) {
        String password = command.password() == null ? "" : command.password();
        Optional<Member> member = find(command.login());

        // Always run one hash comparison, whether or not the account exists.
        boolean matches = passwordHasher.matches(password, member.map(Member::passwordHash).orElse(dummyHash));
        if (member.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return member.get();
    }

    /** A login with "@" is an email, anything else is a user name (user names cannot contain "@"). */
    private Optional<Member> find(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String normalized = login.strip().toLowerCase(Locale.ROOT);
        return normalized.contains("@") ? members.findByEmail(normalized) : members.findByUserName(normalized);
    }
}
