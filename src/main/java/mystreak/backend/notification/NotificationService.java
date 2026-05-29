package mystreak.backend.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final Map<String, NotificationResponse> notifications = new LinkedHashMap<>();

    public NotificationService() {
        put(new NotificationResponse("n1", "오늘 인증, 자정까지 6시간 남았어요", "매일매일 영어 30분 팟의 인증을 놓치지 마세요.", "지금 안내 · 5분 전", "deadline", true, false));
        put(new NotificationResponse("n2", "지수님이 내 인증을 체크했어요", "새벽 5시 러닝 크루 · 이번 주 6번째 인증 완료", "1시간 전", "check", false, false));
        put(new NotificationResponse("n3", "수현님이 댓글을 남겼어요", "아침마다 부지런하세요. 저도 다음 주부터 함께해볼게요!", "3시간 전", "comment", false, false));
        put(new NotificationResponse("n4", "축하해요! 연속 3주 완주 트로피를 획득했어요", "프로필에 자랑스럽게 표시되었어요", "2일 전", "trophy", false, false));
        put(new NotificationResponse("n5", "팟 멤버 4명이 반응을 남겼어요", "니러, 최후, 하린 외 1명 · 이번 주 인증", "3일 전", "check", false, false));
    }

    public List<NotificationResponse> getNotifications(String type) {
        if (type == null || type.isBlank() || type.equals("all")) {
            return new ArrayList<>(notifications.values());
        }

        return notifications.values()
                .stream()
                .filter(notification -> notification.type().equals(type))
                .toList();
    }

    public List<NotificationResponse> markAllRead() {
        notifications.replaceAll((id, notification) -> new NotificationResponse(
                notification.id(),
                notification.title(),
                notification.body(),
                notification.meta(),
                notification.type(),
                notification.urgent(),
                true
        ));
        return getNotifications(null);
    }

    private void put(NotificationResponse notification) {
        notifications.put(notification.id(), notification);
    }
}
