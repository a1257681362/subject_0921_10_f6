package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 缺考裁定终审（双人复核第二人，必须与初审人不同）：
 * 终审落最终结论——成绩是否有效；无效必须写明原因并衔接下次安排。
 */
@Data
public class RulingFinalReviewRequest {

    @NotBlank(message = "终审人工号不能为空")
    private String reviewerCode;

    private String reviewerName;

    /**
     * 最终成绩有效性：VALID 有效（进合格名单） / INVALID 无效（写原因 + 衔接下次）。
     * 缺考（ABSENT）/弃考（WAIVED）只允许 INVALID。
     */
    @NotBlank(message = "成绩有效性结论不能为空（VALID / INVALID）")
    private String scoreValidity;

    /** 无效原因：NOT_CHECKED_IN / DISCIPLINE_VOID / WAIVED_EXAM / OTHER（INVALID 必填）。 */
    private String invalidReason;

    /** 无效原因说明（INVALID 必填，写明原因）。 */
    private String invalidReasonText;

    /** 衔接下次安排：NEXT_BATCH / MAKEUP / REAPPLY / NONE（INVALID 必填）。 */
    private String nextArrangement;

    /** 下次安排说明（如目标批次/补考时间等）。 */
    private String nextArrangementText;

    /** 终审意见。 */
    private String comment;
}
