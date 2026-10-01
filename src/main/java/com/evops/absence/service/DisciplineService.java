package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.common.TimeParser;
import com.evops.absence.dto.DisciplineRequest;
import com.evops.absence.dto.DisciplineRevokeRequest;
import com.evops.absence.entity.DisciplineRecord;
import com.evops.absence.mapper.DisciplineRecordMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 考场违纪记录（只追加）：
 * - 违纪登记随场次落 ACTIVE，score_void=true 表示情节严重、本场成绩作废；
 * - 撤销不删除，置 REVOKED 并留原因；
 * - 作废 / 撤销都联动缺考裁定（依据变化 → 重开 / 待重算，统计随变更重算）。
 */
@Service
public class DisciplineService {

    private static final List<String> TYPES = Arrays.asList(
            AbsenceConstants.DISC_CHEAT,
            AbsenceConstants.DISC_ENTRY_LATE,
            AbsenceConstants.DISC_EARLY_LEAVE,
            AbsenceConstants.DISC_ID_ISSUE,
            AbsenceConstants.DISC_OTHER);

    private final DisciplineRecordMapper disciplineMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final RulingService rulingService;

    public DisciplineService(DisciplineRecordMapper disciplineMapper,
                             ExamBatchService batchService,
                             RegistrationService registrationService,
                             RulingService rulingService) {
        this.disciplineMapper = disciplineMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.rulingService = rulingService;
    }

    @Transactional
    public DisciplineRecord record(DisciplineRequest request) {
        Registration reg = requireRegInBatch(request.getBatchId(), request.getRegNo());
        String type = request.getDisciplineType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException("DISCIPLINE_TYPE_INVALID",
                    "违纪类型仅支持 CHEAT / ENTRY_LATE / EARLY_LEAVE / ID_ISSUE / OTHER：" + type);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = TimeParser.parse(request.getOccurredAt(), now);
        if (occurredAt.isAfter(now)) {
            throw new BusinessException("DISCIPLINE_AT_FUTURE", "违纪发生时间不能晚于当前时间：" + occurredAt);
        }

        String operator = OperatorHolder.current();
        DisciplineRecord d = new DisciplineRecord();
        d.setBatchId(request.getBatchId());
        d.setRegistrationId(reg.getId());
        d.setRegNo(reg.getRegNo());
        d.setName(reg.getName());
        d.setRoomId(request.getRoomId());
        d.setDisciplineType(type);
        d.setDetail(request.getDetail().trim());
        d.setOccurredAt(occurredAt);
        d.setScoreVoid(Boolean.TRUE.equals(request.getScoreVoid()));
        d.setStatus(AbsenceConstants.DISC_ACTIVE);
        d.setRecorder(operator);
        disciplineMapper.insert(d);

        // 新增作废违纪：到考但成绩应无效，联动既有裁定（自动建单也可据此在终审给 INVALID）
        if (Boolean.TRUE.equals(d.getScoreVoid())) {
            rulingService.onEvidenceChanged(reg.getId(),
                    AbsenceConstants.TRACE_DISCIPLINE_VOID,
                    "登记违纪（" + type + "）且情节严重，本场成绩作废：" + d.getDetail(), operator);
        }
        return d;
    }

    /** 撤销违纪：记录保留置 REVOKED，不再作为裁定依据，联动重开裁定。 */
    @Transactional
    public DisciplineRecord revoke(Long id, DisciplineRevokeRequest request) {
        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            throw new BusinessException("DISCIPLINE_NOT_FOUND", "违纪记录不存在：id=" + id);
        }
        if (!AbsenceConstants.DISC_ACTIVE.equals(d.getStatus())) {
            throw new BusinessException("DISCIPLINE_NOT_ACTIVE",
                    "仅有效违纪可撤销，当前状态：" + d.getStatus());
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        d.setStatus(AbsenceConstants.DISC_REVOKED);
        d.setRevokeReason(request.getReason().trim());
        d.setRevokedBy(operator);
        d.setRevokedAt(now);
        disciplineMapper.updateById(d);

        rulingService.onEvidenceChanged(d.getRegistrationId(),
                AbsenceConstants.TRACE_DISCIPLINE_REVOKED,
                "违纪记录撤销（原类型 " + d.getDisciplineType() + "）：" + request.getReason().trim(),
                operator);
        return d;
    }

    @Transactional(readOnly = true)
    public List<DisciplineRecord> listByBatch(Long batchId, String status) {
        QueryWrapper<DisciplineRecord> wrapper = new QueryWrapper<DisciplineRecord>()
                .eq("batch_id", batchId)
                .orderByDesc("occurred_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return disciplineMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public DisciplineRecord get(Long id) {
        DisciplineRecord d = disciplineMapper.selectById(id);
        if (d == null) {
            throw new BusinessException("DISCIPLINE_NOT_FOUND", "违纪记录不存在：id=" + id);
        }
        return d;
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
