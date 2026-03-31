# Requerimientos: Consultor de Registro de Horarios en Jira (Uso Personal)

Este documento detalla los requerimientos para una herramienta personal diseñada para consultar y visualizar el registro de horas (worklogs) en Jira.

## 1. Descripción General
El objetivo es contar con una aplicación basada en Spring Boot que permita visualizar el tiempo dedicado a diversas tareas en Jira, permitiendo consultar los registros de un periodo determinado y obtener informes en PDF con validaciones de carga horaria.

## 2. Requerimientos Funcionales

### RF1: Configuración de Conexión y Usuario
*   El sistema debe permitir configurar la URL de la instancia de Jira.
*   El sistema debe permitir configurar las credenciales de autenticación (Email y API Token).
*   **Usuario Objetivo:** El sistema debe permitir configurar el ID o Email del usuario a consultar mediante el archivo `application.properties` de Spring Boot.

### RF2: Consulta de Worklogs por Tarea (Issue)
*   El usuario debe poder consultar todos los registros de tiempo asociados a sus tareas asignadas.
*   La información mostrada debe incluir: Clave de la tarea, resumen, duración y fecha de inicio estimada.

### RF3: Generación de Informe en PDF y Validación de Horas
*   El sistema debe permitir consultar un rango de fechas (específicamente de Lunes a Viernes).
*   Para cada día laborable dentro del rango, el sistema debe calcular el total de horas registradas en Jira.
*   **Asociación de Horas:** Las horas deben contabilizarse en el día que indica el **Start Date** de la actividad, no necesariamente el día en que se registraron físicamente.
*   **Exportación a PDF:** El resultado de la consulta debe generarse obligatoriamente en un archivo **PDF**.
*   **Regla de Negocio y Formato Visual:**
    *   El informe debe listar cada día del rango con su total de horas.
    *   **Alertas en Rojo:** Aquellos días que tengan menos de **9 horas** registradas deben resaltarse en **color rojo**.
    *   **Cálculo de Faltantes:** Para los días marcados en rojo, el sistema debe indicar explícitamente **cuántas horas faltan** para alcanzar la meta de 9 horas.
    *   **Sugerencia de Capacitación:** El reporte debe incluir una columna que sugiera crear tareas de capacitación por las horas faltantes para cumplir con la meta diaria.

### RF4: Conexión Exclusiva vía API REST
*   El sistema debe interactuar con Jira de forma exclusiva a través de su API REST oficial (v3).
*   Se deben utilizar los endpoints de búsqueda JQL (`/rest/api/3/search/jql`) compatibles con los cambios de Mayo 2025.
*   El sistema debe manejar la autenticación mediante API Token en las cabeceras de las peticiones HTTP (Basic Auth).

### RF5: Endpoints de Consulta y Acción
*   **GET /api/report/pdf:** Genera y descarga el informe PDF (acepta parámetros opcionales `startDate` y `endDate`).
*   **GET /api/report/tasks:** Devuelve la lista de tareas asignadas con su estatus y tiempos (estimado vs real).
*   **POST /api/report/subtask/{parentKey}:** Crea una subtarea en Jira asociada a la tarea padre indicada. Los detalles de la subtarea (resumen, horas, fechas, autorizador, actividad) se configuran en `application.properties`.
*   **POST /api/report/ingest:** Realiza la ingesta masiva de tareas desde un archivo Excel, validando duplicados por nombre y fecha, ejecutando transiciones de estado y registrando horas si la tarea se marca como finalizada. Devuelve un Excel actualizado con las claves de los tickets.

## 3. Requerimientos No Funcionales

### RNF1: Seguridad de Credenciales
*   Las credenciales (API Token) se gestionan a través de `application.properties`.

### RNF2: Usabilidad
*   La herramienta funciona como un servicio web con endpoints REST y también genera un auto-reporte inicial al arrancar.

## 4. Stack Tecnológico (Spring Boot & Maven)
*   **Framework:** Spring Boot 3.x.
*   **Lenguaje:** Java 17.
*   **Gestor de Dependencias:** Maven.
*   **Librerías Clave:**
    *   `Spring Web` (RestTemplate).
    *   `Jackson` (JSON).
    *   `OpenHTMLtoPDF` (Generación de PDF desde HTML).
*   **Estructura:** Aplicación web con soporte para ejecución en línea de comandos (`CommandLineRunner`).

## 5. Flujo de Trabajo Típico
1.  El sistema arranca y genera un reporte PDF automático de la última semana.
2.  El usuario accede a `localhost:8080/api/report/pdf?startDate=YYYY-MM-DD` para obtener un reporte personalizado.
3.  El sistema consulta Jira, agrupa las horas por `startDate` del issue y genera el PDF con alertas visuales.
