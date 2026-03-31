package com.personal.jiralog;

import com.personal.jiralog.client.JiraClient;
import com.personal.jiralog.model.Worklog;
import com.personal.jiralog.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDate;
import java.util.List;

@SpringBootApplication
public class JiraLogApplication implements CommandLineRunner {

	@Autowired
	private JiraClient jiraClient;

	@Autowired
	private ReportService reportService;

	@Value("${jira.target-user-email}")
	private String targetUserEmail;

	@Value("${report.output-path}")
	private String outputPath;

	public static void main(String[] args) {
		SpringApplication.run(JiraLogApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("=================================================");
		System.out.println("  Servicio de Reportes Jira iniciado");
		System.out.println("  Endpoint: GET http://localhost:8080/api/report/pdf");
		System.out.println("  Usuario objetivo: " + targetUserEmail);
		System.out.println("=================================================");

		try {
			// Consultar la última semana de logs
			LocalDate today = LocalDate.now();
			LocalDate oneWeekAgoDate = today.minusWeeks(1);
			long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
			List<Worklog> worklogs = jiraClient.getUserWorklogs(targetUserEmail, oneWeekAgo);

			if (!worklogs.isEmpty()) {
				reportService.generatePdfReport(worklogs, outputPath, targetUserEmail, oneWeekAgoDate, today);
				System.out.println("  Auto-reporte generado en: " + outputPath);
			} else {
				System.out.println("  No se encontraron actividades de Jira para generar el auto-reporte inicial.");
			}
		} catch (Exception e) {
			System.out.println("  Aviso: No se pudo generar el auto-reporte inicial (verificar conexión/configuración/credenciales Jira).");
		}

		System.out.println("=================================================");
	}
}
