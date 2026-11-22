package com.entry.user;

import com.entry.file.FileUrlSigner;
import com.entry.user.dto.MemberDto;
import com.entry.user.dto.ResidentDto;
import com.entry.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds user DTOs, attaching a signed avatar thumbnail URL. */
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final FileUrlSigner signer;

    /** Requires {@code user.getEntrance()} to be loaded. */
    public UserDto toUserDto(User u) {
        return UserDto.from(u, avatarUrl(u));
    }

    public ResidentDto toResident(User u) {
        return ResidentDto.from(u, avatarUrl(u));
    }

    public MemberDto toMember(User u) {
        return MemberDto.from(u, avatarUrl(u));
    }

    public String avatarUrl(User u) {
        return signer.thumbUrl(u.getAvatarFileId());
    }
}
