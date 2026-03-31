package com.personal.jiralog.service;

import com.personal.jiralog.client.JiraClient;
import com.personal.jiralog.model.ExcelActivity;
import com.personal.jiralog.model.JiraIssue;
import com.personal.jiralog.utils.ActivityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ExcelProcessingService.class);

    @Autowired
    private JiraClient jiraClient;

    @Value("${jira.subtask.authorizer-id}")
    private String authorizerId;

    @Value("${jira.subtask.activity-id}")
    private String defaultActivityId;

    @Value("${jira.target-user-email}")
    private String targetUserEmail;

    public byte[] processActivitiesExcel(InputStream inputStream) {
        log.info("Iniciando procesamiento de Excel para ingesta masiva");
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ExcelActivity> activities = parseExcel(sheet);
            log.info("Se encontraron {} actividades para procesar en el Excel", activities.size());
            
            int createdCount = 0;
            int alreadyExistsCount = 0;
            
            // Registro local para evitar duplicados por retraso de indexación en Jira
            // Clave: summary + "|" + date + "|" + parentKey
            Map<String, String> localCache = new HashMap<>();

            for (ExcelActivity activity : activities) {
                if (activity.getSummary() == null || activity.getSummary().trim().isEmpty()) {
                    log.warn("Fila {} tiene un resumen vacío o nulo. Saltando.", activity.getRowIndex());
                    continue;
                }
                
                String currentSummary = activity.getSummary().trim();
                String currentDate = (activity.getStartDate() != null ? activity.getStartDate().toString() : java.time.LocalDate.now().toString());
                String parentKey = (activity.getParentKey() != null ? activity.getParentKey().trim().toUpperCase() : "no-parent");
                String cacheKey = currentSummary.toLowerCase() + "|" + currentDate + "|" + parentKey;

                log.info("Procesando actividad: '{}' (Fila {})", currentSummary, activity.getRowIndex());
                
                String issueKey = null;
                
                // Primero revisamos el cache local de esta ejecución
                if (localCache.containsKey(cacheKey)) {
                    issueKey = localCache.get(cacheKey);
                    log.info("La tarea '{}' bajo el padre '{}' ya fue procesada en esta sesión (Clave: {}). Saltando creación.", 
                            currentSummary, parentKey, issueKey);
                    alreadyExistsCount++;
                } else {
                    // 1. Validar si ya existe una tarea en Jira (vía API)
                    String dateForSearch = (activity.getStartDate() != null ? activity.getStartDate().toString() : java.time.LocalDate.now().toString());
                    List<Map<String, Object>> existing = jiraClient.findIssuesBySummaryAndDate(currentSummary, 
                            dateForSearch, 
                            targetUserEmail,
                            activity.getParentKey());
                    
                    if (existing.isEmpty()) {
                        // 2. Crear subtarea
                        log.info("No se encontró tarea previa en Jira. Creando subtarea para: {}", currentSummary);
                        
                        String activityId = ActivityUtils.getActivityIdByName(activity.getActivityName());
                        if (activityId == null) {
                            log.warn("No se pudo obtener el ID para la actividad: '{}'. Usando ID por defecto: {}", activity.getActivityName(), defaultActivityId);
                            activityId = defaultActivityId;
                        }
                        
                        String startDateStr = (activity.getStartDate() != null) ? activity.getStartDate().toString() : null;
                        String targetDateStr = (activity.getTargetDate() != null) ? activity.getTargetDate().toString() : null;
                        
                        String estimate = activity.getEstimate();
                        if (estimate != null && !estimate.isEmpty() && estimate.matches("\\d+(\\.\\d+)?")) {
                            log.debug("La estimación '{}' es numérica, añadiendo 'h' por defecto.", estimate);
                            estimate += "h";
                        }

                        Map<String, Object> result = jiraClient.createSubtask(
                                  activity.getParentKey(),
                               currentSummary,
                               estimate,
                               startDateStr,
                               targetDateStr,
                               authorizerId,
                               activityId
                        );
                        
                        if (result.containsKey("error")) {
                            String errorMsg = (String) result.get("error");
                            log.error("Fallo crítico en Jira para la actividad '{}': {}. Deteniendo ingesta.", currentSummary, errorMsg);
                            throw new RuntimeException("Error en Jira: " + errorMsg);
                        }

                        issueKey = result.get("key") != null ? result.get("key").toString() : null;
                        if (issueKey != null) {
                            log.info("Subtarea creada exitosamente con clave: {}", issueKey);
                            createdCount++;
                            localCache.put(cacheKey, issueKey);
                        } else {
                            log.error("Error al crear subtarea para: {}. El resultado de Jira no trajo 'key'.", currentSummary);
                        }
                    } else {
                        Map<String, Object> firstExisting = existing.get(0);
                        issueKey = firstExisting.get("key") != null ? firstExisting.get("key").toString() : null;
                        log.info("La tarea '{}' ya existe en Jira con clave: {}. Saltando creación.", currentSummary, issueKey);
                        alreadyExistsCount++;
                        if (issueKey != null) {
                            localCache.put(cacheKey, issueKey);
                        }
                    }
                }
                
                activity.setCreatedKey(issueKey);

                if (issueKey != null) {
                    // 3. Transiciones de estado
                    log.debug("Verificando estado y worklogs para issue: {}", issueKey);
                    processStatusAndWorklog(issueKey, activity);
                }
            }
            
            log.info("Procesamiento completado. Creadas: {}, Ya existentes: {}", createdCount, alreadyExistsCount);
            // 4. Actualizar Excel con la columna de Clave Ticket
            updateExcelWithKeys(sheet, activities);
            
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
            
        } catch (Exception e) {
            log.error("Error al procesar Excel: {}", e.getMessage(), e);
            throw (e instanceof RuntimeException) ? (RuntimeException)e : new RuntimeException("Error procesando Excel", e);
        }
    }

    private List<ExcelActivity> parseExcel(Sheet sheet) {
        List<ExcelActivity> activities = new ArrayList<>();
        // Formato Real del Excel: 
        // Col 0: tipo de actividad (Activity Name)
        // Col 1: nombre de actividad (Summary)
        // Col 2: duracion (Estimate)
        // Col 3: target date (StartDate/TargetDate)
        // Col 4: tarea padre (Parent Key)
        // Col 5: Estado (Status)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            String parentKey = getCellValue(row.getCell(4)); // Col 4: tarea padre
            if (parentKey == null || parentKey.trim().isEmpty()) {
                log.debug("Saltando fila {} por falta de Parent Key", i);
                continue;
            }

            ExcelActivity activity = new ExcelActivity();
            activity.setRowIndex(i);
            activity.setParentKey(parentKey.trim());
            activity.setSummary(getCellValue(row.getCell(1))); // Col 1: nombre de actividad
            activity.setActivityName(getCellValue(row.getCell(0))); // Col 0: tipo de actividad
            activity.setEstimate(getCellValue(row.getCell(2))); // Col 2: duracion
            activity.setStartDate(convertDate(row.getCell(3))); // Col 3: target date
            activity.setTargetDate(convertDate(row.getCell(3))); // Col 3: target date
            activity.setStatus(getCellValue(row.getCell(5))); // Col 5: Estado
            
            activities.add(activity);
        }
        return activities;
    }

    private void processStatusAndWorklog(String issueKey, ExcelActivity activity) {
        String targetStatus = activity.getStatus();
        if (targetStatus == null || targetStatus.isEmpty()) {
            log.debug("No se especificó estado objetivo para el issue {}", issueKey);
            return;
        }

        // Obtener detalle completo para ver estado y horas registradas
        JiraIssue issue = jiraClient.getIssue(issueKey);
        if (issue == null || issue.getFields() == null) {
            log.warn("No se pudo obtener el detalle de la incidencia {}", issueKey);
            return;
        }

        String currentStatus = issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : "";
        log.info("Estado actual de {}: {}. Estado objetivo: {}", issueKey, currentStatus, targetStatus);

        boolean alreadyFinished = isFinishedStatus(currentStatus);
        boolean needsTransition = !currentStatus.equalsIgnoreCase(targetStatus) && !alreadyFinished;
        
        if (needsTransition) {
            log.info("Intentando mover issue {} al estado: {}", issueKey, targetStatus);
            // Obtener transiciones actuales
            List<Map<String, Object>> transitions = jiraClient.getTransitions(issueKey);
            
            // Lógica simplificada: Buscar transición que lleve al estado deseado
            boolean reached = false;
            int attempts = 0;
            while (!reached && attempts < 3) {
                Map<String, Object> targetTransition = transitions.stream()
                    .filter(t -> ((Map<String, Object>)t.get("to")).get("name").toString().equalsIgnoreCase(targetStatus))
                    .findFirst()
                    .orElse(null);

                if (targetTransition != null) {
                    log.info("Ejecutando transición directa a {} para el issue {}", targetStatus, issueKey);
                    jiraClient.doTransition(issueKey, targetTransition.get("id").toString());
                    reached = true;
                } else {
                    // Si no es directo, intentamos mover a "En Curso" primero si está disponible
                    Map<String, Object> progressTransition = transitions.stream()
                        .filter(t -> ((Map<String, Object>)t.get("to")).get("name").toString().equalsIgnoreCase("En Curso") || 
                                     ((Map<String, Object>)t.get("to")).get("name").toString().equalsIgnoreCase("In Progress"))
                        .findFirst()
                        .orElse(null);
                    
                    if (progressTransition != null) {
                        log.info("Moviendo issue {} a estado intermedio 'En Curso'", issueKey);
                        jiraClient.doTransition(issueKey, progressTransition.get("id").toString());
                        transitions = jiraClient.getTransitions(issueKey);
                    } else {
                        log.warn("No se encontró transición directa a {} ni estado intermedio disponible para el issue {}", targetStatus, issueKey);
                        break;
                    }
                }
                attempts++;
            }
        } else {
            if (alreadyFinished && !currentStatus.equalsIgnoreCase(targetStatus)) {
                log.info("El issue {} ya se encuentra finalizado en Jira ({}). No se intentará cambiar su estado a pesar de que el Excel indica {}.", issueKey, currentStatus, targetStatus);
            } else {
                log.info("El issue {} ya se encuentra en el estado deseado: {}.", issueKey, targetStatus);
            }
        }

        // Si el estado final es "Finalizado" (o similar), registrar worklog si aún tiene horas disponibles
        String finalStatus = jiraClient.getIssueStatus(issueKey);
        if (isFinishedStatus(finalStatus)) {
            // REVISAR SI YA TIENE HORAS DISPONIBLES (Remaining Estimate > 0)
            JiraIssue updatedIssue = jiraClient.getIssue(issueKey);
            int remainingSeconds = 0;
            if (updatedIssue != null && updatedIssue.getFields() != null && updatedIssue.getFields().getTimetracking() != null) {
                remainingSeconds = updatedIssue.getFields().getTimetracking().getRemainingEstimateSeconds();
            }

            if (remainingSeconds > 0) {
                log.info("Estado final alcanzado para {} y tiene {}s disponibles. Registrando worklog de {} por actividad concluida.", 
                        issueKey, remainingSeconds, activity.getEstimate());
                
                String estimate = activity.getEstimate();
                if (estimate != null && !estimate.isEmpty() && estimate.matches("\\d+(\\.\\d+)?")) {
                    estimate += "h";
                }

                // Validar que la fecha no sea nula para el worklog
                String worklogDate = (activity.getStartDate() != null) ? activity.getStartDate().toString() : java.time.LocalDate.now().toString();
                
                jiraClient.addWorklog(issueKey, estimate, "Carga masiva desde Excel - Actividad concluida", worklogDate);
            } else {
                log.info("El issue {} está en estado finalizado pero no tiene horas disponibles (Remaining: {}). Saltando registro de worklog.", issueKey, remainingSeconds);
            }
        } else if (isFinishedStatus(targetStatus) || alreadyFinished) {
            log.warn("No se pudo alcanzar un estado finalizado para {}, saltando registro de worklog.", issueKey);
        }
    }

    private boolean isFinishedStatus(String status) {
        if (status == null) return false;
        return "Finalizada".equalsIgnoreCase(status) || 
               "Done".equalsIgnoreCase(status) || 
               "Cerrada".equalsIgnoreCase(status) || 
               "Terminada".equalsIgnoreCase(status) ||
               "Resolved".equalsIgnoreCase(status);
    }

    private void updateExcelWithKeys(Sheet sheet, List<ExcelActivity> activities) {
        Row header = sheet.getRow(0);
        if (header == null) return;
        
        int keyColIndex = -1;
        for (int i = 0; i < header.getLastCellNum(); i++) {
            if ("Clave Ticket".equalsIgnoreCase(getCellValue(header.getCell(i)))) {
                keyColIndex = i;
                break;
            }
        }
        
        if (keyColIndex == -1) {
            keyColIndex = header.getLastCellNum();
            header.createCell(keyColIndex).setCellValue("Clave Ticket");
        }
        
        for (ExcelActivity activity : activities) {
            Row row = sheet.getRow(activity.getRowIndex());
            if (row != null) {
                row.createCell(keyColIndex).setCellValue(activity.getCreatedKey());
            }
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: 
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    return String.valueOf((long) numericValue);
                } else {
                    return String.valueOf(numericValue);
                }
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default: return "";
        }
    }

    private LocalDate convertDate(Cell cell) {
        if (cell == null) return LocalDate.now();
        if (cell.getCellType() == CellType.NUMERIC) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return LocalDate.parse(cell.getStringCellValue());
            } catch (Exception e) {
                return LocalDate.now();
            }
        }
        return LocalDate.now();
    }
}
