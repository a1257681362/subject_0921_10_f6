package com.evops.absence.common;

/**
 * 缺考裁定工作区业务常量：
 * 缺考标记（缺考标记状态）、裁定流程状态、成绩有效性、无效原因、下次安排、
 * 签到来源/状态、违纪类型/状态、版本变更类型、留痕类型、通知类型。
 */
public final class AbsenceConstants {

    private AbsenceConstants() {
    }

    // ============ 缺考标记（缺考标记状态）：缺考与弃考分清类型 ============
    /** 正常到考：有有效签到，且无导致成绩作废的违纪。 */
    public static final String MARK_NORMAL = "NORMAL";
    /** 缺考：应考未到（无有效签到），本人无放弃表示。 */
    public static final String MARK_ABSENT = "ABSENT";
    /** 弃考：本人主动放弃考试（须凭放弃申请人工改型，不能与缺考混为一谈）。 */
    public static final String MARK_WAIVED = "WAIVED";

    // ============ 裁定流程状态（当前版本状态） ============
    /** 待初审：自动建单或重裁后的初始状态。 */
    public static final String RULING_PENDING = "PENDING";
    /** 初审通过、待终审（双人复核第二人）。 */
    public static final String RULING_REVIEWING = "REVIEWING";
    /** 双人复核完成：最终裁定生效，统计只认此状态。 */
    public static final String RULING_CONFIRMED = "CONFIRMED";
    /** 初审驳回：终止，须重开后再走复核。 */
    public static final String RULING_REJECTED = "REJECTED";

    // ============ 成绩有效性结论 ============
    public static final String VALIDITY_VALID = "VALID";
    public static final String VALIDITY_INVALID = "INVALID";

    // ============ 无效原因 ============
    /** 未签到：按签到记录应考未到。 */
    public static final String REASON_NOT_CHECKED_IN = "NOT_CHECKED_IN";
    /** 违纪情节严重，本场成绩作废。 */
    public static final String REASON_DISCIPLINE_VOID = "DISCIPLINE_VOID";
    /** 本人弃考。 */
    public static final String REASON_WAIVED_EXAM = "WAIVED_EXAM";
    /** 其他（须写明原因）。 */
    public static final String REASON_OTHER = "OTHER";

    // ============ 衔接下次安排 ============
    /** 顺延下次批次。 */
    public static final String NEXT_BATCH = "NEXT_BATCH";
    /** 安排补考。 */
    public static final String NEXT_MAKEUP = "MAKEUP";
    /** 重新报名。 */
    public static final String NEXT_REAPPLY = "REAPPLY";
    /** 不安排（如违纪作废）。 */
    public static final String NEXT_NONE = "NONE";

    // ============ 签到来源 ============
    public static final String SOURCE_ON_SITE = "ON_SITE";
    public static final String SOURCE_SUPPLEMENT = "SUPPLEMENT";

    // ============ 签到状态 ============
    /** 现场签到已登记，直接计入到考口径。 */
    public static final String ATT_RECORDED = "RECORDED";
    /** 补录待审批：审批通过前不计入到考口径。 */
    public static final String ATT_PENDING_APPROVAL = "PENDING_APPROVAL";
    /** 补录已批准：计入到考口径。 */
    public static final String ATT_APPROVED = "APPROVED";
    /** 补录已驳回：不计入到考口径，可再次申请。 */
    public static final String ATT_REJECTED = "REJECTED";

    // ============ 违纪类型 ============
    public static final String DISC_CHEAT = "CHEAT";
    public static final String DISC_ENTRY_LATE = "ENTRY_LATE";
    public static final String DISC_EARLY_LEAVE = "EARLY_LEAVE";
    public static final String DISC_ID_ISSUE = "ID_ISSUE";
    public static final String DISC_OTHER = "OTHER";

    // ============ 违纪记录状态 ============
    public static final String DISC_ACTIVE = "ACTIVE";
    public static final String DISC_REVOKED = "REVOKED";

    // ============ 裁定版本变更类型 ============
    public static final String CHANGE_AUTO = "AUTO";
    public static final String CHANGE_MANUAL = "MANUAL";
    public static final String CHANGE_FIRST_REVIEW = "FIRST_REVIEW";
    public static final String CHANGE_SECOND_REVIEW = "SECOND_REVIEW";
    public static final String CHANGE_REJECT = "REJECT";
    public static final String CHANGE_REOPEN = "REOPEN";

    // ============ 过程留痕类型 ============
    public static final String TRACE_EVIDENCE_CHANGED = "EVIDENCE_CHANGED";
    public static final String TRACE_SUPPLEMENT_APPROVED = "SUPPLEMENT_APPROVED";
    public static final String TRACE_SUPPLEMENT_REJECTED = "SUPPLEMENT_REJECTED";
    public static final String TRACE_DISCIPLINE_VOID = "DISCIPLINE_VOID";
    public static final String TRACE_DISCIPLINE_REVOKED = "DISCIPLINE_REVOKED";
    public static final String TRACE_RECLASSIFY = "RECLASSIFY";
    public static final String TRACE_NOTIFIED = "NOTIFIED";
    public static final String TRACE_RECALC = "RECALC";
    public static final String TRACE_REOPEN = "REOPEN";

    // ============ 通知类型 / 状态 ============
    public static final String NOTIFY_RULING_RESULT = "RULING_RESULT";
    public static final String NOTIFY_RULING_CHANGED = "RULING_CHANGED";
    public static final String NOTIFY_NEW = "NEW";
    public static final String NOTIFY_READ = "READ";

    /**
     * 等级合格口径：成绩有效且有等级，等级文本不含不合格类措辞即进合格名单。
     * 与等级规则的自由文本等级配套；如需更严格口径可在规则侧细化等级命名。
     */
    public static boolean isQualifiedGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return false;
        }
        String g = grade.trim();
        return !g.contains("不合格") && !g.contains("不通过") && !g.contains("不及格") && !g.contains("未通过");
    }
}
