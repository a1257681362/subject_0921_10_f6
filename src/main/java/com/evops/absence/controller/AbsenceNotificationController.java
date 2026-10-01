package com.evops.absence.controller;

import com.evops.absence.entity.AbsenceNotification;
import com.evops.absence.service.AbsenceNotificationService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 缺考裁定通知（结论通知本人）：终审生效 / 裁定变更都发；本版站内落库。
 */
@RestController
@RequestMapping("/api/absence/notifications")
public class AbsenceNotificationController {

    private final AbsenceNotificationService notificationService;

    public AbsenceNotificationController(AbsenceNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<AbsenceNotification>> list(@RequestParam(required = false) String regNo,
                                                        @RequestParam(required = false, defaultValue = "false")
                                                        boolean unreadOnly) {
        return ApiResponse.ok(notificationService.list(regNo, unreadOnly));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<AbsenceNotification> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }
}
