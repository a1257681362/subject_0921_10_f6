package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.dto.RulingFinalReviewRequest;
import com.evops.absence.dto.RulingFirstReviewRequest;
import com.evops.absence.dto.RulingGenerateRequest;
import com.evops.absence.dto.RulingReclassifyRequest;
import com.evops.absence.entity.AbsenceNotification;
import com.evops.absence.entity.AbsenceRuling;
import com.evops.absence.entity.AbsenceRulingTrace;
import com.evops.absence.entity.AbsenceRulingVersion;
import com.evops.absence.entity.AttendanceRecord;
import com.evops.absence.entity.DisciplineRecord;
import com.evops.absence.mapper.AbsenceRulingMapper;
import com.evops.absence.mapper.AbsenceRulingTraceMapper;
import com.evops.absence.mapper.AbsenceRulingVersionMapper;
import com.evops.absence.vo.PassListItem;
import com.evops.absence.vo.RulingGenerateItem;
import com.evops.absence.vo.RulingGenerateResult;
import com.evops.absence.vo.RulingStats;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import com.evops.grade.entity.ExamScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺考裁定（模块 + 版本 + 状态）：
 * - 自动标缺考：按签到与违纪记录自动给缺考标记（未到考 ABSENT / 到考 NORMAL），弃考只能人工改型；
 * - 双人复核：PENDING 初审 → REVIEWING 终审（两个不同的人）→ CONFIRMED 最终裁定生效；初审可驳回；
 * - 结论决定成绩有没有效：有效进合格名单，无效写明原因并衔接下次安排，结论通知本人；
 * - 统计只认最终裁定；依据变化或裁定变更自动重开/置脏并重算统计；全过程版本只追加、trace 留痕。
 */
@Service
public class RulingService {

    private static final List<String> MARKS = Arrays.asList(
            AbsenceConstants.MARK_NORMAL, AbsenceConstants.MARK_ABSENT, AbsenceConstants.MARK_WAIVED);
    private static final List<String> VALIDITIES = Arrays.asList(
            AbsenceConstants.VALIDITY_VALID, AbsenceConstants.VALIDITY_INVALID);
    private static final List<String> REASONS = Arrays.asList(
            AbsenceConstants.REASON_NOT_CHECKED_IN,
            AbsenceConstants.REASON_DISCIPLINE_VOID,
            AbsenceConstants.REASON_WAIVED_EXAM,
            AbsenceConstants.REASON_OTHER);
    private static final List<String> ARRANGEMENTS = Arrays.asList(
            AbsenceConstants.NEXT_BATCH, AbsenceConstants.NEXT_MAKEUP,
            AbsenceConstants.NEXT_REAPPLY, AbsenceConstants.NEXT_NONE);

    private final AbsenceRulingMapper rulingMapper;
    private final AbsenceRulingVersionMapper versionMapper;
    private final AbsenceRulingTraceMapper traceMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final EvidenceSupport evidence;
    private final AbsenceNotificationService notificationService;

    public RulingService(AbsenceRulingMapper rulingMapper,
                         AbsenceRulingVersionMapper versionMapper,
                         AbsenceRulingTraceMapper traceMapper,
                         ExamBatchService batchService,
                         RegistrationService registrationService,
                         EvidenceSupport evidence,
                         AbsenceNotificationService notificationService) {
        this.rulingMapper = rulingMapper;
        this.versionMapper = versionMapper;
        this.traceMapper = traceMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.evidence = evidence;
        this.notificationService = notificationService;
    }

    /** 暴露证据口径给签到 / 违纪服务复用。 */
    EvidenceSupport evidence() {
        return evidence;
    }

    // ==================== 自动标缺考（两步：预检 / 执行） ====================

    @Transactional(readOnly = true)
    public RulingGenerateResult previewGenerate(RulingGenerateRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        List<Registration> regs = targetRegistrations(batch, request.getRegNos());
        boolean rebuild = Boolean.TRUE.equals(request.getRebuild());

        List<RulingGenerateItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (Registration reg : regs) {
            RulingGenerateItem item = buildItem(reg);
            AbsenceRuling existing = byRegistration(reg.getId());
            if (existing != null) {
                item.setExistingStatus(existing.getStatus());
                if (AbsenceConstants.RULING_CONFIRMED.equals(existing.getStatus())) {
                    item.setActionable(false);
                    item.setNote("最终裁定已生效，不自动覆盖；依据变化请改型或由系统重开后重裁");
                } else if (AbsenceConstants.RULING_REVIEWING.equals(existing.getStatus())) {
                    item.setActionable(false);
                    item.setNote("裁定初审已通过、终审中，不能重刷（防止绕过初审）");
                } else if (rebuild) {
                    item.setActionable(true);
                    item.setNote("将按当前签到/违纪依据重开为待初审（PENDING）");
                    actionable++;
                } else {
                    item.setActionable(false);
                    item.setNote("已存在裁定（" + existing.getStatus() + "），幂等跳过；需重刷请带 rebuild=true");
                }
            } else {
                item.setActionable(true);
                item.setNote(AbsenceConstants.MARK_ABSENT.equals(item.getAutoAbsenceType())
                        ? "自动标缺考（ABSENT），建单待初审" : "到考正常（NORMAL），建单待初审");
                actionable++;
            }
            if (!item.isActionable()) {
                skip++;
            }
            items.add(item);
        }

        RulingGenerateResult result = new RulingGenerateResult();
        result.setBatchId(batch.getId());
        result.setBatchCode(batch.getCode());
        result.setTotalScanned(items.size());
        result.setActionableCount(actionable);
        result.setSkipCount(skip);
        result.setSummary(String.format("批次 %s 扫描 %d 人：可建单/重刷 %d，跳过 %d",
                batch.getCode(), items.size(), actionable, skip));
        result.setItems(items);
        return result;
    }

