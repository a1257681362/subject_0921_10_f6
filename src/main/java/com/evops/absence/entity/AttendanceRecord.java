package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学员签到记录（一人一批次多条过程记录，但同时刻只有一条生效口径）：
 * - ON_SITE 现场签到直接 RECORDED 生效；
 * - SUPPLEMENT 补录先 PENDING_APPROVAL，审批通过 APPROVED 才计入到考口径，驳回 REJECTED 可再次申请。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_attendance_record")
public class AttendanceRecord extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private Long roomId;
    private String roomNo;
    private Integer seatNo;
    /** 实际签到钟点（补录为申报的实际到场时间）。 */
    private LocalDateTime checkInAt;
    /** ON_SITE 现场签到 / SUPPLEMENT 事后补录。 */
    private String source;
    /** RECORDED / PENDING_APPROVAL / APPROVED / REJECTED。 */
    private String status;
    private String applyReason;
    private String appliedBy;
    private LocalDateTime appliedAt;
    private String approverCode;
    private String approverName;
    private LocalDateTime approvedAt;
    private String approveComment;
    private String operator;
    private String remark;
}
