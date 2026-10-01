package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 违纪记录撤销（只追加留痕，不删除记录）：撤销后不再作为缺考/作废裁定依据。
 */
@Data
public class DisciplineRevokeRequest {

    @NotBlank(message = "撤销原因不能为空")
    private String reason;
}
