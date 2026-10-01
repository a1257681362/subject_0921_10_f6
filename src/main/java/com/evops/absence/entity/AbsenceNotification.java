package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺考裁定通知（结论通知本人；本版站内落库，短信等外发渠道下版再接）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_notification")
public class AbsenceNotification extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private Long rulingId;
    /** RULING_RESULT 最终裁定 / RULING_CHANGED 裁定变更。 */
    private String notifyType;
    private String title;
    private String content;
    /** NEW / READ。 */
    private String status;
    private String operator;
    private LocalDateTime notifiedAt;
    private LocalDateTime readAt;
}
