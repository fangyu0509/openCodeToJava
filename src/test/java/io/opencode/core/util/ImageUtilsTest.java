package io.opencode.core.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    @Test
    void detectFormatByExtension() {
        assertEquals("png", ImageUtils.detectFormat("image.png"));
        assertEquals("jpeg", ImageUtils.detectFormat("photo.jpg"));
        assertEquals("jpeg", ImageUtils.detectFormat("photo.jpeg"));
        assertEquals("gif", ImageUtils.detectFormat("animation.gif"));
        assertEquals("webp", ImageUtils.detectFormat("img.webp"));
        assertEquals("png", ImageUtils.detectFormat("unknown.xyz"));
    }

    @Test
    void isImageByMediaType() {
        assertTrue(ImageUtils.isImage("image/png"));
        assertTrue(ImageUtils.isImage("image/jpeg"));
        assertTrue(ImageUtils.isImage("image/gif"));
        assertFalse(ImageUtils.isImage("text/plain"));
        assertFalse(ImageUtils.isImage("application/pdf"));
    }

    @Test
    void toBase64() {
        var data = "hello".getBytes();
        var b64 = ImageUtils.toBase64(data);
        assertEquals(Base64.getEncoder().encodeToString(data), b64);
    }

    @Test
    void resizeSmallImageReturnsOriginal() {
        var data = new byte[]{0, 1, 2, 3};
        var result = ImageUtils.resizeToMaxDimension(data, "png");
        assertArrayEquals(data, result);
    }
}