    @Transactional
    public RulingGenerateResult generate(RulingGenerateRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        List<Registration> regs = targetRegistrations(batch, request.getRegNos());
        boolean rebuild = Boolean.TRUE.equals(request.getRebuild());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        List<RulingGenerateItem> items = new ArrayList<>();
        List<String> rulingNos = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (Registration reg : regs) {
            RulingGenerateItem item = buildItem(reg);
            AbsenceRuling existing = byRegistration(reg.getId());
            if (existing == null) {
                AbsenceRuling ruling = createRuling(batch, reg, AbsenceConstants.CHANGE_AUTO,
                        "按签到与违纪记录自动标缺考", operator, now);
                rulingNos.add(ruling.getRulingNo());
                item.setExistingStatus(null);
                item.setActionable(true);
                item.setNote("已建单：" + ruling.getAbsenceType());
                actionable++;
            } else if (rebuild
                    && (AbsenceConstants.RULING_PENDING.equals(existing.getStatus())
                    || AbsenceConstants.RULING_REJECTED.equals(existing.getStatus()))) {
                reopenRuling(existing, evidence.autoMark(reg.getId()),
                        AbsenceConstants.CHANGE_REOPEN, "按当前签到/违纪依据重刷裁定", operator, now);
                rulingNos.add(existing.getRulingNo());
                item.setExistingStatus(AbsenceConstants.RULING_PENDING);
                item.setActionable(true);
                item.setNote("已重开为待初审：" + existing.getAbsenceType());
                actionable++;
            } else {
                item.setExistingStatus(existing.getStatus());
                item.setActionable(false);
                item.setNote(AbsenceConstants.RULING_CONFIRMED.equals(existing.getStatus())
                        ? "最终裁定已生效，跳过"
                        : AbsenceConstants.RULING_REVIEWING.equals(existing.getStatus())
                        ? "终审中，不能重刷，跳过" : "已存在裁定，幂等跳过（rebuild=false）");
                skip++;
            }
            items.add(item);
        }

        RulingGenerateResult result = new RulingGenerateResult();
        result.setBatchId(batch.getId());
        result.setBatchCode(batch.getCode());
        result.setTotalScanned(items.size());
        result.setActionableCount(actionable);
        result.setSkipCount(skip);
        result.setRulingNos(rulingNos);
        result.setSummary(String.format("批次 %s 处理 %d 人：建单/重刷 %d，跳过 %d",
                batch.getCode(), items.size(), actionable, skip));
        result.setItems(items);
        return result;
    }

    private RulingGenerateItem buildItem(Registration reg) {
        RulingGenerateItem item = new RulingGenerateItem();
        item.setRegNo(reg.getRegNo());
        item.setName(reg.getName());
        item.setAutoAbsenceType(evidence.autoMark(reg.getId()));
        AttendanceRecord att = evidence.validAttendance(reg.getId());
        item.setCheckedIn(att != null);
        item.setCheckInAt(att == null ? null : att.getCheckInAt().toString());
        List<String> types = new ArrayList<>();
        for (DisciplineRecord d : evidence.activeDisciplines(reg.getId())) {
            types.add(d.getDisciplineType());
        }
        item.setDisciplineTypes(types);
        item.setScoreVoid(evidence.hasScoreVoidDiscipline(reg.getId()));
        ExamScore score = evidence.scoreOf(reg.getId());
        item.setHasScore(score != null);
        item.setGrade(score == null ? null : score.getGrade());
        return item;
    }

    private List<Registration> targetRegistrations(ExamBatch batch, List<String> regNos) {
        List<Registration> all = registrationService.listByBatch(batch.getId());
        if (regNos == null || regNos.isEmpty()) {
            return all;
        }
        Map<String, Registration> byNo = new LinkedHashMap<>();
        for (Registration reg : all) {
            byNo.put(reg.getRegNo(), reg);
        }
        List<Registration> picked = new ArrayList<>();
        for (String no : regNos) {
            Registration reg = byNo.get(no == null ? null : no.trim());
            if (reg == null) {
                throw new BusinessException("REG_NOT_IN_BATCH",
                        "报名号不属于批次 " + batch.getCode() + "：" + no);
            }
            picked.add(reg);
        }
        return picked;
    }

