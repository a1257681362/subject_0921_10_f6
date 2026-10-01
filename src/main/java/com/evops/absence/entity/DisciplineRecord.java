package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 考场违纪记录（只追加；撤销不删除、记 REVOKED 留痕）。
 * 有效（ACTIVE）且 score_void=true 的违纪是自动裁定"成绩作废"的依据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_record")
public class DisciplineRecord extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private Long roomId;
    private String roomNo;
    /** CHEAT / ENTRY_LATE / EARLY_LEAVE / ID_ISSUE / OTHER。 */
    private String disciplineType;
    private String detail;
    private LocalDateTime occurredAt;
    /** 情节是否严重到本场成绩无效（作废）。 */
    private Boolean scoreVoid;
    /** ACTIVE 有效（裁定依据） / REVOKED 已撤销。 */
    private String status;
    private String revokeReason;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private String recorder;
}
