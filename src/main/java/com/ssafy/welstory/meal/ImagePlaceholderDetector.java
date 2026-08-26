package com.ssafy.welstory.meal;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Component
public class ImagePlaceholderDetector {
    public Analysis analyze(byte[] bytes) {
        String hash = sha256(bytes);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new Analysis(hash, false);
            }
            int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 180);
            int samples = 0;
            int nearWhite = 0;
            Set<Integer> quantizedColors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y += step) {
                for (int x = 0; x < image.getWidth(); x += step) {
                    int rgb = image.getRGB(x, y);
                    int red = (rgb >> 16) & 0xff;
                    int green = (rgb >> 8) & 0xff;
                    int blue = rgb & 0xff;
                    samples++;
                    if (red >= 238 && green >= 238 && blue >= 238) nearWhite++;
                    quantizedColors.add(((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4));
                }
            }
            double whiteRatio = samples == 0 ? 0 : (double) nearWhite / samples;
            boolean placeholder = whiteRatio >= 0.82 && quantizedColors.size() <= 80;
            return new Analysis(hash, placeholder);
        } catch (Exception ignored) {
            return new Analysis(hash, false);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Analysis(String hash, boolean placeholder) {}
}
