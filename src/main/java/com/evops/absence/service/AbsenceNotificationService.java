package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.entity.AbsenceNotification;
import com.evops.absence.mapper.AbsenceNotificationMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 缺考裁定站内通知：最终裁定结论 / 裁定变更都通知本人；本版落库，外发渠道下版再接。
 */
@Service
public class AbsenceNotificationService {

    private final AbsenceNotificationMapper notificationMapper;

    public AbsenceNotificationService(AbsenceNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public AbsenceNotification notify(Long batchId, Long registrationId, String regNo, String name,
                                      Long rulingId, String notifyType, String title, String content,
                                      String operator) {
        AbsenceNotification n = new AbsenceNotification();
        n.setBatchId(batchId);
        n.setRegistrationId(registrationId);
        n.setRegNo(regNo);
        n.setName(name);
        n.setRulingId(rulingId);
        n.setNotifyType(notifyType);
        n.setTitle(title);
        n.setContent(content);
        n.setStatus(AbsenceConstants.NOTIFY_NEW);
        n.setOperator(operator);
        n.setNotifiedAt(LocalDateTime.now());
        notificationMapper.insert(n);
        return n;
    }

    @Transactional(readOnly = true)
    public List<AbsenceNotification> list(String regNo, boolean unreadOnly) {
        QueryWrapper<AbsenceNotification> wrapper = new QueryWrapper<AbsenceNotification>()
                .orderByDesc("notified_at", "id");
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        if (unreadOnly) {
            wrapper.eq("status", AbsenceConstants.NOTIFY_NEW);
        }
        return notificationMapper.selectList(wrapper);
    }

    @Transactional
    public AbsenceNotification markRead(Long id) {
        AbsenceNotification n = notificationMapper.selectById(id);
        if (n == null) {
            throw new BusinessException("ABSENCE_NOTIFICATION_NOT_FOUND", "通知不存在：id=" + id);
        }
        if (AbsenceConstants.NOTIFY_NEW.equals(n.getStatus())) {
            n.setStatus(AbsenceConstants.NOTIFY_READ);
            n.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
        return n;
    }
}
