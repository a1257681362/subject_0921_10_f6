package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.entity.AttendanceRecord;
import com.evops.absence.entity.DisciplineRecord;
import com.evops.absence.mapper.AttendanceRecordMapper;
import com.evops.absence.mapper.DisciplineRecordMapper;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 裁定证据口径（唯一事实来源，签到 / 违纪 / 成绩三处共用，避免两个窗口各执一本账）：
 * - 到考：有现场已登记或补录已批准的签到；
 * - 作废：存在有效（ACTIVE）且 score_void=true 的违纪；
 * - 自动标记：到考为 NORMAL，未到考为 ABSENT；弃考（WAIVED）只能人工改型。
 */
@Component
public class EvidenceSupport {

    private final AttendanceRecordMapper attendanceMapper;
    private final DisciplineRecordMapper disciplineMapper;
    private final ExamScoreMapper scoreMapper;
    private final ObjectMapper objectMapper;

    public EvidenceSupport(AttendanceRecordMapper attendanceMapper,
                           DisciplineRecordMapper disciplineMapper,
                           ExamScoreMapper scoreMapper,
                           ObjectMapper objectMapper) {
        this.attendanceMapper = attendanceMapper;
        this.disciplineMapper = disciplineMapper;
        this.scoreMapper = scoreMapper;
        this.objectMapper = objectMapper;
    }

    /** 有效签到（到考依据）：现场已登记或补录已批准，取签到钟点最早的一条。 */
    public AttendanceRecord validAttendance(Long registrationId) {
        List<AttendanceRecord> records = attendanceMapper.selectList(new QueryWrapper<AttendanceRecord>()
                .eq("registration_id", registrationId)
                .in("status", AbsenceConstants.ATT_RECORDED, AbsenceConstants.ATT_APPROVED)
                .orderByAsc("check_in_at", "id"));
        return records.isEmpty() ? null : records.get(0);
    }

    public boolean isCheckedIn(Long registrationId) {
        return validAttendance(registrationId) != null;
    }

    /** 有效违纪（ACTIVE）。 */
    public List<DisciplineRecord> activeDisciplines(Long registrationId) {
        return disciplineMapper.selectList(new QueryWrapper<DisciplineRecord>()
                .eq("registration_id", registrationId)
                .eq("status", AbsenceConstants.DISC_ACTIVE)
                .orderByAsc("occurred_at", "id"));
    }

    /** 是否存在使本场成绩作废的有效违纪。 */
    public boolean hasScoreVoidDiscipline(Long registrationId) {
        for (DisciplineRecord d : activeDisciplines(registrationId)) {
            if (Boolean.TRUE.equals(d.getScoreVoid())) {
                return true;
            }
        }
        return false;
    }

    public ExamScore scoreOf(Long registrationId) {
        return scoreMapper.selectOne(new QueryWrapper<ExamScore>()
                .eq("registration_id", registrationId));
    }

    /** 自动缺考标记：未到考 ABSENT，到考 NORMAL；WAIVED 不在自动口径内。 */
    public String autoMark(Long registrationId) {
        return isCheckedIn(registrationId)
                ? AbsenceConstants.MARK_NORMAL : AbsenceConstants.MARK_ABSENT;
    }

    /** 建单时冻结的证据快照 JSON：签到、违纪、成绩三方口径一次写清。 */
    public String evidenceJson(Long registrationId) {
        try {
            AttendanceRecord att = validAttendance(registrationId);
            List<DisciplineRecord> discs = activeDisciplines(registrationId);
            ExamScore score = scoreOf(registrationId);

            Map<String, Object> map = new LinkedHashMap<>();
            Map<String, Object> attMap = new LinkedHashMap<>();
            attMap.put("checkedIn", att != null);
            attMap.put("source", att == null ? null : att.getSource());
            attMap.put("checkInAt", att == null ? null : att.getCheckInAt());
            attMap.put("roomNo", att == null ? null : att.getRoomNo());
            map.put("attendance", attMap);

            List<Map<String, Object>> discList = new ArrayList<>();
            boolean scoreVoid = false;
            for (DisciplineRecord d : discs) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("type", d.getDisciplineType());
                dm.put("scoreVoid", Boolean.TRUE.equals(d.getScoreVoid()));
                dm.put("occurredAt", d.getOccurredAt());
                dm.put("detail", d.getDetail());
                discList.add(dm);
                if (Boolean.TRUE.equals(d.getScoreVoid())) {
                    scoreVoid = true;
                }
            }
            map.put("disciplines", discList);
            map.put("scoreVoid", scoreVoid);

            Map<String, Object> scoreMap = new LinkedHashMap<>();
            scoreMap.put("recorded", score != null);
            scoreMap.put("paperScore", score == null ? null : score.getPaperScore());
            scoreMap.put("grade", score == null ? null : score.getGrade());
            scoreMap.put("ruleVersionNo", score == null ? null : score.getRuleVersionNo());
            map.put("score", scoreMap);
            map.put("autoMark", autoMark(registrationId));
            map.put("generatedAt", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"error\":\"证据快照生成失败：" + e.getMessage() + "\"}";
        }
    }
}
