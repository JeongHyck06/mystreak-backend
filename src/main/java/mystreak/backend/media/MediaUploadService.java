package mystreak.backend.media;

import jakarta.annotation.PreDestroy;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class MediaUploadService {

    private static final int MAX_VIDEO_SECONDS = 15;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/quicktime");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "video/mp4", ".mp4",
            "video/quicktime", ".mov"
    );

    private final S3MediaProperties properties;
    private final S3Presigner presigner;
    private final String region;

    public MediaUploadService(S3MediaProperties properties) {
        this.properties = properties;
        this.region = isBlank(properties.getRegion()) ? "ap-northeast-2" : properties.getRegion();
        this.presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    public MediaUploadResponse createUpload(String profileId, CreateMediaUploadRequest request) {
        validateConfiguration();
        validateRequest(request);

        String objectKey = "check-ins/%s/%s%s".formatted(
                profileId,
                UUID.randomUUID(),
                extensionFor(request.contentType(), request.fileName())
        );
        int expiresInSeconds = Math.max(60, properties.getUploadUrlExpiresMinutes() * 60);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(request.contentType())
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        URL uploadUrl = presignedRequest.url();

        return new MediaUploadResponse(uploadUrl.toString(), mediaUrlFor(objectKey), objectKey, expiresInSeconds);
    }

    @PreDestroy
    public void close() {
        presigner.close();
    }

    private void validateConfiguration() {
        if (isBlank(properties.getBucket())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 버킷 설정이 필요합니다.");
        }
    }

    private void validateRequest(CreateMediaUploadRequest request) {
        Set<String> allowedTypes = request.mediaType() == UploadMediaType.IMAGE ? IMAGE_CONTENT_TYPES : VIDEO_CONTENT_TYPES;
        if (!allowedTypes.contains(request.contentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다.");
        }
        if (request.mediaType() == UploadMediaType.VIDEO) {
            if (request.durationSeconds() == null || request.durationSeconds() > MAX_VIDEO_SECONDS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동영상은 15초 이내만 업로드할 수 있습니다.");
            }
        }
    }

    private String extensionFor(String contentType, String fileName) {
        String fromContentType = EXTENSIONS.get(contentType);
        if (fromContentType != null) {
            return fromContentType;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex).toLowerCase();
        }
        return "";
    }

    private String mediaUrlFor(String objectKey) {
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(
                properties.getBucket(),
                region,
                objectKey
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
