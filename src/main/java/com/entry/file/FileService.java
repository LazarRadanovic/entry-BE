package com.entry.file;

import com.entry.common.exception.ApiException;
import com.entry.file.dto.FileDto;
import com.entry.security.AuthUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {

    private final StoredFileRepository files;
    private final StorageService storage;
    private final ImageProcessor imageProcessor;
    private final FileUrlSigner signer;

    @Transactional
    public StoredFile storeImage(AuthUser me, MultipartFile upload) {
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageProcessor.Result img = imageProcessor.process(bytes, upload.getContentType());

        UUID blobId = UUID.randomUUID();
        String mainKey = me.entranceId() + "/" + blobId + "." + img.format();
        String thumbKey = me.entranceId() + "/" + blobId + "_thumb." + img.format();
        storage.put(mainKey, img.main());
        storage.put(thumbKey, img.thumb());

        StoredFile f = new StoredFile();
        f.setEntranceId(me.entranceId());
        f.setUploaderId(me.id());
        f.setContentType(img.contentType());
        f.setSizeBytes(img.main().length);
        f.setWidth(img.width());
        f.setHeight(img.height());
        f.setStorageKey(mainKey);
        f.setThumbKey(thumbKey);
        return files.save(f);
    }

    /**
     * Loads files the caller uploaded, for attaching them to a post or message.
     * Preserves the requested order and rejects anything not uploaded by the caller.
     */
    @Transactional(readOnly = true)
    public List<StoredFile> requireOwnFiles(AuthUser me, Collection<UUID> ids) {
        List<UUID> unique = List.copyOf(new LinkedHashSet<>(ids));
        Map<UUID, StoredFile> byId = files.findAllById(unique).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return unique.stream().map(id -> {
            StoredFile f = byId.get(id);
            if (f == null || !me.id().equals(f.getUploaderId()) || !me.entranceId().equals(f.getEntranceId())) {
                throw ApiException.badRequest("INVALID_FILE", "Unknown image: " + id);
            }
            return f;
        }).toList();
    }

    @Transactional
    public void delete(UUID fileId) {
        files.findById(fileId).ifPresent(f -> {
            files.delete(f);
            storage.delete(f.getStorageKey());
            storage.delete(f.getThumbKey());
        });
    }

    /** Serves a blob after the caller has verified the URL signature. */
    @Transactional(readOnly = true)
    public Download load(UUID fileId, FileUrlSigner.Variant variant) {
        StoredFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("File not found"));
        String key = variant == FileUrlSigner.Variant.thumb ? f.getThumbKey() : f.getStorageKey();
        return new Download(storage.get(key), f.getContentType());
    }

    public FileDto toDto(StoredFile f) {
        return new FileDto(f.getId(), signer.fullUrl(f.getId()), signer.thumbUrl(f.getId()), f.getWidth(),
                f.getHeight());
    }

    public record Download(Resource resource, String contentType) {
    }
}
