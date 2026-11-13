package com.healthcare.controller;

import com.healthcare.dto.AppointmentReportRow;
import com.healthcare.dto.AuditLogResponse;
import com.healthcare.dto.BedOccupancyReportRow;
import com.healthcare.dto.DailyLoadReportRow;
import com.healthcare.service.AdminOperationException;
import com.healthcare.service.AllocationReportService;
import com.healthcare.service.AuditLogService;
import com.healthcare.service.CsvReportService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminReportController {

    private static final int MAX_REPORT_DAYS = 366;

    private final AllocationReportService reportService;
    private final CsvReportService csvReportService;
    private final AuditLogService auditLogService;

    public AdminReportController(
            AllocationReportService reportService,
            CsvReportService csvReportService,
            AuditLogService auditLogService) {
        this.reportService = reportService;
        this.csvReportService = csvReportService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/reports/daily-load")
    public ResponseEntity<?> dailyLoad(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "json") String format) {
        DateRange range = dateRange(date, startDate, endDate);
        List<DailyLoadReportRow> rows = reportService.dailyLoad(range.start(), range.end());
        return reportResponse(format, "daily-load.csv", csvReportService.dailyLoad(rows), rows);
    }

    @GetMapping("/reports/bed-occupancy")
    public ResponseEntity<?> bedOccupancy(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "json") String format) {
        DateRange range = dateRange(date, startDate, endDate);
        List<BedOccupancyReportRow> rows = reportService.bedOccupancy(range.start(), range.end());
        return reportResponse(format, "bed-occupancy.csv", csvReportService.bedOccupancy(rows), rows);
    }

    @GetMapping("/reports/appointments")
    public ResponseEntity<?> appointments(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "json") String format) {
        DateRange range = dateRange(date, startDate, endDate);
        List<AppointmentReportRow> rows = reportService.appointments(range.start(), range.end());
        return reportResponse(format, "appointments.csv", csvReportService.appointments(rows), rows);
    }

    @GetMapping("/audit-log")
    public Page<AuditLogResponse> auditLog(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AdminOperationException("page must be non-negative and size must be between 1 and 100.");
        }
        return auditLogService.search(user, action, date, page, size);
    }

    private DateRange dateRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        LocalDate start = date != null ? date : (startDate == null ? LocalDate.now() : startDate);
        LocalDate end = date != null ? date : (endDate == null ? start : endDate);
        if (start.isAfter(end)) {
            throw new AdminOperationException("startDate must not be after endDate.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_REPORT_DAYS) {
            throw new AdminOperationException("Report date range cannot exceed 366 days.");
        }
        return new DateRange(start, end);
    }

    private ResponseEntity<?> reportResponse(
            String format, String filename, String csv, List<?> rows) {
        if (!"csv".equalsIgnoreCase(format)) {
            if (!"json".equalsIgnoreCase(format)) {
                throw new AdminOperationException("format must be json or csv.");
            }
            return ResponseEntity.ok(rows);
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
