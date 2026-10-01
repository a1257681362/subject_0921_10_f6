package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.common.TimeParser;
import com.evops.absence.dto.AttendanceApproveRequest;
import com.evops.absence.dto.AttendanceCheckInRequest;
import com.evops.absence.dto.AttendanceSupplementRequest;
import com.evops.absence.entity.AttendanceRecord;
import com.evops.absence.mapper.AttendanceRecordMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员签到（模块）：
 * - 现场签到直接生效（RECORDED），计入到考口径；
 * - 补录一律走审批：PENDING_APPROVAL → APPROVED / REJECTED，批准才计入到考；
 * - 依据变化联动缺考裁定（标记待重算 / 重开），裁定有变更统计随之重算。
 */
@Service
public class AttendanceService {

    private final AttendanceRecordMapper attendanceMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final RulingService rulingService;

    public AttendanceService(AttendanceRecordMapper attendanceMapper,
                             ExamBatchService batchService,
                             RegistrationService registrationService,
                             RulingService rulingService) {
        this.attendanceMapper = attendanceMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.rulingService = rulingService;
    }

    // ---------- 现场签到（直接生效） ----------

    @Transactional
    public AttendanceRecord checkIn(AttendanceCheckInRequest request) {
        Registration reg = requireRegInBatch(request.getBatchId(), request.getRegNo());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInAt = TimeParser.parse(request.getCheckInAt(), now);
        if (checkInAt.isAfter(now)) {
            throw new BusinessException("CHECKIN_AT_FUTURE", "签到时间不能晚于当前时间：" + checkInAt);
        }
        // 已有任何生效签到（现场或已批准补录）幂等拒收，防止两个窗口重复登记
        if (rulingService.evidence().isCheckedIn(reg.getId())) {
            throw new BusinessException("ATTENDANCE_EXISTS",
                    "学员 " + reg.getRegNo() + " 已有生效签到，不能重复现场签到");
        }

        String operator = OperatorHolder.current();
        AttendanceRecord record = new AttendanceRecord();
        record.setBatchId(request.getBatchId());
        record.setRegistrationId(reg.getId());
        record.setRegNo(reg.getRegNo());
        record.setName(reg.getName());
        record.setRoomId(request.getRoomId());
        record.setCheckInAt(checkInAt);
        record.setSource(AbsenceConstants.SOURCE_ON_SITE);
        record.setStatus(AbsenceConstants.ATT_RECORDED);
        record.setOperator(operator);
        record.setRemark(request.getRemark());
        attendanceMapper.insert(record);

        // 到考依据变化：联动既有缺考裁定
        rulingService.onEvidenceChanged(reg.getId(),
                AbsenceConstants.TRACE_EVIDENCE_CHANGED,
                "现场签到生效（" + checkInAt + "），到考依据新增", operator);
        return record;
    }

    // ---------- 补录申请（待审批，不计到考） ----------

    @Transactional
    public AttendanceRecord applySupplement(AttendanceSupplementRequest request) {
        Registration reg = requireRegInBatch(request.getBatchId(), request.getRegNo());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInAt = TimeParser.parse(request.getCheckInAt(), null);
        if (checkInAt.isAfter(now)) {
            throw new BusinessException("SUPPLEMENT_AT_FUTURE",
                    "补录的实际到场时间不能晚于当前时间：" + checkInAt);
        }
        if (rulingService.evidence().isCheckedIn(reg.getId())) {
            throw new BusinessException("ATTENDANCE_EXISTS",
                    "学员 " + reg.getRegNo() + " 已有生效签到，无需补录");
        }
        // 已有待审批补录的不允许重复申请（先处理上一条）
        Long pending = attendanceMapper.selectCount(new QueryWrapper<AttendanceRecord>()
                .eq("registration_id", reg.getId())
                .eq("source", AbsenceConstants.SOURCE_SUPPLEMENT)
                .eq("status", AbsenceConstants.ATT_PENDING_APPROVAL));
        if (pending != null && pending > 0) {
            throw new BusinessException("SUPPLEMENT_PENDING_EXISTS",
                    "学员 " + reg.getRegNo() + " 已有待审批补录，请先处理，不能重复申请");
        }

        String operator = OperatorHolder.current();
        String applicantCode = request.getApplicantCode() == null || request.getApplicantCode().trim().isEmpty()
                ? operator : request.getApplicantCode().trim();
        AttendanceRecord record = new AttendanceRecord();
        record.setBatchId(request.getBatchId());
        record.setRegistrationId(reg.getId());
        record.setRegNo(reg.getRegNo());
        record.setName(reg.getName());
        record.setRoomId(request.getRoomId());
        record.setCheckInAt(checkInAt);
        record.setSource(AbsenceConstants.SOURCE_SUPPLEMENT);
        record.setStatus(AbsenceConstants.ATT_PENDING_APPROVAL);
        record.setApplyReason(request.getReason().trim());
        record.setAppliedBy(applicantCode);
        record.setAppliedAt(now);
        record.setOperator(operator);
        record.setRemark(request.getRemark());
        attendanceMapper.insert(record);
        return record;
    }

