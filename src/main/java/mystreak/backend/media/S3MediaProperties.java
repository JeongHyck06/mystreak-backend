package mystreak.backend.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media.s3")
public class S3MediaProperties {

    private String bucket;
    private String region = "ap-northeast-2";
    private int uploadUrlExpiresMinutes = 10;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getUploadUrlExpiresMinutes() {
        return uploadUrlExpiresMinutes;
    }

    public void setUploadUrlExpiresMinutes(int uploadUrlExpiresMinutes) {
        this.uploadUrlExpiresMinutes = uploadUrlExpiresMinutes;
    }
}
