package com.englishprep.api.identity.application.register;

import com.englishprep.api.identity.application.password.PasswordHasher;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import com.englishprep.api.identity.domain.member.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new account. The steps: normalize email and user name, refuse if either is already
 * taken, hash the password, build the Member, save it. Everything runs in one transaction.
 */
@Service
public class RegisterMemberUseCase {

    static final String DEFAULT_TIME_ZONE = "UTC";

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;

    public RegisterMemberUseCase(MemberRepository members, PasswordHasher passwordHasher) {
        this.members = members;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public Member register(RegisterMemberCommand command) {
        String email = Person.normalizeLogin(command.email(), "email");
        String userName = Person.normalizeLogin(command.userName(), "userName");

        if (members.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        if (members.existsByUserName(userName)) {
            throw new UserNameAlreadyUsedException();
        }

        String timeZone = isBlank(command.timeZone()) ? DEFAULT_TIME_ZONE : command.timeZone();
        Member member = Member.register(command.firstName(), command.lastName(), userName, email,
                passwordHasher.hash(command.password()), timeZone);
        return members.save(member);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
