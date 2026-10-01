package com.evops.absence.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量自动标缺考预检 / 执行结果。
 */
@Data
public class RulingGenerateResult {
    private Long batchId;
    private String batchCode;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<RulingGenerateItem> items;
    /** 执行结果：新建/重刷的裁定号。 */
    private List<String> rulingNos;
}
