package io.opencode.core.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 图片工具类，提供图片缩放、Base64 编码、格式检测等实用方法
 */
public final class ImageUtils {
    private static final int MAX_DIMENSION = 2048; // 图片最大边长限制（像素）

    private ImageUtils() {} // 工具类私有构造函数，防止实例化

    /**
     * 将图片缩放到不超过 MAX_DIMENSION 的最大边长
     * 如果图片尺寸已在限制内，直接返回原数据
     * 缩放失败时返回原始数据（容错处理）
     *
     * @param imageData 原始图片的字节数据
     * @param format    目标图片格式（如 "png"、"jpeg"）
     * @return 缩放后（或原始）的图片字节数据
     */
    public static byte[] resizeToMaxDimension(byte[] imageData, String format) {
        try {
            var original = ImageIO.read(new ByteArrayInputStream(imageData));
            if (original == null) return imageData;

            int width = original.getWidth();
            int height = original.getHeight();

            if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return imageData;

            // 按比例缩放，保持宽高比，以较长边为基准
            double scale;
            if (width > height) {
                scale = (double) MAX_DIMENSION / width;
            } else {
                scale = (double) MAX_DIMENSION / height;
            }

            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);

            var resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            var g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            var baos = new ByteArrayOutputStream();
            var formatName = format != null && !format.isBlank() ? format : "png";
            ImageIO.write(resized, formatName, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return imageData; // 缩放异常时返回原数据，确保不中断流程
        }
    }

    /** 将字节数据编码为 Base64 字符串 */
    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * 根据文件名后缀检测图片格式
     * 如果无法识别则默认返回 "png"
     */
    public static String detectFormat(String filename) {
        if (filename == null) return "png";
        var lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpeg";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".webp")) return "webp";
        return "png";
    }

    /** 判断给定的媒体类型是否为图片类型 */
    public static boolean isImage(String mediaType) {
        return mediaType != null && mediaType.startsWith("image/");
    }
}
