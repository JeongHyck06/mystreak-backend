package mystreak.backend.media;

public record MediaUploadResponse(
        String uploadUrl,
        String mediaUrl,
        String objectKey,
        int expiresInSeconds
) {
}
