package mystreak.backend.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import mystreak.backend.auth.AuthService;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;
    private final AuthService authService;

    public MediaUploadController(MediaUploadService mediaUploadService, AuthService authService) {
        this.mediaUploadService = mediaUploadService;
        this.authService = authService;
    }

    @Operation(summary = "인증 미디어 S3 업로드 URL을 발급합니다")
    @PostMapping("/api/media/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse createUpload(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateMediaUploadRequest request
    ) {
        return mediaUploadService.createUpload(authService.requireUserId(authorization), request);
    }

    @Operation(summary = "업로드된 미디어를 조회합니다")
    @GetMapping("/api/media/files")
    public ResponseEntity<Void> readMedia(@RequestParam String objectKey) {
        URI location = URI.create(mediaUploadService.createReadUrl(objectKey).toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .build();
    }
}
