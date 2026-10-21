package com.marketplace.backend.service;

import com.marketplace.backend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Saves product/review media to app.upload.dir and returns its public URL
 * (/uploads/<random>.<ext>). Only a fixed set of image/video types is
 * accepted, checked by extension AND by the file's leading bytes, and the
 * stored name is random, so an upload can't become an HTML/SVG page that runs
 * script on our origin or overwrite another file.
 */
@Service
public class UploadService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 30L * 1024 * 1024;
    private static final Map<String, String> IMAGE_TYPES = Map.of("jpg", "jpg", "jpeg", "jpg", "png", "png", "webp", "webp", "gif", "gif");
    private static final Map<String, String> VIDEO_TYPES = Map.of("mp4", "mp4", "webm", "webm");

    private final Path root;

    public UploadService(@Value("${app.upload.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path root() {
        return root;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Vui lòng chọn tệp", HttpStatus.BAD_REQUEST);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";

        String storedExt;
        if (IMAGE_TYPES.containsKey(ext)) {
            if (file.getSize() > MAX_IMAGE_BYTES) throw new ApiException("Ảnh tối đa 5MB", HttpStatus.BAD_REQUEST);
            storedExt = IMAGE_TYPES.get(ext);
        } else if (VIDEO_TYPES.containsKey(ext)) {
            if (file.getSize() > MAX_VIDEO_BYTES) throw new ApiException("Video tối đa 30MB", HttpStatus.BAD_REQUEST);
            storedExt = VIDEO_TYPES.get(ext);
        } else {
            throw new ApiException("Chỉ hỗ trợ ảnh JPG, PNG, WEBP, GIF hoặc video MP4, WEBM", HttpStatus.BAD_REQUEST);
        }

        try {
            byte[] head = new byte[12];
            int n;
            try (InputStream in = file.getInputStream()) {
                n = in.readNBytes(head, 0, head.length);
            }
            if (!matchesSignature(storedExt, head, n)) {
                throw new ApiException("Nội dung tệp không đúng định dạng " + storedExt.toUpperCase(Locale.ROOT), HttpStatus.BAD_REQUEST);
            }
            String name = UUID.randomUUID() + "." + storedExt;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + name;
        } catch (IOException e) {
            throw new ApiException("Không lưu được tệp, vui lòng thử lại", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // "magic bytes" at the start of each allowed format
    private static boolean matchesSignature(String ext, byte[] b, int n) {
        return switch (ext) {
            case "jpg" -> n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> n >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "gif" -> n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "webp" -> n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "mp4" -> n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            case "webm" -> n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                    && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
            default -> false;
        };
    }
}
