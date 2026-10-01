package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 签到补录申请：事后补录一律先落 PENDING_APPROVAL，审批通过才计入到考口径。
 */
@Data
public class AttendanceSupplementRequest {

    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    /** 申报的实际到场钟点（必填，不能晚于当前时间）。 */
    @NotBlank(message = "补录必须填写实际到场时间 checkInAt")
    private String checkInAt;

    /** 补录事由（必填，审批留痕）。 */
    @NotBlank(message = "补录必须填写补录事由 reason")
    private String reason;

    /** 申请人工号（可空，缺省取请求头 X-Operator）；审批人不得与申请人为同一人。 */
    private String applicantCode;

    private Long roomId;

    private String remark;
}
