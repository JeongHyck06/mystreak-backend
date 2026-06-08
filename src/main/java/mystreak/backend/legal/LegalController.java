package mystreak.backend.legal;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> privacyPolicy() {
        return ResponseEntity.ok("""
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>My Streak 개인정보 처리방침</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.6; margin: 0; color: #1f2933; background: #f7faf7; }
                    main { max-width: 760px; margin: 0 auto; padding: 40px 20px; background: #fff; min-height: 100vh; }
                    h1 { font-size: 28px; margin-bottom: 8px; }
                    h2 { font-size: 20px; margin-top: 32px; }
                    p, li { font-size: 15px; }
                    .muted { color: #60706a; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>My Streak 개인정보 처리방침</h1>
                    <p class="muted">시행일: 2026년 6월 8일</p>

                    <h2>1. 수집하는 개인정보</h2>
                    <p>My Streak은 회원가입, 로그인, 서비스 제공을 위해 이메일, 이름, 프로필 정보, 소셜 로그인 식별자, 앱 이용 기록, 사용자가 업로드한 인증 콘텐츠를 수집할 수 있습니다.</p>

                    <h2>2. 개인정보 이용 목적</h2>
                    <p>수집한 정보는 계정 생성 및 로그인, 스트릭/팟 기능 제공, 인증 콘텐츠 관리, 서비스 안정성 개선, 고객 문의 대응을 위해 사용됩니다.</p>

                    <h2>3. 보관 및 파기</h2>
                    <p>개인정보는 서비스 이용 기간 동안 보관하며, 계정 삭제 또는 보관 목적 달성 시 관련 법령에 따라 지체 없이 파기합니다.</p>

                    <h2>4. 제3자 제공</h2>
                    <p>My Streak은 법령에 따른 경우를 제외하고 사용자의 개인정보를 사전 동의 없이 제3자에게 제공하지 않습니다.</p>

                    <h2>5. 외부 서비스</h2>
                    <p>소셜 로그인 및 이메일 인증을 위해 Apple, Google, Kakao 등 외부 인증 서비스를 사용할 수 있으며, 각 서비스의 개인정보 처리방침이 함께 적용될 수 있습니다.</p>

                    <h2>6. 이용자의 권리</h2>
                    <p>이용자는 본인의 개인정보 조회, 수정, 삭제, 처리 정지를 요청할 수 있습니다.</p>

                    <h2>7. 문의</h2>
                    <p>개인정보 관련 문의는 앱 내 문의 기능 또는 운영자 이메일을 통해 접수할 수 있습니다.</p>
                  </main>
                </body>
                </html>
                """);
    }
}
