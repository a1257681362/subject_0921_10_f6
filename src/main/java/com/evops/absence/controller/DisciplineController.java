package com.evops.absence.controller;

import com.evops.absence.dto.DisciplineRequest;
import com.evops.absence.dto.DisciplineRevokeRequest;
import com.evops.absence.entity.DisciplineRecord;
import com.evops.absence.service.DisciplineService;
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
 * 考场违纪记录：登记（可标记本场成绩作废）/ 撤销留痕；作废违纪联动缺考裁定。
 */
@RestController
@RequestMapping("/api/absence/disciplines")
public class DisciplineController {

    private final DisciplineService disciplineService;

    public DisciplineController(DisciplineService disciplineService) {
        this.disciplineService = disciplineService;
    }

    @PostMapping
    public ApiResponse<DisciplineRecord> record(@Valid @RequestBody DisciplineRequest request) {
        return ApiResponse.ok(disciplineService.record(request));
    }

    /** 撤销违纪（记录保留，不再作为裁定依据）。 */
    @PostMapping("/{id}/revoke")
    public ApiResponse<DisciplineRecord> revoke(@PathVariable Long id,
                                                @Valid @RequestBody DisciplineRevokeRequest request) {
        return ApiResponse.ok(disciplineService.revoke(id, request));
    }

    @GetMapping
    public ApiResponse<List<DisciplineRecord>> list(@RequestParam Long batchId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(disciplineService.listByBatch(batchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<DisciplineRecord> get(@PathVariable Long id) {
        return ApiResponse.ok(disciplineService.get(id));
    }
}
