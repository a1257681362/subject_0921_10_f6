package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 缺考标记改型：缺考（ABSENT）与弃考（WAIVED）分清类型。
 * 自动判定只会标 ABSENT；改 WAIVED（本人主动放弃）必须凭放弃说明/证据人工改型，
 * 已生效裁定改型一律重开走新版本（裁定有变更，统计随之重算）。
 */
@Data
public class RulingReclassifyRequest {

    /** 目标标记：NORMAL / ABSENT / WAIVED。 */
    @NotBlank(message = "目标缺考标记不能为空（NORMAL / ABSENT / WAIVED）")
    private String absenceType;

    /** 改型原因/放弃证据说明（必填，过程留痕）。 */
    @NotBlank(message = "改型必须填写原因或放弃证据 reason")
    private String reason;
}
