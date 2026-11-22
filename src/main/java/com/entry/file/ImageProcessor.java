package com.entry.file;

import com.entry.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Validates uploads and re-encodes them. Re-encoding strips EXIF metadata (e.g. GPS location of a
 * phone photo) and applies EXIF orientation. Animated GIFs become their first frame.
 */
@Component
public class ImageProcessor {

    public static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final int MAX_SIDE = 2048;
    private static final int THUMB_SIDE = 480;
    private static final long MAX_PIXELS = 50_000_000L; // guards against decompression bombs

    public record Result(byte[] main, byte[] thumb, String format, String contentType, int width, int height) {
    }

    private static final Map<String, String> CONTENT_TYPES = Map.of("jpg", "image/jpeg", "png", "image/png");

    public Result process(byte[] input, String declaredType) {
        if (input.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "The file is empty");
        }
        if (input.length > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Images can be at most 10 MB");
        }
        if (declaredType == null || !ALLOWED_TYPES.contains(declaredType)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG, GIF and WebP images are allowed");
        }
        checkDimensions(input);

        // Photos stay JPEG; anything that may carry transparency becomes PNG.
        String format = "image/jpeg".equals(declaredType) ? "jpg" : "png";
        try {
            BufferedImage source = Thumbnails.of(new ByteArrayInputStream(input)).scale(1.0).asBufferedImage();
            BufferedImage main = (source.getWidth() > MAX_SIDE || source.getHeight() > MAX_SIDE)
                    ? Thumbnails.of(source).size(MAX_SIDE, MAX_SIDE).asBufferedImage()
                    : source;
            byte[] mainBytes = write(Thumbnails.of(main).scale(1.0), format);
            byte[] thumbBytes = write(Thumbnails.of(main).size(THUMB_SIDE, THUMB_SIDE), format);
            return new Result(mainBytes, thumbBytes, format, CONTENT_TYPES.get(format), main.getWidth(),
                    main.getHeight());
        } catch (IOException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static byte[] write(Thumbnails.Builder<BufferedImage> builder, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.outputFormat(format).outputQuality(0.85).toOutputStream(out);
        return out.toByteArray();
    }

    /** Reads only the header to reject non-images and absurd dimensions before decoding. */
    private static void checkDimensions(byte[] input) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw ApiException.badRequest("IMAGE_TOO_LARGE", "Image dimensions are too large");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("INVALID_IMAGE", "The file is not a valid image");
    }
}
