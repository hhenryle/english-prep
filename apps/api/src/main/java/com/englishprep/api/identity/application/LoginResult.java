package com.englishprep.api.identity.application;

import com.englishprep.api.identity.domain.Member;

/** What a successful login gives back: the member and the two tokens issued for this session. */
public record LoginResult(Member member, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
