package com.personal.jiralog.service;

import com.personal.jiralog.model.JiraIssue;
import com.personal.jiralog.model.Worklog;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    public void generatePdfReport(List<Worklog> worklogs, String outputPath, String userEmail, LocalDate startDate, LocalDate endDate) {
        byte[] pdfContent = generatePdfReport(worklogs, userEmail, startDate, endDate);
        if (pdfContent != null) {
            try (OutputStream os = new FileOutputStream(outputPath)) {
                os.write(pdfContent);
                System.out.println("Reporte generado exitosamente en: " + outputPath);
            } catch (Exception e) {
                System.err.println("Error al guardar el PDF: " + e.getMessage());
            }
        }
    }

    public byte[] generatePdfReport(List<Worklog> worklogs, String userEmail, LocalDate startDate, LocalDate endDate) {
        if (worklogs == null) return null;
        
        final String targetEmail = userEmail != null ? userEmail.trim() : "";
        System.out.println("[DEBUG_LOG] Generando PDF para " + targetEmail + " en rango " + startDate + " a " + endDate);
        System.out.println("[DEBUG_LOG] Total worklogs recibidos: " + worklogs.size());

        // Agrupar por la fecha de la tarea (startDate) calculada en JiraClient
        Map<LocalDate, List<Worklog>> worklogsPerDay = worklogs.stream()
                .peek(w -> {
                    // Log de depuración para cada worklog
                    String authStr = w.getAuthor() != null ? w.getAuthor().getEmailAddress() : "null";
                    System.out.println("[DEBUG_LOG] Procesando worklog: Issue=" + w.getIssueKey() + 
                                       ", Author=" + authStr + ", Started=" + w.getStarted() + 
                                       ", CalculatedDate=" + w.getStartDate());
                })
                .collect(Collectors.groupingBy(
                        w -> LocalDate.parse(w.getStartDate())
                ));

        // Filtrar worklogs que caen dentro del rango solicitado
        Map<LocalDate, List<Worklog>> filteredWorklogsPerDay = worklogsPerDay.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(startDate) && !entry.getKey().isAfter(endDate))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
                .append("body { font-family: sans-serif; font-size: 12px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }")
                .append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
                .append("th { background-color: #f2f2f2; }")
                .append(".error { color: red; font-weight: bold; }")
                .append(".note { font-size: 0.9em; color: #666; margin-bottom: 20px; }")
                .append(".tasks-list { font-size: 0.85em; color: #333; margin: 0; padding-left: 20px; }")
                .append(".dup-1 { background-color: #e3f2fd; }") // Light blue
                .append(".dup-2 { background-color: #f1f8e9; }") // Light green
                .append(".dup-3 { background-color: #fff3e0; }") // Light orange
                .append(".dup-4 { background-color: #f3e5f5; }") // Light purple
                .append(".dup-5 { background-color: #fbe9e7; }") // Light red
                .append(".dup-6 { background-color: #e0f2f1; }") // Light teal
                .append(".dup-7 { background-color: #fffde7; }") // Light yellow
                .append("</style></head><body>")
                .append("<h1>Reporte de Horas Jira</h1>")
                .append("<p>Usuario: ").append(userEmail).append("</p>")
                .append("<p>Rango: ").append(startDate).append(" al ").append(endDate).append("</p>")
                .append("<p class='note'>* Las horas se asocian a la Fecha de la Tarea (Target Date / Start Date).</p>")
                .append("<table><tr><th>Fecha</th><th>Actividades (Jira)</th><th>Total Horas</th><th>Estado</th><th>Sugerencia de Capacitación</th></tr>");

        // Rango desde startDate hasta endDate
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek().getValue() >= 1 && current.getDayOfWeek().getValue() <= 5) {
                List<Worklog> dayWorklogs = filteredWorklogsPerDay.getOrDefault(current, List.of());
                double jiraHours = dayWorklogs.stream().mapToDouble(w -> w.getTimeSpentSeconds() / 3600.0).sum();
                double totalHours = jiraHours;
                
                html.append("<tr>")
                        .append("<td>").append(current).append("<br/>(").append(current.getDayOfWeek()).append(")</td>")
                        .append("<td>");
                
                if (dayWorklogs.isEmpty()) {
                    html.append("<i>Sin actividades registradas</i>");
                } else {
                    // Ordenar actividades por resumen (summary) para que las duplicadas aparezcan juntas verticalmente
                    List<Worklog> sortedDayWorklogs = dayWorklogs.stream()
                            .sorted(java.util.Comparator.comparing((Worklog w) -> w.getIssueSummary() != null ? w.getIssueSummary() : "")
                                    .thenComparing(w -> w.getIssueKey() != null ? w.getIssueKey() : ""))
                            .collect(Collectors.toList());

                    // Contar cuántas claves de tarea distintas hay por cada descripción para detectar duplicados
                    Map<String, java.util.Set<String>> summaryToKeys = sortedDayWorklogs.stream()
                            .collect(Collectors.groupingBy(
                                    w -> w.getIssueSummary() != null ? w.getIssueSummary() : "UNKNOWN",
                                    Collectors.mapping(w -> w.getIssueKey() != null ? w.getIssueKey() : "UNKNOWN", Collectors.toSet())
                            ));
                    
                    // Asignar colores a los grupos de duplicados (mismo resumen, diferente clave)
                    Map<String, String> summaryToColorClass = new java.util.HashMap<>();
                    int colorCounter = 1;
                    for (Map.Entry<String, java.util.Set<String>> entry : summaryToKeys.entrySet()) {
                        if (entry.getValue().size() > 1) {
                            summaryToColorClass.put(entry.getKey(), "dup-" + colorCounter);
                            colorCounter = (colorCounter % 7) + 1;
                        }
                    }

                    html.append("<ul class='tasks-list'>");
                    for (Worklog wl : sortedDayWorklogs) {
                        String summary = wl.getIssueSummary() != null ? wl.getIssueSummary() : "UNKNOWN";
                        String issueKey = wl.getIssueKey() != null ? wl.getIssueKey() : "UNKNOWN";
                        boolean isDuplicate = wl.getIssueSummary() != null && summaryToKeys.getOrDefault(summary, java.util.Collections.emptySet()).size() > 1;
                        String colorClass = summaryToColorClass.getOrDefault(summary, "");
                        double wlHours = wl.getTimeSpentSeconds() / 3600.0;
                        
                        html.append("<li").append(!colorClass.isEmpty() ? " class='" + colorClass + "'" : "").append(">")
                            .append("<b").append(isDuplicate ? " class='error'" : "").append(">")
                            .append(issueKey).append("</b>: ")
                            .append("<b>").append(summary).append("</b>")
                            .append(" (").append(String.format("%.2f", wlHours)).append(" hrs)")
                            .append("</li>");
                    }
                    html.append("</ul>");
                }
                
                html.append("</td>")
                        .append("<td>").append(String.format("%.2f", totalHours)).append(" hrs</td>");

                if (totalHours < 9.0) {
                    double missing = 9.0 - totalHours;
                    html.append("<td class='error'>Faltan ").append(String.format("%.2f", missing)).append(" hrs</td>")
                            .append("<td><b>Sugerencia:</b> Crear tarea de capacitación de ").append(String.format("%.2f", missing)).append(" hrs</td>");
                } else {
                    html.append("<td>Cumplido</td>")
                            .append("<td>-</td>");
                }
                html.append("</tr>");
            }
            current = current.plusDays(1);
        }

        html.append("</table></body></html>");

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html.toString(), null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            System.err.println("Error al generar el PDF: " + e.getMessage());
            return null;
        }
    }

    public byte[] generateSubtaskReport(JiraIssue parent, List<JiraIssue> subtasks) {
        if (parent == null) return null;

        double parentEstimateHours = (parent.getFields() != null && parent.getFields().getTimetracking() != null) ? 
                parent.getFields().getTimetracking().getOriginalEstimateSeconds() / 3600.0 : 0.0;
        
        double subtasksTotalHours = subtasks.stream()
                .filter(s -> s.getFields() != null && s.getFields().getTimetracking() != null)
                .mapToDouble(s -> s.getFields().getTimetracking().getOriginalEstimateSeconds() / 3600.0)
                .sum();

        double parentEstimateDays = parentEstimateHours / 9.0;
        double subtasksTotalDays = subtasksTotalHours / 9.0;

        boolean isOverEstimated = subtasksTotalHours > parentEstimateHours;

        // Agrupar por fecha
        Map<String, List<JiraIssue>> groupedByDate = subtasks.stream()
                .collect(Collectors.groupingBy(s -> {
                    if (s.getFields() == null) return "SIN DATOS";
                    String date = s.getFields().getCustomfield_10715();
                    if (date == null || date.isEmpty() || date.equals("null")) date = s.getFields().getCustomfield_10015();
                    if (date == null || date.isEmpty() || date.equals("null")) date = s.getFields().getStartDate();
                    if (date == null || date.isEmpty() || date.equals("null")) date = s.getFields().getStartdate();
                    if (date == null || date.isEmpty() || date.equals("null")) date = s.getFields().getDuedate();
                    return (date != null && date.length() >= 10) ? date.substring(0, 10) : "SIN FECHA";
                }));

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
                .append("body { font-family: sans-serif; font-size: 12px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }")
                .append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
                .append("th { background-color: #f2f2f2; }")
                .append(".error { color: red; font-weight: bold; }")
                .append(".header { background-color: #eee; padding: 10px; margin-bottom: 20px; border-radius: 5px; }")
                .append("h1, h3 { color: #333; }")
                .append("</style></head><body>")
                .append("<h1>Análisis de Subtareas: ").append(parent.getKey()).append("</h1>")
                .append("<div class='header'>")
                .append("<b>Resumen:</b> ").append(parent.getFields() != null ? parent.getFields().getSummary() : "SIN RESUMEN").append("<br/>")
                .append("<b>Estimación Padre:</b> ").append(String.format("%.2f", parentEstimateHours)).append(" hrs (")
                .append(String.format("%.2f", parentEstimateDays)).append(" días)<br/>")
                .append("<b").append(isOverEstimated ? " class='error'" : "").append(">Total Subtareas:</b> ")
                .append(String.format("%.2f", subtasksTotalHours)).append(" hrs (")
                .append(String.format("%.2f", subtasksTotalDays)).append(" días)")
                .append("</div>");

        List<String> sortedDates = groupedByDate.keySet().stream().sorted().collect(Collectors.toList());

        for (String date : sortedDates) {
            double dateTotalHours = groupedByDate.get(date).stream()
                    .filter(s -> s.getFields() != null && s.getFields().getTimetracking() != null)
                    .mapToDouble(s -> s.getFields().getTimetracking().getOriginalEstimateSeconds() / 3600.0)
                    .sum();
            double dateTotalDays = dateTotalHours / 9.0;

            html.append("<h3>Fecha: ").append(date).append(" (").append(String.format("%.2f", dateTotalHours))
                    .append(" hrs - ").append(String.format("%.2f", dateTotalDays)).append(" días)</h3>")
                    .append("<table><tr><th>Clave</th><th>Resumen</th><th>Estado</th><th>Estimación</th></tr>");
            
            List<JiraIssue> sortedSubtasks = groupedByDate.get(date).stream()
                    .sorted(java.util.Comparator.comparing(JiraIssue::getKey))
                    .collect(Collectors.toList());

            for (JiraIssue subtask : sortedSubtasks) {
                double estimate = (subtask.getFields() != null && subtask.getFields().getTimetracking() != null) ? 
                        subtask.getFields().getTimetracking().getOriginalEstimateSeconds() / 3600.0 : 0.0;
                
                String statusName = (subtask.getFields() != null && subtask.getFields().getStatus() != null) ? 
                        subtask.getFields().getStatus().getName() : "UNKNOWN";

                html.append("<tr>")
                        .append("<td>").append(subtask.getKey()).append("</td>")
                        .append("<td>").append(subtask.getFields() != null ? subtask.getFields().getSummary() : "SIN RESUMEN").append("</td>")
                        .append("<td>").append(statusName).append("</td>")
                        .append("<td>").append(String.format("%.2f", estimate)).append(" hrs</td>")
                        .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html.toString(), null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            System.err.println("Error al generar el PDF de subtareas: " + e.getMessage());
            return null;
        }
    }
}
