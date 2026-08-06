package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.security.StaffSecurityRoles;
import com.benchreadiness.interview.service.ManagementReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/analytics/report")
@PreAuthorize("hasAnyRole('" + StaffSecurityRoles.ADMIN + "')")
public class ManagementReportController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ManagementReportController.class);

    private final ManagementReportService reportService;

    public ManagementReportController(ManagementReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Download a professional PDF management report.
     *
     * Query params:
     *   from  — inclusive start date (yyyy-MM-dd), default: 30 days ago
     *   to    — inclusive end date   (yyyy-MM-dd), default: today
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("X-User-Id") String userId) {

        if (from == null) from = LocalDate.now().minusDays(30);
        if (to   == null) to   = LocalDate.now();
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] pdf = reportService.generateReport(from, to);
            String filename = "BenchReadiness_Report_"
                    + from.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "_to_"
                    + to.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + ".pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header("X-Report-From", from.toString())
                    .header("X-Report-To",   to.toString())
                    .body(pdf);
        } catch (Exception e) {
            log.error("Report generation failed for userId={}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
