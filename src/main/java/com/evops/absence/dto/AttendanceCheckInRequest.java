package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 现场签到：按批次 + 报名号定位名册；checkInAt 可空（缺省当前时间）。
 */
@Data
public class AttendanceCheckInRequest {

    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    /** 实际签到钟点，可空（缺省当前时间）；格式 yyyy-MM-dd HH:mm(:ss)。 */
    private String checkInAt;

    private Long roomId;

    private String remark;
}
