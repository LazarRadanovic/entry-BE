package com.entry.file;

import org.springframework.core.io.Resource;

/** Blob storage abstraction (local disk today; MinIO/S3 can be added as another implementation). */
public interface StorageService {

    void put(String key, byte[] data);

    /** @throws com.entry.common.exception.ApiException 404 if the blob is missing */
    Resource get(String key);

    void delete(String key);
}
