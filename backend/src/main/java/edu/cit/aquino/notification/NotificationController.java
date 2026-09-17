package edu.cit.aquino.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:5173")
class NotificationController {
    private final NotificationRepository notificationRepository;

    NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    ResponseEntity<List<NotificationRecord>> getNotifications() {
        return ResponseEntity.ok(notificationRepository.findAll());
    }
}
