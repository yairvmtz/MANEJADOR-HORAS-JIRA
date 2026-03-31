package com.personal.jiralog.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.jiralog.model.JiraIssue;
import com.personal.jiralog.model.Worklog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String authHeader;
    private final ObjectMapper objectMapper;
    private String email;
    private String accountId;

    @Value("${jira.subtask.issuetype-id:10008}")
    private String subtaskIssueTypeId;

    @Value("${jira.subtask.project-key:}")
    private String defaultProjectKey;

    public JiraClient(RestTemplate restTemplate,
                      @Value("${jira.base-url}") String baseUrl,
                      @Value("${jira.email}") String email,
                      @Value("${jira.api-token}") String apiToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.email = email;
        this.objectMapper = new ObjectMapper();
        String auth = email + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());
        this.accountId = resolveMyAccountId();
    }

    private String resolveMyAccountId() {
        String url = baseUrl + "/rest/api/3/myself";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String id = root.path("accountId").asText();
            log.info("AccountId resuelto para {}: {}", email, id);
            return id;
        } catch (Exception e) {
            log.error("Error al resolver accountId propio: {}", e.getMessage());
            // Fallback al valor previo conocido por si acaso
            return "712020:d1d604b2-b76f-4587-88e1-4d1d3b7feba3";
        }
    }

    public String getAccountIdByEmail(String userEmail) {
        if (userEmail == null || userEmail.isEmpty()) return null;
        if (userEmail.equalsIgnoreCase(this.email)) return this.accountId;

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/user/search")
                .queryParam("query", userEmail)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("accountId").asText();
            }
        } catch (Exception e) {
            log.error("Error al buscar accountId para {}: {}", userEmail, e.getMessage());
        }
        return null;
    }

    public String getUserDisplayName(String userEmail) {
        if (userEmail == null || userEmail.isEmpty()) return null;

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/user/search")
                .queryParam("query", userEmail)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray() && root.size() > 0) {
                return root.get(0).path("displayName").asText();
            }
        } catch (Exception e) {
            log.error("Error al buscar nombre para {}: {}", userEmail, e.getMessage());
        }
        return userEmail;
    }

    public Map<String, Object> createSubtask(String parentKey, String summary, String timeEstimate, String startDate, String targetDate, String authorizerId, String activityId) {
        String url = baseUrl + "/rest/api/3/issue";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");

        // Construir el cuerpo de la petición para Jira Cloud API v3
        Map<String, Object> fields = new HashMap<>();
        
        // Proyecto y Parent
        String projectKey = null;
        String finalParentKey = parentKey != null ? parentKey.trim() : null;

        if (finalParentKey != null && finalParentKey.contains("-")) {
            projectKey = finalParentKey.split("-")[0].trim().toUpperCase();
        } else if (defaultProjectKey != null && !defaultProjectKey.isEmpty()) {
            projectKey = defaultProjectKey;
            // Si el parentKey es solo el número, le agregamos el prefijo del proyecto
            if (finalParentKey != null && !finalParentKey.isEmpty() && !finalParentKey.contains("-")) {
                finalParentKey = projectKey + "-" + finalParentKey;
            }
        }

        if (projectKey != null && !projectKey.isEmpty()) {
            Map<String, String> project = new HashMap<>();
            project.put("key", projectKey);
            fields.put("project", project);
        } else {
            log.warn("No se pudo extraer ni encontrar una clave de proyecto para parentKey: {}", parentKey);
        }
        
        Map<String, String> parent = new HashMap<>();
        parent.put("key", finalParentKey);
        fields.put("parent", parent);
        
        // IssueType (Sub-task)
        Map<String, String> issuetype = new HashMap<>();
        issuetype.put("id", subtaskIssueTypeId);
        fields.put("issuetype", issuetype);
        
        fields.put("summary", summary);
        
        // Asignado al usuario actual
        Map<String, String> assignee = new HashMap<>();
        assignee.put("accountId", accountId);
        fields.put("assignee", assignee);

        // Campos personalizados específicos de la instancia
        if (startDate != null && !startDate.isEmpty()) {
            fields.put("customfield_10015", startDate);
        } else {
            // Valor por defecto hoy si es obligatorio
            fields.put("customfield_10015", java.time.LocalDate.now().toString());
        }
        
        if (targetDate != null && !targetDate.isEmpty()) {
            fields.put("customfield_10715", targetDate);
        } else {
            // Valor por defecto hoy + 1 si es obligatorio
            fields.put("customfield_10715", java.time.LocalDate.now().plusDays(1).toString());
        }
        
        // Autorizador (Select List)
        if (authorizerId != null) {
            Map<String, String> authorizer = new HashMap<>();
            authorizer.put("accountId", authorizerId);
            fields.put("customfield_10854", authorizer);
        }

        // Actividad (Multi Select)
        if (activityId != null) {
            List<Map<String, String>> activities = new ArrayList<>();
            Map<String, String> activity = new HashMap<>();
            activity.put("id", activityId);
            activities.add(activity);
            fields.put("customfield_10776", activities);
        }
        
        // Timetracking
        Map<String, String> timetracking = new HashMap<>();
        timetracking.put("originalEstimate", timeEstimate);
        fields.put("timetracking", timetracking);

        Map<String, Object> body = new HashMap<>();
        body.put("fields", fields);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            log.debug("Enviando petición a Jira (createSubtask): {}", requestJson);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Respuesta de Jira (createSubtask): {}", objectMapper.writeValueAsString(response.getBody()));
            log.info("Subtarea creada exitosamente en Jira: {}", response.getBody().get("key"));
            return response.getBody();
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                errorMsg = ((org.springframework.web.client.HttpStatusCodeException) e).getResponseBodyAsString();
            }
            log.error("Error al crear subtarea en Jira ({}): {}", summary, errorMsg);
            
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", errorMsg);
            return errorMap;
        }
    }

    public List<Map<String, Object>> findIssuesBySummaryAndDate(String summary, String date, String userEmail, String parentKey) {
        log.info("Buscando incidencia: '{}' para el usuario '{}' en la fecha '{}' y padre '{}'", summary, userEmail, date, parentKey);
        
        // 1. Escapar el resumen para JQL (especialmente si se usa ~ para búsqueda de texto)
        // Jira JQL requiere escapar caracteres especiales en búsquedas de texto con ~
        String escapedSummary = escapeJqlText(summary);
        
        // Normalizar el parentKey para que incluya el prefijo del proyecto si es necesario
        String finalParentKey = parentKey != null ? parentKey.trim() : null;
        if (finalParentKey != null && !finalParentKey.isEmpty() && !finalParentKey.contains("-") && defaultProjectKey != null && !defaultProjectKey.isEmpty()) {
            finalParentKey = defaultProjectKey + "-" + finalParentKey;
        }

        // Intentar resolver accountId siempre para evitar problemas de privacidad en Jira Cloud
        String targetAccountId = getAccountIdByEmail(userEmail);
        String assigneePart = targetAccountId != null ? targetAccountId : userEmail;
        
        log.debug("Usando assigneePart para JQL: {} y finalParentKey: {}", assigneePart, finalParentKey);
        
        // Construir JQL más permisivo para asegurar que traemos los issues y filtramos localmente
        // Buscamos principalmente por el asignado y el padre (si existe) o el resumen aproximado
        StringBuilder jql = new StringBuilder();
        jql.append("assignee = \"").append(assigneePart).append("\"");
        
        if (finalParentKey != null && !finalParentKey.isEmpty()) {
            jql.append(" AND parent = \"").append(finalParentKey).append("\"");
        }
        
        // El resumen lo usamos como filtro JQL solo para reducir el volumen, 
        // pero la validación real de "exacto" se hace en Java.
        // Usamos comillas dobles internas para buscar la frase exacta en JQL
        jql.append(" AND summary ~ \"\\\"").append(escapedSummary).append("\\\"\"");

        // Si hay fecha, la añadimos al JQL solo si es para ampliar (OR) o para reducir (AND)
        // Para ser más robustos, NO filtramos por fecha en JQL si ya tenemos padre y asignado,
        // ya que el filtrado local en Java es mucho más fiable con múltiples campos de fecha.
        // Solo añadimos el filtro de fecha si NO tenemos padre, para no traer demasiados issues.
        if ((finalParentKey == null || finalParentKey.isEmpty()) && date != null && !date.isEmpty()) {
            String nextDate = java.time.LocalDate.parse(date).plusDays(1).toString();
            jql.append(" AND (");
            jql.append("(created >= \"").append(date).append("\" AND created < \"").append(nextDate).append("\")");
            jql.append(" OR duedate = \"").append(date).append("\"");
            jql.append(" OR customfield_10015 = \"").append(date).append("\"");
            jql.append(" OR customfield_10715 = \"").append(date).append("\"");
            jql.append(")");
        }

        // Ordenar por creación descendente
        jql.append(" ORDER BY created DESC");

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/search/jql")
                .queryParam("jql", jql.toString())
                .queryParam("fields", "summary,customfield_10015,assignee,status,created,parent,duedate,startDate,startdate,customfield_10715")
                .queryParam("maxResults", 50)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando Jira (findIssuesBySummaryAndDate) JQL: {}", jql.toString());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("issues")) {
                List<Map<String, Object>> issues = (List<Map<String, Object>>) body.get("issues");
                log.debug("Se encontraron {} incidencias potenciales en Jira con el JQL", issues.size());
                
                List<Map<String, Object>> exactMatches = new ArrayList<>();
                for (Map<String, Object> issue : issues) {
                    Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
                    if (fields != null) {
                        String issueKey = issue.get("key") != null ? issue.get("key").toString() : "Unknown";
                        
                        // 1. Validar resumen exacto
                        String issueSummary = fields.get("summary") != null ? fields.get("summary").toString().trim() : "";
                        String targetSummary = summary.trim();
                        boolean summaryMatch = issueSummary.equalsIgnoreCase(targetSummary);
                        
                        // 2. Validar fecha
                        String issueStartDateCF = fields.get("customfield_10015") != null ? fields.get("customfield_10015").toString() : null;
                        String issueCreatedDate = fields.get("created") != null ? fields.get("created").toString().substring(0, 10) : null;
                        String issueDueDate = fields.get("duedate") != null ? fields.get("duedate").toString() : null;
                        String issueStartDateSys = fields.get("startDate") != null ? fields.get("startDate").toString() : null;
                        String issueStartDateSys2 = fields.get("startdate") != null ? fields.get("startdate").toString() : null;
                        String issueTargetDateCF = fields.get("customfield_10715") != null ? fields.get("customfield_10715").toString() : null;
                        
                        // Normalizar fechas
                        if (issueStartDateSys != null && issueStartDateSys.length() > 10) issueStartDateSys = issueStartDateSys.substring(0, 10);
                        if (issueStartDateSys2 != null && issueStartDateSys2.length() > 10) issueStartDateSys2 = issueStartDateSys2.substring(0, 10);
                        if (issueDueDate != null && issueDueDate.length() > 10) issueDueDate = issueDueDate.substring(0, 10);
                        if (issueStartDateCF != null && issueStartDateCF.length() > 10) issueStartDateCF = issueStartDateCF.substring(0, 10);
                        if (issueTargetDateCF != null && issueTargetDateCF.length() > 10) issueTargetDateCF = issueTargetDateCF.substring(0, 10);

                        boolean dateMatch = false;
                        if (date == null || date.isEmpty()) {
                            dateMatch = true; 
                        } else {
                            if (date.equals(issueStartDateCF) || date.equals(issueCreatedDate) || date.equals(issueDueDate) 
                                    || date.equals(issueStartDateSys) || date.equals(issueStartDateSys2) || date.equals(issueTargetDateCF)) {
                                dateMatch = true;
                            } else {
                                log.debug("Issue {} no coincide por fecha. Buscada: {}. Encontradas: CF10015:{}, Created:{}, Due:{}, CF10715: {}, SD1:{}, SD2:{}", 
                                        issueKey, date, issueStartDateCF, issueCreatedDate, issueDueDate, issueTargetDateCF, issueStartDateSys, issueStartDateSys2);
                            }
                        }
                        
                        // 3. Validar asignado
                        boolean assigneeMatch = false;
                        Map<String, Object> assignee = (Map<String, Object>) fields.get("assignee");
                        if (assignee != null) {
                            String issueAssigneeId = assignee.get("accountId") != null ? assignee.get("accountId").toString() : null;
                            String issueEmail = assignee.get("emailAddress") != null ? assignee.get("emailAddress").toString() : null;

                            if (issueAssigneeId != null && (issueAssigneeId.equals(assigneePart) || issueAssigneeId.equals(targetAccountId))) {
                                assigneeMatch = true;
                            } else if (issueEmail != null && (issueEmail.equalsIgnoreCase(userEmail) || issueEmail.equalsIgnoreCase(assigneePart))) {
                                assigneeMatch = true;
                            } else {
                                log.debug("Issue {} no coincide por asignado. Encontrado: {} / {}. Buscado: {} o {}", 
                                        issueKey, issueAssigneeId, issueEmail, assigneePart, userEmail);
                            }
                        } else {
                            log.debug("Issue {} sin asignado visible, confiando en JQL", issueKey);
                            assigneeMatch = true;
                        }

                        // 4. Validar Padre
                        boolean parentMatch = true;
                        if (finalParentKey != null && !finalParentKey.trim().isEmpty()) {
                            Map<String, Object> parent = (Map<String, Object>) fields.get("parent");
                            if (parent != null && parent.containsKey("key")) {
                                String issueParentKey = parent.get("key").toString();
                                parentMatch = issueParentKey.equalsIgnoreCase(finalParentKey.trim());
                            } else {
                                parentMatch = false;
                            }
                            if (!parentMatch) {
                                log.debug("Issue {} no coincide por padre. Esperado: {}", issueKey, finalParentKey);
                            }
                        }

                        if (summaryMatch && dateMatch && assigneeMatch && parentMatch) {
                            log.info("Coincidencia exacta encontrada: {} - {}", issue.get("key"), issueSummary);
                            exactMatches.add(issue);
                        } else if (!summaryMatch) {
                             log.debug("Issue {} no coincide por resumen. Encontrado: '{}', Buscado: '{}'", issueKey, issueSummary, targetSummary);
                        }
                    }
                }
                
                return exactMatches;
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                errorMsg = ((org.springframework.web.client.HttpStatusCodeException) e).getResponseBodyAsString();
            }
            log.error("Error al buscar incidencias en Jira (JQL: {}): {}", jql.toString(), errorMsg);
        }
        return Collections.emptyList();
    }

    private String escapeJqlText(String text) {
        if (text == null) return "";
        // Caracteres reservados en JQL text search (~): + - & | ! ( ) { } [ ] ^ ~ * ? : \ /
        // Se deben escapar con \\
        String[] reservedChars = {"\\", "+", "-", "&", "|", "!", "(", ")", "{", "}", "[", "]", "^", "~", "*", "?", ":", "/"};
        String escaped = text;
        for (String c : reservedChars) {
            escaped = escaped.replace(c, "\\\\" + c);
        }
        // También escapar comillas dobles
        escaped = escaped.replace("\"", "\\\"");
        return escaped;
    }

    public JiraIssue getIssue(String issueKey) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "?fields=summary,status,timetracking,duedate,startdate,startDate,customfield_10015,customfield_10715";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando detalle de la incidencia: {}", issueKey);
            ResponseEntity<JiraIssue> response = restTemplate.exchange(url, HttpMethod.GET, entity, JiraIssue.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error al obtener detalle de {}: {}", issueKey, e.getMessage());
        }
        return null;
    }

    public List<JiraIssue> getSubtasks(String parentKey) {
        String jql = String.format("parent = '%s'", parentKey);
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("fields", "summary,status,timetracking,duedate,startdate,startDate,customfield_10015,customfield_10715")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando subtareas para {}. JQL: {}", parentKey, jql);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issuesNode = root.path("issues");
            return objectMapper.convertValue(issuesNode, new TypeReference<List<JiraIssue>>() {});
        } catch (Exception e) {
            log.error("Error al consultar subtareas de {}: {}", parentKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String getIssueStatus(String issueKey) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "?fields=status";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando estado de la incidencia: {}", issueKey);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("fields")) {
                Map<String, Object> fields = (Map<String, Object>) body.get("fields");
                if (fields.containsKey("status")) {
                    Map<String, Object> status = (Map<String, Object>) fields.get("status");
                    return status.get("name").toString();
                }
            }
        } catch (Exception e) {
            log.error("Error al obtener estado de {}: {}", issueKey, e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> getTransitions(String issueKey) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando transiciones en Jira para: {}", issueKey);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                log.debug("Respuesta de transiciones Jira para {}: {}", issueKey, objectMapper.writeValueAsString(body));
                if (body.containsKey("transitions")) {
                    List<Map<String, Object>> transitions = (List<Map<String, Object>>) body.get("transitions");
                    log.debug("Se obtuvieron {} transiciones posibles para {}", transitions.size(), issueKey);
                    return transitions;
                }
            }
        } catch (Exception e) {
            log.error("Error al obtener transiciones para {}: {}", issueKey, e.getMessage());
        }
        return Collections.emptyList();
    }

    public void doTransition(String issueKey, String transitionId) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        Map<String, String> transition = new HashMap<>();
        transition.put("id", transitionId);
        body.put("transition", transition);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Ejecutando transición en Jira para {}: {}", issueKey, objectMapper.writeValueAsString(body));
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            log.info("Transición {} ejecutada exitosamente para {}", transitionId, issueKey);
        } catch (Exception e) {
            log.error("Error al ejecutar transición {} para {}: {}", transitionId, issueKey, e.getMessage());
        }
    }

    public void addWorklog(String issueKey, String timeSpent, String comment, String date) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/worklog";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("timeSpent", timeSpent);
        
        // Formato ADF (Atlassian Document Format) para el comentario en API v3
        Map<String, Object> adfComment = new HashMap<>();
        adfComment.put("type", "doc");
        adfComment.put("version", 1);
        
        Map<String, Object> paragraph = new HashMap<>();
        paragraph.put("type", "paragraph");
        
        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", comment != null ? comment : "Registro de trabajo automático");
        
        paragraph.put("content", Collections.singletonList(text));
        adfComment.put("content", Collections.singletonList(paragraph));
        
        body.put("comment", adfComment);
        
        // Formato de fecha esperado: yyyy-MM-dd'T'HH:mm:ss.SSSZ
        body.put("started", date + "T09:00:00.000+0000");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            log.debug("Añadiendo worklog en Jira para {}: {}", issueKey, requestJson);
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            log.info("Worklog de {} añadido exitosamente a {}", timeSpent, issueKey);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                errorMsg = ((org.springframework.web.client.HttpStatusCodeException) e).getResponseBodyAsString();
            }
            log.error("Error al añadir worklog a {}: {}", issueKey, errorMsg);
        }
    }

    public List<JiraIssue> getAssignedIssues(String userEmail) {
        // JQL para buscar tareas asignadas al usuario que no estén cerradas (opcionalmente)
        String jql = String.format("assignee = '%s'", userEmail);
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("fields", "summary,status,timetracking,worklog,duedate,startdate,startDate,customfield_10015,customfield_10715")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Consultando tareas asignadas en Jira (getAssignedIssues) JQL: {}", jql);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.debug("Respuesta de tareas asignadas Jira: {}", response.getBody());
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issuesNode = root.path("issues");
            List<JiraIssue> issues = objectMapper.convertValue(issuesNode, new TypeReference<List<JiraIssue>>() {});
            
            // Post-procesar para asignar StartDate a los worklogs internos
            for (JiraIssue issue : issues) {
                if (issue.getFields().getWorklog() != null && issue.getFields().getWorklog().getWorklogs() != null) {
                    // Buscar la fecha de inicio entre varios campos posibles (prioridad a Target Date)
                    String issueStartDate = issue.getFields().getCustomfield_10715();
                    if (issueStartDate == null || issueStartDate.isEmpty() || issueStartDate.equals("null")) {
                        issueStartDate = issue.getFields().getCustomfield_10015();
                    }
                    if (issueStartDate == null || issueStartDate.isEmpty() || issueStartDate.equals("null")) {
                        issueStartDate = issue.getFields().getStartdate();
                    }
                    if (issueStartDate == null || issueStartDate.isEmpty() || issueStartDate.equals("null")) {
                        issueStartDate = issue.getFields().getStartDate();
                    }
                    if (issueStartDate == null || issueStartDate.isEmpty() || issueStartDate.equals("null")) {
                        issueStartDate = issue.getFields().getDuedate(); // Fallback temporal si no hay startDate
                    }

                    for (Worklog wl : issue.getFields().getWorklog().getWorklogs()) {
                        if (issueStartDate != null && !issueStartDate.isEmpty() && !issueStartDate.equals("null")) {
                            wl.setStartDate(issueStartDate.substring(0, 10));
                        } else {
                            wl.setStartDate(wl.getStarted().substring(0, 10));
                        }
                    }
                }
            }
            return issues;
        } catch (Exception e) {
            log.error("Error al consultar tareas asignadas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Worklog> getUserWorklogs(String userEmail, long sinceMillis) {
        String targetAccountId = getAccountIdByEmail(userEmail);
        String sinceDate = formatDate(sinceMillis);
        
        // En JQL, si tenemos el accountId, es mejor usarlo
        String jqlAuthor = (targetAccountId != null) ? targetAccountId : userEmail;
        String jql = String.format("worklogAuthor = '%s' AND worklogDate >= '%s'", jqlAuthor, sinceDate);
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("fields", "summary,duedate,startdate,startDate,customfield_10015,customfield_10715")
                .queryParam("maxResults", "100")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("Consultando issues con worklogs para el reporte. JQL: {}", jql);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issues = root.path("issues");
            List<Worklog> allWorklogs = new ArrayList<>();

            for (JsonNode issueNode : issues) {
                String issueKey = issueNode.path("key").asText();
                String issueSummary = issueNode.path("fields").path("summary").asText();
                
                // Obtener fecha de inicio del issue (prioridad a Target Date)
                JsonNode fields = issueNode.path("fields");
                String issueStartDate = null;
                if (fields.has("customfield_10715") && !fields.path("customfield_10715").isNull()) {
                    issueStartDate = fields.path("customfield_10715").asText();
                } else if (fields.has("customfield_10015") && !fields.path("customfield_10015").isNull()) {
                    issueStartDate = fields.path("customfield_10015").asText();
                } else if (fields.has("startDate") && !fields.path("startDate").isNull()) {
                    issueStartDate = fields.path("startDate").asText();
                } else if (fields.has("startdate") && !fields.path("startdate").isNull()) {
                    issueStartDate = fields.path("startdate").asText();
                } else if (fields.has("duedate") && !fields.path("duedate").isNull()) {
                    issueStartDate = fields.path("duedate").asText();
                }
                
                String finalStartDate = (issueStartDate != null && issueStartDate.length() >= 10) ? issueStartDate.substring(0, 10) : null;

                // Consultar worklogs específicos de este issue para obtener el array completo
                String worklogUrl = baseUrl + "/rest/api/3/issue/" + issueKey + "/worklog";
                ResponseEntity<String> wlResponse = restTemplate.exchange(worklogUrl, HttpMethod.GET, entity, String.class);
                JsonNode wlRoot = objectMapper.readTree(wlResponse.getBody());
                JsonNode worklogsArray = wlRoot.path("worklogs");

                if (worklogsArray.isArray()) {
                    for (JsonNode wlNode : worklogsArray) {
                        Worklog wl = objectMapper.treeToValue(wlNode, Worklog.class);
                        String wlDate = wl.getStarted().substring(0, 10);
                        
                        // Filtrar por autor y fecha
                        String authorEmail = wl.getAuthor() != null ? wl.getAuthor().getEmailAddress() : null;
                        String authorAccountId = wl.getAuthor() != null ? wl.getAuthor().getAccountId() : null;
                        
                        boolean emailMatch = authorEmail != null && authorEmail.equalsIgnoreCase(userEmail);
                        boolean accountMatch = authorAccountId != null && targetAccountId != null && authorAccountId.equals(targetAccountId);
                        
                        // Si no tenemos accountId del target, confiamos en lo que devuelva Jira si el JQL filtró bien,
                        // pero siempre es más seguro validar. Si Jira no devuelve email, el accountMatch es vital.
                        boolean authorMatch = emailMatch || accountMatch;

                        if (authorMatch && wlDate.compareTo(sinceDate) >= 0) {
                            wl.setIssueKey(issueKey);
                            wl.setIssueSummary(issueSummary);
                            wl.setStartDate(finalStartDate != null ? finalStartDate : wlDate);
                            allWorklogs.add(wl);
                        } else {
                            log.debug("Worklog de {} ({}) ignorado para issue {}. Esperado: {} o {}. Fecha: {}", 
                                authorEmail, authorAccountId, issueKey, userEmail, targetAccountId, wlDate);
                        }
                    }
                }
            }
            log.info("Total de worklogs recuperados de Jira: {}", allWorklogs.size());
            return allWorklogs;
        } catch (Exception e) {
            log.error("Error al recuperar worklogs de Jira: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String formatDate(long millis) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(millis));
    }
}
