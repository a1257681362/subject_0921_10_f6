package com.evops.absence.vo;

import lombok.Data;

/**
 * 批量自动标缺考预检项：逐条展示签到/违纪依据、自动标记与可否建单。
 */
@Data
public class RulingGenerateItem {
    private String regNo;
    private String name;
    /** 自动缺考标记：NORMAL / ABSENT（违纪到场不作缺考，但单列 scoreVoid 提示）。 */
    private String autoAbsenceType;
    /** 是否有有效签到（现场已登记或补录已批准）。 */
    private boolean checkedIn;
    private String checkInAt;
    /** 有效违纪类型列表。 */
    private java.util.List<String> disciplineTypes;
    /** 是否存在使成绩作废的有效违纪。 */
    private boolean scoreVoid;
    /** 是否已录入成绩。 */
    private boolean hasScore;
    private String grade;
    /** 是否已存在裁定及其状态。 */
    private String existingStatus;
    private boolean actionable;
    private String note;
}
