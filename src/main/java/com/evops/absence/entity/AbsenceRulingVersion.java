package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺考裁定版本（缺考标记版本）：每次建单 / 改型 / 初审 / 终审 / 驳回 / 重裁只追加一版，永不修改。
 * 主表 t_absence_ruling 只切 current_version、status 与最终结论字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_ruling_version")
public class AbsenceRulingVersion extends BaseEntity {
    private Long rulingId;
    private Integer versionNo;
    /** 本版缺考标记：NORMAL / ABSENT / WAIVED。 */
    private String absenceType;
    /** 本版流程状态：PENDING / REVIEWING / CONFIRMED / REJECTED。 */
    private String status;
    /** 本版成绩有效性：VALID / INVALID。 */
    private String scoreValidity;
    private String invalidReason;
    private String invalidReasonText;
    private String nextArrangement;
    private String nextArrangementText;
    // ---- 本版成绩快照 ----
    private Long scoreId;
    private BigDecimal paperScore;
    private String grade;
    private String ruleVersionNo;
    private Boolean qualified;
    /** AUTO / MANUAL / FIRST_REVIEW / SECOND_REVIEW / REJECT / REOPEN。 */
    private String changeType;
    private String changeReason;
    private String operator;
    private LocalDateTime changedAt;
}
