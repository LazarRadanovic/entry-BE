package com.entry.auth;

import com.entry.user.dto.UserDto;

/** Internal result of a login/refresh: the raw refresh token goes into a cookie, the rest into the body. */
public record AuthResult(String accessToken, long expiresIn, String refreshToken, UserDto user) {
}
