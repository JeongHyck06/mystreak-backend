package mystreak.backend.pod;

public class PodNotFoundException extends RuntimeException {

    public PodNotFoundException(String podId) {
        super("Pod not found: " + podId);
    }
}
