package com.evops.absence.controller;

import com.evops.absence.dto.RulingFinalReviewRequest;
import com.evops.absence.dto.RulingFirstReviewRequest;
import com.evops.absence.dto.RulingGenerateRequest;
import com.evops.absence.dto.RulingReclassifyRequest;
import com.evops.absence.entity.AbsenceRuling;
import com.evops.absence.entity.AbsenceRulingTrace;
import com.evops.absence.entity.AbsenceRulingVersion;
import com.evops.absence.service.RulingService;
import com.evops.absence.vo.PassListItem;
import com.evops.absence.vo.RulingGenerateResult;
import com.evops.absence.vo.RulingStats;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 缺考裁定（模块 / 版本 / 状态）：
 * 自动标缺考 → 初审 → 终审（双人，落最终结论、成绩快照、通知本人）；
 * 缺考弃考改型；合格名单与统计只认最终裁定；版本链与过程留痕可查。
 */
@RestController
@RequestMapping("/api/absence/rulings")
public class RulingController {

    private final RulingService rulingService;

    public RulingController(RulingService rulingService) {
        this.rulingService = rulingService;
    }

    // ---------- 自动标缺考（两步预检） ----------

    /** 第一步：预检影响范围（自动标记、签到/违纪依据、可建单/跳过逐条列出），不落库。 */
    @PostMapping("/generate/preview")
    public ApiResponse<RulingGenerateResult> generatePreview(@Valid @RequestBody RulingGenerateRequest request) {
        return ApiResponse.ok(rulingService.previewGenerate(request));
    }

    /** 第二步：确认执行——按签到与违纪记录自动建单（PENDING 待初审）。 */
    @PostMapping("/generate")
    public ApiResponse<RulingGenerateResult> generate(@Valid @RequestBody RulingGenerateRequest request) {
        return ApiResponse.ok(rulingService.generate(request));
    }

    // ---------- 双人复核 ----------

    /** 初审（第一人）：通过送终审；驳回写意见，裁定终止（可改型/rebuild 重开）。 */
    @PostMapping("/{id}/first-review")
    public ApiResponse<AbsenceRuling> firstReview(@PathVariable Long id,
                                                  @Valid @RequestBody RulingFirstReviewRequest request) {
        return ApiResponse.ok(rulingService.firstReview(id, request));
    }

    /** 终审（第二人，必须与初审人不同）：决定成绩是否有效、无效写原因与下次安排、通知本人。 */
    @PostMapping("/{id}/final-review")
    public ApiResponse<AbsenceRuling> finalReview(@PathVariable Long id,
                                                  @Valid @RequestBody RulingFinalReviewRequest request) {
        return ApiResponse.ok(rulingService.finalReview(id, request));
    }

    /** 缺考/弃考改型（分清类型；已生效裁定改型自动重开走新版本）。 */
    @PostMapping("/{id}/reclassify")
    public ApiResponse<AbsenceRuling> reclassify(@PathVariable Long id,
                                                 @Valid @RequestBody RulingReclassifyRequest request) {
        return ApiResponse.ok(rulingService.reclassify(id, request));
    }

    // ---------- 合格名单 / 统计（只认最终裁定；变更即重算） ----------

    /** 合格名单：最终裁定成绩有效且等级合格。 */
    @GetMapping("/pass-list")
    public ApiResponse<List<PassListItem>> passList(@RequestParam Long batchId) {
        return ApiResponse.ok(rulingService.passList(batchId));
    }

    /** 批次统计：正常/缺考/弃考、有效/无效、合格、无效原因与下次安排分布（只认最终裁定）。 */
    @GetMapping("/stats")
    public ApiResponse<RulingStats> stats(@RequestParam Long batchId) {
        return ApiResponse.ok(rulingService.stats(batchId));
    }

    /** 重算统计：裁定/依据变更后按最新最终裁定重算，清脏标记、留痕。 */
    @PostMapping("/stats/recalculate")
    public ApiResponse<RulingStats> recalculate(@RequestParam Long batchId) {
        return ApiResponse.ok(rulingService.recalculate(batchId));
    }

    // ---------- 查询 / 版本链 / 过程留痕 ----------

    @GetMapping
    public ApiResponse<List<AbsenceRuling>> list(@RequestParam(required = false) Long batchId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String absenceType,
                                                 @RequestParam(required = false) String scoreValidity) {
        return ApiResponse.ok(rulingService.list(batchId, status, absenceType, scoreValidity));
    }

    @GetMapping("/{id}")
    public ApiResponse<AbsenceRuling> get(@PathVariable Long id) {
        return ApiResponse.ok(rulingService.get(id));
    }

    /** 缺考标记版本链（建单/初审/终审/驳回/重裁，只追加）。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<AbsenceRulingVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(rulingService.versions(id));
    }

    /** 过程留痕（证据变化、补录审批、违纪变化、改型、通知、重算）。 */
    @GetMapping("/{id}/traces")
    public ApiResponse<List<AbsenceRulingTrace>> traces(@PathVariable Long id) {
        return ApiResponse.ok(rulingService.traces(id));
    }
}
