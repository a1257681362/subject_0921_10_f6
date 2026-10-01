package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 签到补录审批：approver 为审批人；驳回必须填写意见。
 */
@Data
public class AttendanceApproveRequest {

    @NotBlank(message = "审批人工号不能为空")
    private String approverCode;

    /** 审批人姓名（可空，回显用）。 */
    private String approverName;

    @NotNull(message = "审批结论不能为空（approved=true 批准 / false 驳回）")
    private Boolean approved;

    /** 审批意见；驳回时必填。 */
    private String comment;
}
