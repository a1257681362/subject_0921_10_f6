package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 缺考裁定初审（双人复核第一人）：
 * approved=true 初审通过送终审（REVIEWING）；approved=false 初审驳回（REJECTED，须写意见）。
 */
@Data
public class RulingFirstReviewRequest {

    @NotBlank(message = "初审人工号不能为空")
    private String reviewerCode;

    private String reviewerName;

    @NotNull(message = "初审结论不能为空（approved=true 通过送终审 / false 驳回）")
    private Boolean approved;

    /** 初审意见；驳回时必填。 */
    private String comment;
}
