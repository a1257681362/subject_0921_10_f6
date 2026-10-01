package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺考裁定主表（模块）：一人一批次一条。
 * 当前缺考标记、流程状态、最终结论（成绩有效性/无效原因/下次安排）、成绩快照均取当前版本；
 * 全部过程在 t_absence_ruling_version 只追加。缺考标记版本号见 currentVersion。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_ruling")
public class AbsenceRuling extends BaseEntity {
    /** 裁定号 QK + 批次编码 + 4 位流水。 */
    private String rulingNo;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 缺考标记（缺考标记状态）：NORMAL / ABSENT / WAIVED。 */
    private String absenceType;
    /** 裁定流程状态：PENDING / REVIEWING / CONFIRMED / REJECTED。 */
    private String status;
    private Integer currentVersion;
    /** 自动判定依据快照：有无签到、有无违纪及类型（JSON 文本，落库时生成）。 */
    private String evidenceSummary;
    /** VALID / INVALID（仅终审确认生效后写入）。 */
    private String scoreValidity;
    /** NOT_CHECKED_IN / DISCIPLINE_VOID / WAIVED_EXAM / OTHER。 */
    private String invalidReason;
    private String invalidReasonText;
    /** NEXT_BATCH / MAKEUP / REAPPLY / NONE。 */
    private String nextArrangement;
    private String nextArrangementText;
    // ---- 成绩快照（裁定时冻结，结论决定有没有效；统计按快照口径） ----
    private Long scoreId;
    private BigDecimal paperScore;
    private String grade;
    private String ruleVersionNo;
    /** 最终裁定 VALID 且等级合格才置真（统计只认最终裁定）。 */
    private Boolean qualified;
    // ---- 双人复核 ----
    private String initiatorCode;
    private String initiatorName;
    /** 初审人（复核第一人）。 */
    private String firstReviewerCode;
    private String firstReviewerName;
    private LocalDateTime firstReviewedAt;
    private String firstReviewComment;
    /** 终审人（复核第二人，必须与初审人不是同一人）。 */
    private String secondReviewerCode;
    private String secondReviewerName;
    private LocalDateTime secondReviewedAt;
    private String secondReviewComment;
    /** 结论通知本人的发送时间。 */
    private LocalDateTime notifiedAt;
    /** 依据变化或裁定变更后置真，统计重算后清除。 */
    private Boolean statsDirty;
    private LocalDateTime statsCalculatedAt;
    private String operator;
    private String remark;
}
