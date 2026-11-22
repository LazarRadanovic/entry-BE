package com.entry.user;

import com.entry.security.AuthUser;
import com.entry.user.dto.ResidentDto;
import com.entry.user.dto.UpdateProfileRequest;
import com.entry.user.dto.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Available to PENDING users too (used by the "waiting for approval" screen). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return userService.getMe(me);
    }

    @PutMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(me, request);
    }

    @PutMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto updateAvatar(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return userService.updateAvatar(me, file);
    }

    @DeleteMapping("/me/avatar")
    public UserDto removeAvatar(@AuthenticationPrincipal AuthUser me) {
        return userService.removeAvatar(me);
    }

    @GetMapping
    public List<ResidentDto> residents(@AuthenticationPrincipal AuthUser me) {
        return userService.listResidents(me);
    }

    @GetMapping("/{id}")
    public ResidentDto resident(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return userService.getResident(me, id);
    }
}
