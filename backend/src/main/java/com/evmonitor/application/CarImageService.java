package com.evmonitor.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CarImageService {

    private static final int MAX_DIMENSION = 500;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    @Value("${app.car-images.directory}")
    private String uploadDirectory;

    public String uploadImage(UUID carId, MultipartFile file) throws IOException {
        validateFile(file);

        File dir = new File(uploadDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        BufferedImage original;
        try {
            original = ImageIO.read(file.getInputStream());
        } catch (javax.imageio.IIOException e) {
            throw new IllegalArgumentException("Ungültiges Bildformat. Bitte JPEG oder PNG hochladen.");
        }
        if (original == null) {
            throw new IllegalArgumentException("Ungültiges Bildformat. Bitte JPEG oder PNG hochladen.");
        }

        File targetFile = new File(dir, carId + ".jpg");
        try {
            BufferedImage resized = resize(original);
            writeJpeg(resized, targetFile);
        } catch (Exception e) {
            log.warn("Image processing failed for car {}: {}", carId, e.getMessage());
            throw new IllegalArgumentException("Bildformat nicht unterstützt. Bitte ein Standard-RGB-JPEG oder PNG hochladen.");
        }

        return targetFile.getAbsolutePath();
    }

    public void deleteImage(UUID carId) {
        File file = new File(uploadDirectory, carId + ".jpg");
        if (file.exists()) {
            file.delete();
        }
    }

    public Optional<Resource> getImageResource(UUID carId) {
        File file = new File(uploadDirectory, carId + ".jpg");
        if (!file.exists()) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(file));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Bitte eine Bilddatei auswählen.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Bild zu groß. Maximale Dateigröße: 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Ungültiges Dateiformat. Erlaubt: JPEG, PNG.");
        }
    }

    private BufferedImage resize(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            // No resize needed — still convert to RGB to strip alpha for JPEG
            return toRgb(original);
        }

        int newWidth, newHeight;
        if (width >= height) {
            newWidth = MAX_DIMENSION;
            newHeight = (int) Math.round((double) height / width * MAX_DIMENSION);
        } else {
            newHeight = MAX_DIMENSION;
            newWidth = (int) Math.round((double) width / height * MAX_DIMENSION);
        }

        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return result;
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgb.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return rgb;
    }

    private void writeJpeg(BufferedImage image, File targetFile) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(targetFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