    // ==================== 双人复核 ====================

    /** 初审（复核第一人）：通过送终审；驳回终止，须重开后再走。 */
    @Transactional
    public AbsenceRuling firstReview(Long rulingId, RulingFirstReviewRequest request) {
        AbsenceRuling ruling = requireRuling(rulingId);
        if (!AbsenceConstants.RULING_PENDING.equals(ruling.getStatus())) {
            throw new BusinessException("RULING_NOT_PENDING",
                    "裁定 " + ruling.getRulingNo() + " 当前状态 " + ruling.getStatus() + "，仅待初审（PENDING）可初审");
        }
        String reviewerCode = request.getReviewerCode().trim();
        // 初审人不能是建单人（自动建单人为 system / 触发人）——双人复核第一关
        if (reviewerCode.equals(ruling.getInitiatorCode())) {
            throw new BusinessException("REVIEWER_SAME_AS_INITIATOR",
                    "初审人不能是裁定发起人本人：" + reviewerCode);
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        ruling.setFirstReviewerCode(reviewerCode);
        ruling.setFirstReviewerName(request.getReviewerName());
        ruling.setFirstReviewedAt(now);
        ruling.setFirstReviewComment(request.getComment() == null ? null : request.getComment().trim());
        ruling.setOperator(operator);

        if (Boolean.FALSE.equals(request.getApproved())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessException("FIRST_REVIEW_COMMENT_REQUIRED", "初审驳回必须填写意见");
            }
            ruling.setStatus(AbsenceConstants.RULING_REJECTED);
            ruling.setCurrentVersion(ruling.getCurrentVersion() + 1);
            rulingMapper.updateById(ruling);
            appendVersion(ruling, AbsenceConstants.CHANGE_REJECT,
                    "初审驳回：" + request.getComment().trim(), operator, now);
            return ruling;
        }

        ruling.setStatus(AbsenceConstants.RULING_REVIEWING);
        ruling.setCurrentVersion(ruling.getCurrentVersion() + 1);
        rulingMapper.updateById(ruling);
        appendVersion(ruling, AbsenceConstants.CHANGE_FIRST_REVIEW,
                request.getComment() == null ? "初审通过，送终审" : "初审通过：" + request.getComment().trim(),
                operator, now);
        return ruling;
    }

