package com.entry.file;

import com.entry.common.exception.ApiException;
import com.entry.file.dto.FileDto;
import com.entry.security.AuthUser;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileUrlSigner signer;

    /** Upload an image first, then reference its id when creating a post or sending a chat message. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto upload(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return fileService.toDto(fileService.storeImage(me, file));
    }

    /** Public endpoint, authorised by the URL signature (see {@link FileUrlSigner}). */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable UUID id,
            @RequestParam(defaultValue = "full") FileUrlSigner.Variant variant,
            @RequestParam long exp,
            @RequestParam String sig) {
        if (!signer.verify(id, variant, exp, sig)) {
            throw ApiException.notFound("File not found");
        }
        FileService.Download d = fileService.load(id, variant);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.resource());
    }
}
