package com.personal.jiralog.controller;

import com.personal.jiralog.client.JiraClient;
import com.personal.jiralog.model.JiraIssue;
import com.personal.jiralog.model.Worklog;
import com.personal.jiralog.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private JiraClient jiraClient;

    @Autowired
    private ReportService reportService;

    @Autowired
    private com.personal.jiralog.service.ExcelProcessingService excelProcessingService;

    @Value("${jira.target-user-email}")
    private String targetUserEmail;

    @Value("${jira.subtask.summary:Pruebas tempranas}")
    private String subtaskSummary;

    @Value("${jira.subtask.time-estimate:8h}")
    private String subtaskTimeEstimate;

    @Value("${jira.subtask.start-date:}")
    private String subtaskStartDate;

    @Value("${jira.subtask.target-date:}")
    private String subtaskTargetDate;

    @Value("${jira.subtask.authorizer-id:619549bc3618cd006f294572}")
    private String subtaskAuthorizerId;

    @Value("${jira.subtask.activity-id:12101}")
    private String subtaskActivityId;

    @PostMapping("/subtask/{parentKey}")
    public ResponseEntity<Map<String, Object>> createSubtask(
            @PathVariable String parentKey,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String targetDate) {
        
        String finalStartDate = (startDate != null && !startDate.isEmpty()) ? startDate : 
                               (subtaskStartDate.isEmpty() ? LocalDate.now().toString() : subtaskStartDate);
        
        log.info("Petición individual de creación de subtarea: '{}' para el día: {}", subtaskSummary, finalStartDate);
        
        // Validar si ya existe para evitar duplicados
        List<Map<String, Object>> existing = jiraClient.findIssuesBySummaryAndDate(subtaskSummary, finalStartDate, targetUserEmail, parentKey);
        if (!existing.isEmpty()) {
            Map<String, Object> first = existing.get(0);
            String key = first.get("key") != null ? first.get("key").toString() : "desconocida";
            log.warn("La tarea ya existe en Jira ({}). Abortando creación individual.", key);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "La tarea ya existe en Jira");
            response.put("key", key);
            response.put("status", "ALREADY_EXISTS");
            return ResponseEntity.ok(response);
        }

        Map<String, Object> result = jiraClient.createSubtask(
                parentKey,
                subtaskSummary,
                subtaskTimeEstimate,
                finalStartDate,
                (targetDate != null && !targetDate.isEmpty()) ? targetDate : (subtaskTargetDate.isEmpty() ? null : subtaskTargetDate),
                subtaskAuthorizerId,
                subtaskActivityId
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/subtasks/{parentKey}")
    public ResponseEntity<byte[]> getSubtaskAnalysis(@PathVariable String parentKey) {
        log.info("Petición de análisis de subtareas para: {}", parentKey);
        
        JiraIssue parent = jiraClient.getIssue(parentKey);
        if (parent == null) {
            log.error("No se encontró la tarea padre: {}", parentKey);
            return ResponseEntity.notFound().build();
        }

        List<JiraIssue> subtasks = jiraClient.getSubtasks(parentKey);
        log.info("Se encontraron {} subtareas para {}", subtasks.size(), parentKey);
        
        byte[] pdfBytes = reportService.generateSubtaskReport(parent, subtasks);

        if (pdfBytes == null) {
            return ResponseEntity.internalServerError().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "analisis-subtareas-" + parentKey + ".pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        String userEmail = targetUserEmail != null ? targetUserEmail.trim() : "";
        if (startDate == null) {
            // Por defecto, última semana
            startDate = LocalDate.now().minusWeeks(1);
        }
        
        if (endDate == null) {
            // Por defecto, hoy
            endDate = LocalDate.now();
        }

        long sinceMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<Worklog> worklogs = jiraClient.getUserWorklogs(userEmail, sinceMillis);

        byte[] pdfBytes = reportService.generatePdfReport(worklogs, userEmail, startDate, endDate);

        if (pdfBytes == null) {
            return ResponseEntity.internalServerError().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "reporte-horas.pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<JiraIssue>> getAssignedTasks() {
        List<JiraIssue> issues = jiraClient.getAssignedIssues(targetUserEmail);
        return ResponseEntity.ok(issues);
    }

    @GetMapping("/stats/month")
    public ResponseEntity<Map<String, Object>> getMonthStats() {
        String userEmail = targetUserEmail != null ? targetUserEmail.trim() : "";
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        long sinceMillis = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        List<Worklog> worklogs = jiraClient.getUserWorklogs(userEmail, sinceMillis);
        
        long totalSeconds = 0;
        for (Worklog wl : worklogs) {
            // Filtrar por si acaso Jira devuelve algo fuera del rango, aunque el JQL debería bastar
            if (wl.getStarted() != null) {
                totalSeconds += wl.getTimeSpentSeconds();
            }
        }

        double totalHours = totalSeconds / 3600.0;
        
        // Calcular días laborales (Lunes a Viernes) desde el inicio del mes hasta hoy inclusive
        int workingDays = 0;
        for (LocalDate date = startOfMonth; !date.isAfter(now); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }

        int expectedHours = workingDays * 9;
        double percentage = expectedHours > 0 ? (totalHours / expectedHours) * 100 : 0;

        String userName = jiraClient.getUserDisplayName(userEmail);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalHours", totalHours);
        stats.put("expectedHours", (double)expectedHours);
        stats.put("percentage", percentage);
        stats.put("workingDays", workingDays);
        stats.put("userName", userName != null ? userName : userEmail);
        
        // Formatear el mes a español
        String monthSpanish = now.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("es", "ES"));
        // Capitalizar la primera letra
        monthSpanish = monthSpanish.substring(0, 1).toUpperCase() + monthSpanish.substring(1).toLowerCase();
        
        stats.put("month", monthSpanish);
        stats.put("year", now.getYear());

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestActivities(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        log.info("Recibida petición de ingesta masiva. Archivo: {}, Tamaño: {} bytes", file.getOriginalFilename(), file.getSize());
        try {
            byte[] resultExcel = excelProcessingService.processActivitiesExcel(file.getInputStream());
            if (resultExcel == null) {
                log.error("Error durante el procesamiento del Excel de ingesta.");
                return ResponseEntity.internalServerError().body("Error durante el procesamiento del Excel.");
            }

            log.info("Ingesta masiva finalizada con éxito. Enviando archivo procesado.");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "registro_actividades_procesado.xlsx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resultExcel.length)
                    .body(resultExcel);
        } catch (Exception e) {
            log.error("Excepción al procesar la ingesta masiva: {}", e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error en la ingesta masiva: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
