package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 批量自动标缺考的目标：batchId 整批扫描，或 regNos 指定人员。
 * 不传任何目标视为按当前所有批次（不推荐，接口仍按批次粒度返回）。
 */
@Data
public class RulingGenerateRequest {

    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    /** 仅扫描指定报名号（可空 = 全批次）。 */
    private java.util.List<String> regNos;

    /** 对已存在未生效裁定（PENDING/REJECTED）是否重建新版本，缺省 false 幂等跳过。 */
    private Boolean rebuild;
}
