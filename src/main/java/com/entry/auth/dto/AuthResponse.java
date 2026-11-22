package com.entry.auth.dto;

import com.entry.user.dto.UserDto;

/** The refresh token is NOT here: it is set as an httpOnly cookie. */
public record AuthResponse(String accessToken, long expiresIn, UserDto user) {
}
