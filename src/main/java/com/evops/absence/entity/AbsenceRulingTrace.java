package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺考裁定过程留痕（只追加）：证据变化、补录审批结论、违纪变化、改型、通知、重算等过程事件。
 * 与版本链互为印证：版本记状态与结论，trace 记触发原因与动作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_ruling_trace")
public class AbsenceRulingTrace extends BaseEntity {
    private Long rulingId;
    private Long batchId;
    /**
     * EVIDENCE_CHANGED / SUPPLEMENT_APPROVED / SUPPLEMENT_REJECTED /
     * DISCIPLINE_VOID / DISCIPLINE_REVOKED / RECLASSIFY / NOTIFIED / RECALC / REOPEN。
     */
    private String traceType;
    private String detail;
    private String operator;
    private LocalDateTime tracedAt;
}
