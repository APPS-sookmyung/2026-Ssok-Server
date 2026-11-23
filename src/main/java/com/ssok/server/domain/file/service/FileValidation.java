package com.ssok.server.domain.file.service;

import com.ssok.server.common.exception.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidation {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    public record Validated(String name, String contentType, byte[] bytes) {}

    public Validated validate(MultipartFile file) {
        if (file.isEmpty()) throw new InvalidRequestException("file must not be empty");
        if (file.getSize() > MAX_BYTES) throw new FileTooLargeException();
        String original = file.getOriginalFilename();
        if (original == null || original.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("invalid file name");
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.length() > 255) throw new InvalidRequestException("invalid file name");
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        catch (IOException e) { throw new FileStorageException(); }
        if (bytes.length > MAX_BYTES) throw new FileTooLargeException();
        String lower = name.toLowerCase(Locale.ROOT);
        String type;
        if (lower.endsWith(".pdf") && startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) type = "application/pdf";
        else if (lower.endsWith(".png") && startsWith(bytes, PNG)) type = "image/png";
        else if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && startsWith(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255})) type = "image/jpeg";
        else throw new UnsupportedFileException();
        return new Validated(name, type, bytes);
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(prefix, Arrays.copyOf(bytes, prefix.length));
    }
}