    /** 终审（复核第二人，必须与初审人不同）：落最终结论、成绩快照、通知本人。 */
    @Transactional
    public AbsenceRuling finalReview(Long rulingId, RulingFinalReviewRequest request) {
        AbsenceRuling ruling = requireRuling(rulingId);
        if (!AbsenceConstants.RULING_REVIEWING.equals(ruling.getStatus())) {
            throw new BusinessException("RULING_NOT_REVIEWING",
                    "裁定 " + ruling.getRulingNo() + " 当前状态 " + ruling.getStatus() + "，仅待终审（REVIEWING）可终审");
        }
        String reviewerCode = request.getReviewerCode().trim();
        if (reviewerCode.equals(ruling.getFirstReviewerCode())) {
            throw new BusinessException("REVIEWER_SAME_AS_FIRST",
                    "终审人不能与初审人为同一人（双人复核）：" + reviewerCode);
        }
        String validity = request.getScoreValidity().trim().toUpperCase();
        if (!VALIDITIES.contains(validity)) {
            throw new BusinessException("VALIDITY_INVALID", "成绩有效性仅支持 VALID / INVALID：" + validity);
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 缺考 / 弃考结论只能是成绩无效
        if (AbsenceConstants.VALIDITY_VALID.equals(validity)
                && !AbsenceConstants.MARK_NORMAL.equals(ruling.getAbsenceType())) {
            throw new BusinessException("RULING_VALIDITY_CONFLICT",
                    "缺考标记为 " + ruling.getAbsenceType() + " 的裁定成绩只能判无效（INVALID），不能进合格名单");
        }

        String reasonCode = null;
        String reasonText = null;
        String arrangement = null;
        String arrangementText = null;
        if (AbsenceConstants.VALIDITY_INVALID.equals(validity)) {
            reasonCode = requireCode(request.getInvalidReason(), REASONS, "无效原因");
            if (request.getInvalidReasonText() == null || request.getInvalidReasonText().trim().isEmpty()) {
                throw new BusinessException("INVALID_REASON_TEXT_REQUIRED", "成绩无效必须写明原因 invalidReasonText");
            }
            reasonText = request.getInvalidReasonText().trim();
            arrangement = requireCode(request.getNextArrangement(), ARRANGEMENTS, "下次安排");
            arrangementText = request.getNextArrangementText() == null ? null
                    : request.getNextArrangementText().trim();
        }

        // 冻结成绩快照（以当前成绩行口径为准；成绩行永久记规则版本号）
        ExamScore score = evidence.scoreOf(ruling.getRegistrationId());
        fillScoreSnapshot(ruling, score);

        ruling.setScoreValidity(validity);
        ruling.setInvalidReason(reasonCode);
        ruling.setInvalidReasonText(reasonText);
        ruling.setNextArrangement(arrangement);
        ruling.setNextArrangementText(arrangementText);
        boolean qualified = AbsenceConstants.VALIDITY_VALID.equals(validity)
                && AbsenceConstants.isQualifiedGrade(ruling.getGrade());
        ruling.setQualified(qualified);
        ruling.setSecondReviewerCode(reviewerCode);
        ruling.setSecondReviewerName(request.getReviewerName());
        ruling.setSecondReviewedAt(now);
        ruling.setSecondReviewComment(request.getComment() == null ? null : request.getComment().trim());
        ruling.setStatus(AbsenceConstants.RULING_CONFIRMED);
        ruling.setCurrentVersion(ruling.getCurrentVersion() + 1);
        ruling.setStatsDirty(false);
        ruling.setStatsCalculatedAt(now);
        ruling.setOperator(operator);
        rulingMapper.updateById(ruling);
        appendVersion(ruling, AbsenceConstants.CHANGE_SECOND_REVIEW,
                request.getComment() == null ? "终审确认，最终裁定生效" : "终审：" + request.getComment().trim(),
                operator, now);

        // 此前已有过 CONFIRMED 版本说明是重裁 → 按"裁定变更"再次通知本人
        boolean changed = countConfirmedVersions(ruling.getId()) > 1;
        String notifyType = changed ? AbsenceConstants.NOTIFY_RULING_CHANGED
                : AbsenceConstants.NOTIFY_RULING_RESULT;
        String title = changed ? "缺考裁定结论变更通知" : "缺考裁定结论通知";
        AbsenceNotification n = notificationService.notify(ruling.getBatchId(), ruling.getRegistrationId(),
                ruling.getRegNo(), ruling.getName(), ruling.getId(), notifyType, title,
                buildNotifyContent(ruling), operator);
        ruling.setNotifiedAt(n.getNotifiedAt());
        rulingMapper.updateById(ruling);
        addTrace(ruling, AbsenceConstants.TRACE_NOTIFIED,
                (changed ? "裁定变更，" : "") + "结论已通知本人（通知 id=" + n.getId() + "）", operator, now);
        return ruling;
    }

    // ==================== 缺考 / 弃考改型（分清类型；已生效改型重开走新版本） ====================

    @Transactional
    public AbsenceRuling reclassify(Long rulingId, RulingReclassifyRequest request) {
        AbsenceRuling ruling = requireRuling(rulingId);
        String target = request.getAbsenceType().trim().toUpperCase();
        if (!MARKS.contains(target)) {
            throw new BusinessException("ABSENCE_TYPE_INVALID",
                    "缺考标记仅支持 NORMAL / ABSENT / WAIVED：" + target);
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (AbsenceConstants.RULING_CONFIRMED.equals(ruling.getStatus())) {
            String oldMark = ruling.getAbsenceType();
            // 已生效裁定改型：原结论失效，重开待初审，双人复核与通知重来，统计随后重算
            reopenRuling(ruling, target, AbsenceConstants.CHANGE_REOPEN,
                    "缺考标记改型为 " + target + "：" + request.getReason().trim(), operator, now);
            addTrace(ruling, AbsenceConstants.TRACE_RECLASSIFY,
                    "缺考标记由 " + oldMark + " 变更为 " + target
                            + "（" + request.getReason().trim() + "），已生效裁定重开", operator, now);
            return ruling;
        }

        String old = ruling.getAbsenceType();
        ruling.setAbsenceType(target);
        // 驳回单借改型重新进入初审流程
        if (AbsenceConstants.RULING_REJECTED.equals(ruling.getStatus())) {
            ruling.setStatus(AbsenceConstants.RULING_PENDING);
        }
        ruling.setCurrentVersion(ruling.getCurrentVersion() + 1);
        ruling.setOperator(operator);
        rulingMapper.updateById(ruling);
        appendVersion(ruling, AbsenceConstants.CHANGE_MANUAL,
                "缺考标记改型 " + old + " → " + target + "：" + request.getReason().trim(), operator, now);
        addTrace(ruling, AbsenceConstants.TRACE_RECLASSIFY,
                "缺考标记由 " + old + " 变更为 " + target + "：" + request.getReason().trim(), operator, now);
        return ruling;
    }

    // ==================== 依据变化联动（签到 / 违纪服务调用） ====================

    /**
     * 签到补录审批、现场签到、违纪作废 / 撤销后调用：
     * - 无裁定：不处理（后续批量自动建单按新依据判定）；
     * - 已生效：依据若导致自动标记变化则重开重裁，未变化也置脏提示统计复核重算；
     * - 过程中：直接刷新工作标记（PENDING/REVIEWING），驳回单不动，留痕。
     */
    @Transactional
    public void onEvidenceChanged(Long registrationId, String traceType, String detail, String operator) {
        AbsenceRuling ruling = byRegistration(registrationId);
        if (ruling == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        addTrace(ruling, traceType, detail, operator, now);

        String autoMark = evidence.autoMark(registrationId);
        if (AbsenceConstants.RULING_CONFIRMED.equals(ruling.getStatus())) {
            // 到考口径翻转（未签到↔已签到）、出现作废违纪、或原"违纪作废"结论所依据的作废违纪已不存在：
            // 最终结论可能被推翻，必须重开重裁
            boolean disciplineVoidGone = AbsenceConstants.REASON_DISCIPLINE_VOID.equals(ruling.getInvalidReason())
                    && !evidence.hasScoreVoidDiscipline(registrationId);
            if (!autoMark.equals(ruling.getAbsenceType())
                    || evidence.hasScoreVoidDiscipline(registrationId)
                    || disciplineVoidGone) {
                reopenRuling(ruling, autoMark, AbsenceConstants.CHANGE_REOPEN,
                        "裁定依据变化（" + detail + "），原最终裁定重开重裁", operator, now);
                addTrace(ruling, AbsenceConstants.TRACE_REOPEN,
                        "依据变化自动重开，自动标记 " + autoMark + "，须重新双人复核", operator, now);
            } else {
                // 依据变了但结论口径不变：置脏，统计重算后清
                ruling.setStatsDirty(true);
                ruling.setOperator(operator);
                rulingMapper.updateById(ruling);
            }
            return;
        }
        if (AbsenceConstants.RULING_PENDING.equals(ruling.getStatus())
                || AbsenceConstants.RULING_REVIEWING.equals(ruling.getStatus())) {
            if (!autoMark.equals(ruling.getAbsenceType())) {
                ruling.setAbsenceType(autoMark);
                ruling.setEvidenceSummary(evidence.evidenceJson(registrationId));
                ruling.setOperator(operator);
                rulingMapper.updateById(ruling);
                addTrace(ruling, AbsenceConstants.TRACE_EVIDENCE_CHANGED,
                        "过程中按新依据刷新工作标记为 " + autoMark, operator, now);
            }
        }
        // REJECTED 单不自动复活，须显式改型或 rebuild 重刷
    }

    // ==================== 合格名单 / 统计（只认最终裁定） ====================

    /** 合格名单：最终裁定 VALID 且等级合格；成绩口径取裁定冻结快照。 */
    @Transactional(readOnly = true)
    public List<PassListItem> passList(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        List<AbsenceRuling> confirmed = rulingMapper.selectList(new QueryWrapper<AbsenceRuling>()
                .eq("batch_id", batch.getId())
                .eq("status", AbsenceConstants.RULING_CONFIRMED)
                .eq("score_validity", AbsenceConstants.VALIDITY_VALID)
                .eq("qualified", true)
                .orderByDesc("paper_score")
                .orderByAsc("reg_no"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<PassListItem> list = new ArrayList<>();
        for (AbsenceRuling r : confirmed) {
            PassListItem item = new PassListItem();
            item.setRulingNo(r.getRulingNo());
            item.setRegNo(r.getRegNo());
            item.setName(r.getName());
            item.setPaperScore(r.getPaperScore());
            item.setGrade(r.getGrade());
            item.setRuleVersionNo(r.getRuleVersionNo());
            item.setConfirmedAt(r.getSecondReviewedAt() == null ? null
                    : r.getSecondReviewedAt().format(fmt));
            list.add(item);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public RulingStats stats(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        List<Registration> regs = registrationService.listByBatch(batch.getId());
        List<AbsenceRuling> rulings = rulingMapper.selectList(new QueryWrapper<AbsenceRuling>()
                .eq("batch_id", batch.getId()));

        RulingStats stats = new RulingStats();
        stats.setBatchId(batch.getId());
        stats.setBatchCode(batch.getCode());
        stats.setTotalRegistrations(regs.size());
        stats.setRulingCount(rulings.size());
        stats.setUnruledCount(regs.size() - rulings.size());

        int confirmed = 0;
        int valid = 0;
        int qualified = 0;
        int dirty = 0;
        for (AbsenceRuling r : rulings) {
            switch (r.getStatus()) {
                case AbsenceConstants.RULING_PENDING:
                    stats.setPendingCount(stats.getPendingCount() + 1);
                    break;
                case AbsenceConstants.RULING_REVIEWING:
                    stats.setReviewingCount(stats.getReviewingCount() + 1);
                    break;
                case AbsenceConstants.RULING_REJECTED:
                    stats.setRejectedCount(stats.getRejectedCount() + 1);
                    break;
                case AbsenceConstants.RULING_CONFIRMED:
                    confirmed++;
                    if (AbsenceConstants.MARK_NORMAL.equals(r.getAbsenceType())) {
                        stats.setNormalCount(stats.getNormalCount() + 1);
                    } else if (AbsenceConstants.MARK_ABSENT.equals(r.getAbsenceType())) {
                        stats.setAbsentCount(stats.getAbsentCount() + 1);
                    } else if (AbsenceConstants.MARK_WAIVED.equals(r.getAbsenceType())) {
                        stats.setWaivedCount(stats.getWaivedCount() + 1);
                    }
                    if (AbsenceConstants.VALIDITY_VALID.equals(r.getScoreValidity())) {
                        valid++;
                    } else if (AbsenceConstants.VALIDITY_INVALID.equals(r.getScoreValidity())) {
                        stats.setInvalidCount(stats.getInvalidCount() + 1);
                        if (r.getInvalidReason() != null) {
                            stats.getInvalidReasonCounts().merge(r.getInvalidReason(), 1, Integer::sum);
                        }
                        if (r.getNextArrangement() != null) {
                            stats.getNextArrangementCounts().merge(r.getNextArrangement(), 1, Integer::sum);
                        }
                    }
                    if (Boolean.TRUE.equals(r.getQualified())) {
                        qualified++;
                    }
                    break;
                default:
                    break;
            }
            if (Boolean.TRUE.equals(r.getStatsDirty())) {
                dirty++;
            }
        }
        stats.setConfirmedCount(confirmed);
        stats.setValidCount(valid);
        stats.setQualifiedCount(qualified);
        stats.setDirtyCount(dirty);
        if (confirmed > 0) {
            stats.setQualifiedRate(round2(qualified * 100.0 / confirmed));
            stats.setAbsentRate(round2((stats.getAbsentCount() + stats.getWaivedCount()) * 100.0 / confirmed));
        }
        AbsenceRuling latest = rulings.stream()
                .filter(r -> r.getStatsCalculatedAt() != null)
                .max((a, b) -> a.getStatsCalculatedAt().compareTo(b.getStatsCalculatedAt()))
                .orElse(null);
        stats.setCalculatedAt(latest == null ? null : latest.getStatsCalculatedAt().toString());
        return stats;
    }

    /**
     * 重算批次统计：为最终有效但裁定时成绩尚未录入的裁定补冻成绩快照、重算合格标记；
     * 清除脏标记并落重算时间，全过程留痕。
     */
    @Transactional
    public RulingStats recalculate(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        List<AbsenceRuling> rulings = rulingMapper.selectList(new QueryWrapper<AbsenceRuling>()
                .eq("batch_id", batch.getId()));
        for (AbsenceRuling r : rulings) {
            if (!AbsenceConstants.RULING_CONFIRMED.equals(r.getStatus())) {
                continue;
            }
            boolean changed = false;
            if (r.getScoreId() == null) {
                ExamScore score = evidence.scoreOf(r.getRegistrationId());
                if (score != null) {
                    fillScoreSnapshot(r, score);
                    changed = true;
                    if (AbsenceConstants.VALIDITY_VALID.equals(r.getScoreValidity())) {
                        r.setQualified(AbsenceConstants.isQualifiedGrade(r.getGrade()));
                    }
                }
            }
            if (changed || Boolean.TRUE.equals(r.getStatsDirty())) {
                r.setStatsDirty(false);
                r.setStatsCalculatedAt(now);
                r.setOperator(operator);
                rulingMapper.updateById(r);
                addTrace(r, AbsenceConstants.TRACE_RECALC,
                        (changed ? "补冻成绩快照并" : "") + "依据/裁定变更后重算统计口径", operator, now);
            }
        }
        return stats(batchId);
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<AbsenceRuling> list(Long batchId, String status, String absenceType, String validity) {
        QueryWrapper<AbsenceRuling> wrapper = new QueryWrapper<AbsenceRuling>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (absenceType != null && !absenceType.trim().isEmpty()) {
            wrapper.eq("absence_type", absenceType.trim().toUpperCase());
        }
        if (validity != null && !validity.trim().isEmpty()) {
            wrapper.eq("score_validity", validity.trim().toUpperCase());
        }
        return rulingMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public AbsenceRuling get(Long id) {
        return requireRuling(id);
    }

    @Transactional(readOnly = true)
    public List<AbsenceRulingVersion> versions(Long id) {
        requireRuling(id);
        return versionMapper.selectList(new QueryWrapper<AbsenceRulingVersion>()
                .eq("ruling_id", id)
                .orderByAsc("version_no"));
    }

    @Transactional(readOnly = true)
    public List<AbsenceRulingTrace> traces(Long id) {
        requireRuling(id);
        return traceMapper.selectList(new QueryWrapper<AbsenceRulingTrace>()
                .eq("ruling_id", id)
                .orderByAsc("traced_at", "id"));
    }

    // ==================== 内部 ====================

    private AbsenceRuling createRuling(ExamBatch batch, Registration reg, String changeType,
                                       String reason, String operator, LocalDateTime now) {
        String mark = evidence.autoMark(reg.getId());
        AbsenceRuling ruling = new AbsenceRuling();
        ruling.setRulingNo(nextRulingNo(batch));
        ruling.setBatchId(batch.getId());
        ruling.setRegistrationId(reg.getId());
        ruling.setRegNo(reg.getRegNo());
        ruling.setName(reg.getName());
        ruling.setAbsenceType(mark);
        ruling.setStatus(AbsenceConstants.RULING_PENDING);
        ruling.setCurrentVersion(1);
        ruling.setEvidenceSummary(evidence.evidenceJson(reg.getId()));
        ruling.setQualified(false);
        ruling.setStatsDirty(false);
        // 发起人记系统（自动）或当前经办人（重刷由人触发时 changeType=REOPEN，另行记录）
        ruling.setInitiatorCode(AbsenceConstants.CHANGE_AUTO.equals(changeType) ? "system" : operator);
        ruling.setInitiatorName(AbsenceConstants.CHANGE_AUTO.equals(changeType) ? "系统自动判定" : operator);
        fillScoreSnapshot(ruling, evidence.scoreOf(reg.getId()));
        ruling.setOperator(operator);
        rulingMapper.insert(ruling);
        appendVersion(ruling, changeType, reason + "（自动标记 " + mark + "）", operator, now);
        return ruling;
    }

    /** 重开：原最终结论失效，回到待初审，版本链追加一版（原版本全部保留）。 */
    private void reopenRuling(AbsenceRuling ruling, String mark, String changeType,
                              String reason, String operator, LocalDateTime now) {
        ruling.setAbsenceType(mark);
        ruling.setStatus(AbsenceConstants.RULING_PENDING);
        ruling.setCurrentVersion(ruling.getCurrentVersion() + 1);
        ruling.setEvidenceSummary(evidence.evidenceJson(ruling.getRegistrationId()));
        ruling.setScoreValidity(null);
        ruling.setInvalidReason(null);
        ruling.setInvalidReasonText(null);
        ruling.setNextArrangement(null);
        ruling.setNextArrangementText(null);
        ruling.setQualified(false);
        ruling.setFirstReviewerCode(null);
        ruling.setFirstReviewerName(null);
        ruling.setFirstReviewedAt(null);
        ruling.setFirstReviewComment(null);
        ruling.setSecondReviewerCode(null);
        ruling.setSecondReviewerName(null);
        ruling.setSecondReviewedAt(null);
        ruling.setSecondReviewComment(null);
        ruling.setNotifiedAt(null);
        ruling.setStatsDirty(true);
        ruling.setOperator(operator);
        // MyBatis-Plus updateById 默认忽略 null，会清不掉旧结论；这里显式 SET（含置空）
        UpdateWrapper<AbsenceRuling> uw = new UpdateWrapper<AbsenceRuling>()
                .eq("id", ruling.getId())
                .set("absence_type", mark)
                .set("status", AbsenceConstants.RULING_PENDING)
                .set("current_version", ruling.getCurrentVersion())
                .set("evidence_summary", ruling.getEvidenceSummary())
                .set("score_validity", null)
                .set("invalid_reason", null)
                .set("invalid_reason_text", null)
                .set("next_arrangement", null)
                .set("next_arrangement_text", null)
                .set("qualified", false)
                .set("first_reviewer_code", null)
                .set("first_reviewer_name", null)
                .set("first_reviewed_at", null)
                .set("first_review_comment", null)
                .set("second_reviewer_code", null)
                .set("second_reviewer_name", null)
                .set("second_reviewed_at", null)
                .set("second_review_comment", null)
                .set("notified_at", null)
                .set("stats_dirty", true)
                .set("operator", operator)
                .set("update_time", now);
        rulingMapper.update(null, uw);
        appendVersion(ruling, changeType, reason + "（工作标记 " + mark + "）", operator, now);
    }

    private void fillScoreSnapshot(AbsenceRuling ruling, ExamScore score) {
        ruling.setScoreId(score == null ? null : score.getId());
        ruling.setPaperScore(score == null ? null : score.getPaperScore());
        ruling.setGrade(score == null ? null : score.getGrade());
        ruling.setRuleVersionNo(score == null ? null : score.getRuleVersionNo());
    }

    private void appendVersion(AbsenceRuling ruling, String changeType, String reason,
                               String operator, LocalDateTime now) {
        AbsenceRulingVersion v = new AbsenceRulingVersion();
        v.setRulingId(ruling.getId());
        v.setVersionNo(ruling.getCurrentVersion());
        v.setAbsenceType(ruling.getAbsenceType());
        v.setStatus(ruling.getStatus());
        v.setScoreValidity(ruling.getScoreValidity());
        v.setInvalidReason(ruling.getInvalidReason());
        v.setInvalidReasonText(ruling.getInvalidReasonText());
        v.setNextArrangement(ruling.getNextArrangement());
        v.setNextArrangementText(ruling.getNextArrangementText());
        v.setScoreId(ruling.getScoreId());
        v.setPaperScore(ruling.getPaperScore());
        v.setGrade(ruling.getGrade());
        v.setRuleVersionNo(ruling.getRuleVersionNo());
        v.setQualified(Boolean.TRUE.equals(ruling.getQualified()));
        v.setChangeType(changeType);
        v.setChangeReason(reason);
        v.setOperator(operator);
        v.setChangedAt(now);
        versionMapper.insert(v);
    }

    private void addTrace(AbsenceRuling ruling, String type, String detail,
                          String operator, LocalDateTime now) {
        AbsenceRulingTrace trace = new AbsenceRulingTrace();
        trace.setRulingId(ruling.getId());
        trace.setBatchId(ruling.getBatchId());
        trace.setTraceType(type);
        trace.setDetail(detail);
        trace.setOperator(operator);
        trace.setTracedAt(now);
        traceMapper.insert(trace);
    }

    private long countConfirmedVersions(Long rulingId) {
        Long c = versionMapper.selectCount(new QueryWrapper<AbsenceRulingVersion>()
                .eq("ruling_id", rulingId)
                .eq("status", AbsenceConstants.RULING_CONFIRMED));
        return c == null ? 0 : c;
    }

    private String buildNotifyContent(AbsenceRuling r) {
        StringBuilder sb = new StringBuilder();
        sb.append("您在批次中的考试缺考裁定已完成。");
        sb.append("缺考标记：").append(markText(r.getAbsenceType())).append("；");
        if (AbsenceConstants.VALIDITY_VALID.equals(r.getScoreValidity())) {
            sb.append("成绩有效");
            if (r.getPaperScore() != null) {
                sb.append("，卷面分 ").append(r.getPaperScore())
                        .append("，等级 ").append(r.getGrade() == null ? "未定级" : r.getGrade());
            }
            sb.append("，已进入合格名单。");
        } else {
            sb.append("成绩无效");
            sb.append("，原因：").append(reasonText(r.getInvalidReason()));
            if (r.getInvalidReasonText() != null) {
                sb.append("（").append(r.getInvalidReasonText()).append("）");
            }
            sb.append("；下次安排：").append(arrangementText(r.getNextArrangement()));
            if (r.getNextArrangementText() != null) {
                sb.append("（").append(r.getNextArrangementText()).append("）");
            }
            sb.append("。");
        }
        sb.append("裁定号 ").append(r.getRulingNo()).append("，如有异议请在公示期内向考试机构提出。");
        return sb.toString();
    }

    private String markText(String mark) {
        if (AbsenceConstants.MARK_ABSENT.equals(mark)) {
            return "缺考（应到未到）";
        }
        if (AbsenceConstants.MARK_WAIVED.equals(mark)) {
            return "弃考（本人主动放弃）";
        }
        return "正常到考";
    }

    private String reasonText(String code) {
        if (code == null) {
            return "未注明";
        }
        switch (code) {
            case AbsenceConstants.REASON_NOT_CHECKED_IN: return "未签到（应考未到）";
            case AbsenceConstants.REASON_DISCIPLINE_VOID: return "违纪情节严重，本场成绩作废";
            case AbsenceConstants.REASON_WAIVED_EXAM: return "本人弃考";
            case AbsenceConstants.REASON_OTHER: return "其他原因";
            default: return code;
        }
    }

    private String arrangementText(String code) {
        if (code == null) {
            return "未安排";
        }
        switch (code) {
            case AbsenceConstants.NEXT_BATCH: return "顺延下次批次";
            case AbsenceConstants.NEXT_MAKEUP: return "安排补考";
            case AbsenceConstants.NEXT_REAPPLY: return "重新报名";
            case AbsenceConstants.NEXT_NONE: return "不安排";
            default: return code;
        }
    }

    private String requireCode(String code, List<String> allowed, String field) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("RULING_CODE_REQUIRED", field + "不能为空");
        }
        String normalized = code.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException("RULING_CODE_INVALID", field + "取值不支持：" + code);
        }
        return normalized;
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private AbsenceRuling byRegistration(Long registrationId) {
        return rulingMapper.selectOne(new QueryWrapper<AbsenceRuling>()
                .eq("registration_id", registrationId));
    }

    private AbsenceRuling requireRuling(Long id) {
        if (id == null) {
            throw new BusinessException("RULING_ID_REQUIRED", "缺考裁定 id 不能为空");
        }
        AbsenceRuling ruling = rulingMapper.selectById(id);
        if (ruling == null) {
            throw new BusinessException("RULING_NOT_FOUND", "缺考裁定不存在：id=" + id);
        }
        return ruling;
    }

    /** 裁定号：QK + 批次编码 + 4 位流水（按批次递增，唯一约束兜底）。 */
    private String nextRulingNo(ExamBatch batch) {
        String prefix = "QK" + batch.getCode() + "-";
        int max = 0;
        List<AbsenceRuling> exist = rulingMapper.selectList(new QueryWrapper<AbsenceRuling>()
                .likeRight("ruling_no", prefix));
        for (AbsenceRuling r : exist) {
            String no = r.getRulingNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