    // ---------- 补录审批（通过才计入到考，联动裁定） ----------

    @Transactional
    public AttendanceRecord approve(Long attendanceId, AttendanceApproveRequest request) {
        AttendanceRecord record = requireRecord(attendanceId);
        if (!AbsenceConstants.SOURCE_SUPPLEMENT.equals(record.getSource())
                || !AbsenceConstants.ATT_PENDING_APPROVAL.equals(record.getStatus())) {
            throw new BusinessException("ATTENDANCE_NOT_APPROVABLE",
                    "仅待审批的补录记录可审批，当前来源/状态：" + record.getSource() + "/" + record.getStatus());
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 审批人不能是补录申请人本人（申请与审批分离）
        if (request.getApproverCode().trim().equals(record.getAppliedBy())) {
            throw new BusinessException("APPROVER_SAME_AS_APPLICANT",
                    "补录审批人不能是申请人本人（申请与审批分离）：" + request.getApproverCode().trim());
        }

        if (Boolean.FALSE.equals(request.getApproved())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessException("APPROVE_COMMENT_REQUIRED", "驳回补录必须填写审批意见");
            }
            record.setStatus(AbsenceConstants.ATT_REJECTED);
            record.setApproverCode(request.getApproverCode().trim());
            record.setApproverName(request.getApproverName());
            record.setApprovedAt(now);
            record.setApproveComment(request.getComment().trim());
            record.setOperator(operator);
            attendanceMapper.updateById(record);

            rulingService.onEvidenceChanged(record.getRegistrationId(),
                    AbsenceConstants.TRACE_SUPPLEMENT_REJECTED,
                    "签到补录被驳回：" + request.getComment().trim(), operator);
            return record;
        }

        // 批准前再校一次：审批等待期间可能已有现场签到
        if (rulingService.evidence().isCheckedIn(record.getRegistrationId())) {
            throw new BusinessException("ATTENDANCE_EXISTS",
                    "等待审批期间学员 " + record.getRegNo() + " 已产生生效签到，补录不再批准");
        }
        record.setStatus(AbsenceConstants.ATT_APPROVED);
        record.setApproverCode(request.getApproverCode().trim());
        record.setApproverName(request.getApproverName());
        record.setApprovedAt(now);
        record.setApproveComment(request.getComment() == null ? null : request.getComment().trim());
        record.setOperator(operator);
        attendanceMapper.updateById(record);

        rulingService.onEvidenceChanged(record.getRegistrationId(),
                AbsenceConstants.TRACE_SUPPLEMENT_APPROVED,
                "签到补录经审批通过（到场时间 " + record.getCheckInAt() + "），计入到考口径",
                operator);
        return record;
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public List<AttendanceRecord> listByBatch(Long batchId, String status) {
        QueryWrapper<AttendanceRecord> wrapper = new QueryWrapper<AttendanceRecord>()
                .eq("batch_id", batchId)
                .orderByDesc("check_in_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return attendanceMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> listPending() {
        return attendanceMapper.selectList(new QueryWrapper<AttendanceRecord>()
                .eq("status", AbsenceConstants.ATT_PENDING_APPROVAL)
                .orderByAsc("applied_at", "id"));
    }

    @Transactional(readOnly = true)
    public AttendanceRecord get(Long id) {
        return requireRecord(id);
    }

    // ---------- 内部 ----------

    private AttendanceRecord requireRecord(Long id) {
        if (id == null) {
            throw new BusinessException("ATTENDANCE_ID_REQUIRED", "签到记录 id 不能为空");
        }
        AttendanceRecord record = attendanceMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("ATTENDANCE_NOT_FOUND", "签到记录不存在：id=" + id);
        }
        return record;
    }

    private Registration requireRegInBatch(Long batchId, String regNo) {
        ExamBatch batch = batchService.getBatch(batchId);
        Registration reg = registrationService.findByRegNo(regNo == null ? null : regNo.trim());
        if (reg == null || !reg.getBatchId().equals(batch.getId())) {
            throw new BusinessException("REG_NOT_IN_BATCH",
                    "报名号不属于批次 " + batch.getCode() + "：" + regNo);
        }
        return reg;
    }
}
