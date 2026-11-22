package com.entry.user;

import com.entry.common.exception.ApiException;
import com.entry.file.FileService;
import com.entry.file.StoredFile;
import com.entry.security.AuthUser;
import com.entry.user.dto.ResidentDto;
import com.entry.user.dto.UpdateProfileRequest;
import com.entry.user.dto.UserDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final UserMapper mapper;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public UserDto getMe(AuthUser me) {
        return mapper.toUserDto(loadWithEntrance(me.id()));
    }

    @Transactional
    public UserDto updateMe(AuthUser me, UpdateProfileRequest req) {
        User user = loadWithEntrance(me.id());
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setFloor(req.floor().trim());
        user.setApartment(req.apartment().trim());
        return mapper.toUserDto(user);
    }

    @Transactional
    public UserDto updateAvatar(AuthUser me, MultipartFile file) {
        User user = loadWithEntrance(me.id());
        UUID previous = user.getAvatarFileId();
        StoredFile stored = fileService.storeImage(me, file);
        user.setAvatarFileId(stored.getId());
        users.flush(); // drop the FK reference before deleting the old file
        if (previous != null) {
            fileService.delete(previous);
        }
        return mapper.toUserDto(user);
    }

    @Transactional
    public UserDto removeAvatar(AuthUser me) {
        User user = loadWithEntrance(me.id());
        UUID previous = user.getAvatarFileId();
        if (previous != null) {
            user.setAvatarFileId(null);
            users.flush();
            fileService.delete(previous);
        }
        return mapper.toUserDto(user);
    }

    /** Active residents of the caller's entrance. */
    @Transactional(readOnly = true)
    public List<ResidentDto> listResidents(AuthUser me) {
        return users.findByEntrance_IdAndStatusOrderByFloorAscApartmentAscLastNameAsc(me.entranceId(),
                        UserStatus.ACTIVE)
                .stream().map(mapper::toResident).toList();
    }

    @Transactional(readOnly = true)
    public ResidentDto getResident(AuthUser me, UUID id) {
        return users.findByIdAndEntrance_Id(id, me.entranceId())
                .filter(User::isActive)
                .map(mapper::toResident)
                .orElseThrow(() -> ApiException.notFound("Resident not found"));
    }

    private User loadWithEntrance(UUID id) {
        return users.findWithEntranceById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
