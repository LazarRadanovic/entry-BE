package com.entry.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.entry.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void downscalesLargeJpegAndMakesThumbnail() throws Exception {
        ImageProcessor.Result r = processor.process(image(4000, 3000, "jpg"), "image/jpeg");

        assertThat(r.contentType()).isEqualTo("image/jpeg");
        assertThat(r.width()).isEqualTo(2048);
        assertThat(r.height()).isEqualTo(1536);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(r.thumb()));
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(480);
    }

    @Test
    void keepsSmallPngSizeAndFormat() throws Exception {
        ImageProcessor.Result r = processor.process(image(300, 200, "png"), "image/png");

        assertThat(r.contentType()).isEqualTo("image/png");
        assertThat(r.width()).isEqualTo(300);
        assertThat(r.height()).isEqualTo(200);
    }

    @Test
    void rejectsNonImagesAndWrongTypes() throws Exception {
        assertThatThrownBy(() -> processor.process("hello".getBytes(), "image/png"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_IMAGE"));
        assertThatThrownBy(() -> processor.process(image(10, 10, "png"), "application/pdf"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE"));
        assertThatThrownBy(() -> processor.process(new byte[0], "image/png"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("EMPTY_FILE"));
    }

    private static byte[] image(int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.getGraphics().fillRect(0, 0, w / 2, h / 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
