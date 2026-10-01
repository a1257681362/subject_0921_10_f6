package com.evops.absence.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 批次缺考/成绩有效性统计：只认最终裁定（CONFIRMED）；过程态单列计数，不进结论口径。
 */
@Data
public class RulingStats {
    private Long batchId;
    private String batchCode;
    /** 名册总人数。 */
    private int totalRegistrations;
    /** 已建裁定人数 / 未建裁定人数。 */
    private int rulingCount;
    private int unruledCount;

    // 过程态（未完成双人复核，不计结论）
    private int pendingCount;
    private int reviewingCount;
    private int rejectedCount;

    // 最终裁定（CONFIRMED）
    private int confirmedCount;
    private int normalCount;
    private int absentCount;
    private int waivedCount;
    private int validCount;
    private int invalidCount;
    /** 进合格名单人数（最终 VALID 且等级合格）。 */
    private int qualifiedCount;
    /** 合格率 = qualifiedCount / confirmedCount（百分比，保留两位）。 */
    private double qualifiedRate;
    /** 最终缺考率 =（ABSENT+WAIVED）/ confirmedCount。 */
    private double absentRate;

    /** 无效原因分布（键为原因码）。 */
    private Map<String, Integer> invalidReasonCounts = new LinkedHashMap<>();
    /** 下次安排分布（键为安排码）。 */
    private Map<String, Integer> nextArrangementCounts = new LinkedHashMap<>();

    /** 依据/裁定已变更、尚待重算的条数（正常为 0；变更即触发重算）。 */
    private int dirtyCount;
    private String calculatedAt;
}
