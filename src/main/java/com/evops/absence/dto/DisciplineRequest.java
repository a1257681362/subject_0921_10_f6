package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 违纪记录登记（监考通报）：类型 + 情节 + 钟点，scoreVoid 标记本场成绩是否作废。
 */
@Data
public class DisciplineRequest {

    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    /** CHEAT / ENTRY_LATE / EARLY_LEAVE / ID_ISSUE / OTHER。 */
    @NotBlank(message = "违纪类型不能为空")
    private String disciplineType;

    @NotBlank(message = "违纪情节说明不能为空")
    private String detail;

    /** 发生时间，可空（缺省当前时间）。 */
    private String occurredAt;

    private Long roomId;

    /** 情节是否严重到本场成绩无效（缺省 false）。 */
    private Boolean scoreVoid;
}
