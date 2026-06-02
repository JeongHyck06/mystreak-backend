package mystreak.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class GoogleMailService {

    private final JavaMailSender mailSender;
    private final String username;
    private final String fromAddress;

    public GoogleMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${app.mail.from:${spring.mail.username:}}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.username = username;
        this.fromAddress = fromAddress;
    }

    public void sendVerificationCode(String to, String code) {
        if (username == null || username.isBlank()) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Google mail username is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("[MyStreak] 이메일 인증 코드");
        message.setText("""
                MyStreak 이메일 인증 코드입니다.

                인증 코드: %s

                이 코드는 10분 동안만 사용할 수 있어요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new AuthException(HttpStatus.BAD_GATEWAY, "이메일 전송에 실패했어요");
        }
    }
}
