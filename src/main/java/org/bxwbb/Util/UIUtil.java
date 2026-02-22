package org.bxwbb.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;

public class UIUtil {

    /**
     * 核心方法：修改JLabel图片的颜色
     * @param targetColor 目标颜色（滤镜颜色）
     */
    public static BufferedImage changeLabelImageColor(Color targetColor, BufferedImage originalImage) {
        BufferedImage coloredImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        int[] pixels = new int[originalImage.getWidth() * originalImage.getHeight()];
        originalImage.getRGB(0, 0, originalImage.getWidth(), originalImage.getHeight(), pixels, 0, originalImage.getWidth());

        int targetRed = targetColor.getRed();
        int targetGreen = targetColor.getGreen();
        int targetBlue = targetColor.getBlue();

        for (int i = 0; i < pixels.length; i++) {
            Color pixelColor = new Color(pixels[i], true);
            int alpha = pixelColor.getAlpha();

            int gray = (pixelColor.getRed() + pixelColor.getGreen() + pixelColor.getBlue()) / 3;
            int newRed = (gray * targetRed) / 255;
            int newGreen = (gray * targetGreen) / 255;
            int newBlue = (gray * targetBlue) / 255;

            pixels[i] = new Color(newRed, newGreen, newBlue, alpha).getRGB();
        }

        coloredImage.setRGB(0, 0, originalImage.getWidth(), originalImage.getHeight(), pixels, 0, originalImage.getWidth());

        return coloredImage;
    }

    public static Image imageResetSize(Image image) {
        return imageResetSize(image, 20, 20);
    }

    public static Image imageResetSize(Image image, int width, int height) {
        return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }

    /**
     * 核心方法：将第二张图片自适应缩放后叠加到第一张图片的指定坐标位置
     * @param baseImage 底图（被叠加的图片）
     * @param overlayImage 叠加图（需要缩放并叠加的图片）
     * @param targetX 叠加图左上角的目标X坐标（相对于底图）
     * @param targetY 叠加图左上角的目标Y坐标（相对于底图）
     * @return 合成后的新图片（BufferedImage）
     */
    public static BufferedImage overlayImageWithAutoScale(BufferedImage baseImage, BufferedImage overlayImage, int targetX, int targetY) {
        if (baseImage == null || overlayImage == null) {
            throw new IllegalArgumentException("底图或叠加图不能为空");
        }

        int minX = Math.min(0, targetX);
        int minY = Math.min(0, targetY);

        int maxX = Math.max(baseImage.getWidth(), targetX + overlayImage.getWidth());
        int maxY = Math.max(baseImage.getHeight(), targetY + overlayImage.getHeight());

        int finalWidth = maxX - minX;
        int finalHeight = maxY - minY;

        int baseDrawX = -minX;
        int baseDrawY = -minY;
        int overlayDrawX = targetX - minX;
        int overlayDrawY = targetY - minY;

        BufferedImage combinedImage = new BufferedImage(
                finalWidth,
                finalHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = combinedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, finalWidth, finalHeight);
        g2d.setComposite(AlphaComposite.SrcOver);

        g2d.drawImage(
                baseImage,
                baseDrawX,
                baseDrawY,
                baseImage.getWidth(),
                baseImage.getHeight(),
                null
        );

        g2d.drawImage(
                overlayImage,
                overlayDrawX,
                overlayDrawY,
                overlayImage.getWidth(),
                overlayImage.getHeight(),
                null
        );

        g2d.dispose();

        return combinedImage;
    }

    public static BufferedImage convertToBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        if (image == null) {
            return createDefaultEmptyImage(20, 20);
        }

        ImageObserver observer = new JLabel();
        int width = image.getWidth(observer);
        int height = image.getHeight(observer);

        if (width <= 0 || height <= 0) {
            width = 20;
            height = 20;
        }

        BufferedImage bufferedImage = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, observer);
        g2d.dispose();

        return bufferedImage;
    }

    public static BufferedImage createDefaultEmptyImage(int width, int height) {
        BufferedImage emptyImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = emptyImage.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        return emptyImage;
    }

}
