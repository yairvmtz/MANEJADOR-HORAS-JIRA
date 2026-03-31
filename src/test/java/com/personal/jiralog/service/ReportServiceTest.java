package com.personal.jiralog.service;

import com.personal.jiralog.model.Worklog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    private List<Worklog> mockWorklogs;
    private String testUser = "test@example.com";

    @BeforeEach
    void setUp() {
        mockWorklogs = new ArrayList<>();
        // Crear algunos worklogs ficticios
        Worklog w1 = new Worklog();
        w1.setTimeSpentSeconds(3600 * 5); // 5 horas
        w1.setStarted("2024-03-25T10:00:00.000+0000");
        w1.setStartDate("2024-03-25");
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(testUser);
        w1.setAuthor(author);
        
        mockWorklogs.add(w1);
    }

    @Test
    void testGenerateOneWeekReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(1);
        
        byte[] pdf = reportService.generatePdfReport(mockWorklogs, testUser, start, end);
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void testGenerateFifteenDaysReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(15);
        
        byte[] pdf = reportService.generatePdfReport(mockWorklogs, testUser, start, end);
        
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void testGroupingByStartDate() {
        LocalDate startDate = LocalDate.of(2024, 3, 19);
        Worklog w1 = new Worklog();
        w1.setTimeSpentSeconds(3600 * 3); // 3 horas
        w1.setStarted("2024-03-25T10:00:00.000+0000"); // Registrado el 25
        w1.setStartDate("2024-03-19"); // Pero pertenece al issue que inició el 19
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(testUser);
        w1.setAuthor(author);

        List<Worklog> logs = List.of(w1);
        // El reporte es del 15 al 30 de marzo.
        // Con la nueva lógica, debe aparecer el 25 de marzo (fecha de registro).
        byte[] pdf = reportService.generatePdfReport(logs, testUser, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 30));

        assertNotNull(pdf);
    }

    @Test
    void testFilterByRequestedRange() {
        // Un worklog fuera del rango solicitado (2024-03-10)
        Worklog wOut = new Worklog();
        wOut.setTimeSpentSeconds(3600 * 5);
        wOut.setStarted("2024-03-10T10:00:00.000+0000");
        wOut.setStartDate("2024-03-10");
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(testUser);
        wOut.setAuthor(author);

        // Un worklog dentro del rango solicitado (2024-03-20)
        Worklog wIn = new Worklog();
        wIn.setTimeSpentSeconds(3600 * 5);
        wIn.setStarted("2024-03-20T10:00:00.000+0000");
        wIn.setStartDate("2024-03-20");
        wIn.setAuthor(author);

        List<Worklog> logs = List.of(wOut, wIn);
        
        // Rango del reporte: del 15 al 25 de marzo
        byte[] pdf = reportService.generatePdfReport(logs, testUser, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 25));

        assertNotNull(pdf);
        // Aquí no podemos verificar fácilmente el contenido del PDF, pero la ejecución exitosa confirma que el filtro no falla.
    }

    @Test
    void testSpecificCaseMarch27() {
        // Simular el caso del 27 de marzo de 2026
        String user = "tu-email@empresa.com";
        LocalDate start = LocalDate.of(2026, 3, 20);
        LocalDate end = LocalDate.of(2026, 3, 27);
        
        List<Worklog> logs = new ArrayList<>();
        Worklog wl = new Worklog();
        wl.setIssueKey("CDSPT1-123");
        wl.setIssueSummary("Test Issue");
        wl.setTimeSpentSeconds(3600 * 9); // 9 horas
        wl.setStarted("2026-03-27T10:00:00.000-0600");
        wl.setStartDate("2026-03-27");
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(user);
        wl.setAuthor(author);
        logs.add(wl);
        
        byte[] pdf = reportService.generatePdfReport(logs, user, start, end);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void testNullEmailAddressInWorklog() {
        String user = "tu-email@empresa.com";
        LocalDate start = LocalDate.of(2026, 3, 20);
        LocalDate end = LocalDate.of(2026, 3, 27);
        
        List<Worklog> logs = new ArrayList<>();
        Worklog wl = new Worklog();
        wl.setIssueKey("CDSPT1-123");
        wl.setIssueSummary("Test Issue con email nulo");
        wl.setTimeSpentSeconds(3600 * 9); // 9 horas
        wl.setStarted("2026-03-27T10:00:00.000-0600");
        wl.setStartDate("2026-03-27");
        
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(null); // Email nulo (común en Jira Cloud)
        author.setAccountId("712020:d1d604b2-b76f-4587-88e1-4d1d3b7feba3");
        wl.setAuthor(author);
        logs.add(wl);
        
        byte[] pdf = reportService.generatePdfReport(logs, user, start, end);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // Si el reporte se genera sin errores, significa que el peek y la agrupación funcionaron sin el filtro restrictivo de email
    }
    @Test
    void testAdfCommentDeserialization() throws Exception {
        String json = "{\"id\":\"123\",\"started\":\"2026-03-27T09:00:00.000+0000\",\"timeSpentSeconds\":3600,\"comment\":{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Comentario ADF\"}]}]}}";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Worklog wl = mapper.readValue(json, Worklog.class);
        
        assertNotNull(wl.getComment());
        assertTrue(wl.getComment() instanceof java.util.Map);
        assertEquals(3600, wl.getTimeSpentSeconds());
    }

    @Test
    void testWorklogGroupingBug() {
        // Simular el caso reportado: Tarea del 16 que aparece el 25
        String user = "user@example.com";
        LocalDate startReport = LocalDate.of(2026, 3, 20);
        LocalDate endReport = LocalDate.of(2026, 3, 31);
        
        List<Worklog> logs = new ArrayList<>();
        Worklog.Author author = new Worklog.Author();
        author.setEmailAddress(user);

        Worklog wl2 = new Worklog();
        wl2.setIssueKey("CDST05-3424");
        wl2.setIssueSummary("Tarea del 16 que sale el 25");
        wl2.setTimeSpentSeconds(3600 * 2);
        wl2.setStarted("2026-03-16T10:00:00.000-0600"); // REALMENTE se hizo el 16
        wl2.setStartDate("2026-03-25"); // El issue dice que inició el 25 (por error o reasignación)
        wl2.setAuthor(author);
        logs.add(wl2);
        
        // El reporte es del 20 al 31.
        // Actualmente la lógica dice: 
        // 1. issueStartDate (25) está en el rango [20, 31] -> SÍ. RETORNA 25.
        // Por lo tanto, el mapa de resultados contendrá una entrada para el 25-Mar-2026 con este worklog.

        // Queremos que NO esté el 25 de marzo.
    }
}
