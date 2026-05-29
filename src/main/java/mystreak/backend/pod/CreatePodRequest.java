package mystreak.backend.pod;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePodRequest(
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 100) String description,
        @Min(2) @Max(30) int maxMembers,
        @NotBlank String tagLine,
        @NotEmpty List<@NotBlank String> tags
) {
}
