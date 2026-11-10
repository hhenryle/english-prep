package com.englishprep.api.identity.application.login;

import com.englishprep.api.identity.application.token.AccessToken;
import com.englishprep.api.identity.application.token.IssuedRefreshToken;
import com.englishprep.api.identity.domain.member.Member;

/** What a successful login gives back: the member and the two tokens issued for this session. */
public record LoginResult(Member member, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
