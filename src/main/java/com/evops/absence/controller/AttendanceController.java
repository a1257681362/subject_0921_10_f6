package com.evops.absence.controller;

import com.evops.absence.dto.AttendanceApproveRequest;
import com.evops.absence.dto.AttendanceCheckInRequest;
import com.evops.absence.dto.AttendanceSupplementRequest;
import com.evops.absence.entity.AttendanceRecord;
import com.evops.absence.service.AttendanceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 学员签到：现场签到直接生效；补录一律走审批（PENDING_APPROVAL → APPROVED/REJECTED）。
 * 审批结论联动缺考裁定，依据变化自动重开/置脏。
 */
@RestController
@RequestMapping("/api/absence/attendances")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** 现场签到（直接生效，计入到考口径）。 */
    @PostMapping("/check-in")
    public ApiResponse<AttendanceRecord> checkIn(@Valid @RequestBody AttendanceCheckInRequest request) {
        return ApiResponse.ok(attendanceService.checkIn(request));
    }

    /** 签到补录申请（待审批，不计到考）。 */
    @PostMapping("/supplement")
    public ApiResponse<AttendanceRecord> supplement(@Valid @RequestBody AttendanceSupplementRequest request) {
        return ApiResponse.ok(attendanceService.applySupplement(request));
    }

    /** 补录审批：通过才计入到考；驳回必须写意见。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<AttendanceRecord> approve(@PathVariable Long id,
                                                 @Valid @RequestBody AttendanceApproveRequest request) {
        return ApiResponse.ok(attendanceService.approve(id, request));
    }

    @GetMapping
    public ApiResponse<List<AttendanceRecord>> list(@RequestParam Long batchId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(attendanceService.listByBatch(batchId, status));
    }

    /** 待审批补录清单。 */
    @GetMapping("/pending")
    public ApiResponse<List<AttendanceRecord>> pending() {
        return ApiResponse.ok(attendanceService.listPending());
    }

    @GetMapping("/{id}")
    public ApiResponse<AttendanceRecord> get(@PathVariable Long id) {
        return ApiResponse.ok(attendanceService.get(id));
    }
}
