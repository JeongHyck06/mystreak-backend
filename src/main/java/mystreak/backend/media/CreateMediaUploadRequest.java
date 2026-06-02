package mystreak.backend.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMediaUploadRequest(
        @NotBlank @Size(max = 160) String fileName,
        @NotBlank @Size(max = 80) String contentType,
        @NotNull UploadMediaType mediaType,
        Integer durationSeconds
) {
}
