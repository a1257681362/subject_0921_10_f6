package com.evops.absence.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合格名单项：只取最终裁定 VALID 且等级合格者；成绩口径取裁定时冻结的快照。
 */
@Data
public class PassListItem {
    private String rulingNo;
    private String regNo;
    private String name;
    private BigDecimal paperScore;
    private String grade;
    private String ruleVersionNo;
    /** 最终裁定生效钟点（终审确认时间）。 */
    private String confirmedAt;
}
