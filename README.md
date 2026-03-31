# Manejador de Horas — Jira Log Reporter

Aplicación personal basada en **Spring Boot** que permite consultar, visualizar y reportar el registro de horas (worklogs) en Jira. Genera informes en PDF, crea subtareas y soporta ingesta masiva desde Excel.

---

## Requisitos Previos

- **Java 17** o superior
- **Maven 3.x**
- Acceso a una instancia de **Jira Cloud** con un **API Token** generado

---

## Configuración

Antes de ejecutar la aplicación, edita el archivo `src/main/resources/application.properties` con tus datos:

```properties
# URL de tu instancia de Jira
jira.base-url=https://tu-empresa.atlassian.net

# Credenciales de autenticación
jira.email=tu-email@empresa.com
jira.api-token=TU_API_TOKEN_AQUI

# Usuario cuyas horas se consultarán
jira.target-user-email=tu-email@empresa.com

# Configuración de subtareas por defecto
jira.subtask.project-key=CLAVE_PROYECTO
jira.subtask.summary=dayly
jira.subtask.time-estimate=15m
jira.subtask.authorizer-id=ID_DEL_AUTORIZADOR
jira.subtask.activity-id=ID_DE_ACTIVIDAD
jira.subtask.issuetype-id=ID_TIPO_ISSUE

# Ruta de salida del PDF generado
report.output-path=reporte-horas.pdf
```

> **¿Cómo obtener un API Token de Jira?**
> 1. Ingresa a [https://id.atlassian.com/manage-profile/security/api-tokens](https://id.atlassian.com/manage-profile/security/api-tokens)
> 2. Haz clic en **Create API token**.
> 3. Copia el token generado y pégalo en `jira.api-token`.

---

## Ejecución

### Compilar y ejecutar con Maven

```bash
mvn spring-boot:run
```

### Compilar y ejecutar el JAR

```bash
mvn clean package
java -jar target/jiralog-*.jar
```

Al arrancar, la aplicación genera automáticamente un reporte PDF de la **última semana** y lo guarda en la ruta configurada en `report.output-path`.

La interfaz web estará disponible en: [http://localhost:8080](http://localhost:8080)

---

## Endpoints Disponibles

### `GET /api/report/pdf`
Genera y descarga el informe de horas en formato **PDF**.

| Parámetro   | Tipo   | Requerido | Descripción                        |
|-------------|--------|-----------|------------------------------------|
| `startDate` | String | No        | Fecha de inicio (`YYYY-MM-DD`)     |
| `endDate`   | String | No        | Fecha de fin (`YYYY-MM-DD`)        |

**Ejemplo:**
```
GET http://localhost:8080/api/report/pdf?startDate=2025-06-02&endDate=2025-06-06
```

El PDF generado incluye:
- Total de horas por día laborable (lunes a viernes).
- Días con **menos de 9 horas** resaltados en **rojo**.
- Indicación de cuántas horas faltan para alcanzar la meta diaria.
- Sugerencia de tareas de capacitación para cubrir las horas faltantes.

---

### `GET /api/report/tasks`
Devuelve en JSON la lista de tareas asignadas al usuario con su estatus y tiempos (estimado vs. real).

**Ejemplo:**
```
GET http://localhost:8080/api/report/tasks
```

---

### `POST /api/report/subtask/{parentKey}`
Crea una subtarea en Jira asociada a la tarea padre indicada, usando los valores configurados en `application.properties`.

**Ejemplo:**
```
POST http://localhost:8080/api/report/subtask/CDST05-3387
```

---

### `POST /api/report/ingest`
Realiza la **ingesta masiva** de tareas desde un archivo Excel (`.xlsx`).

**Body:** `form-data`
- `file` → archivo Excel a procesar

**Ejemplo con curl:**
```bash
curl -X POST http://localhost:8080/api/report/ingest \
  -F "file=@registro de actividades.xlsx" \
  --output resultado.xlsx
```

El sistema:
1. Valida duplicados por nombre y fecha de inicio.
2. Crea las subtareas que no existan en Jira.
3. Ejecuta transiciones de estado según la columna **Status**.
4. Registra horas si la tarea se marca como **Finalizada**.
5. Devuelve un Excel actualizado con la columna **Clave Ticket**.

---

## Formato del Excel para Ingesta

El archivo `.xlsx` debe tener las siguientes columnas (la fila 1 es encabezado, los datos comienzan en la fila 2):

| Columna        | Descripción                                              | Ejemplo          |
|----------------|----------------------------------------------------------|------------------|
| Parent Key     | Clave de la tarea padre en Jira                          | `CDST05-3387`    |
| Summary        | Nombre descriptivo de la subtarea                        | `Revisión de PR` |
| Activity Name  | Nombre de la actividad (ver `ACTIVIDADES_JIRA.md`)       | `Desarrollo`     |
| Estimate       | Tiempo estimado                                          | `2h`, `30m`      |
| Start Date     | Fecha de inicio (`YYYY-MM-DD`)                           | `2025-06-02`     |
| Target Date    | Fecha de fin (`YYYY-MM-DD`)                              | `2025-06-02`     |
| Status         | Estado deseado                                           | `Finalizada`     |

**Estados soportados:** `Por hacer`, `En Curso`, `Finalizada`

---

## Colección Postman

El proyecto incluye el archivo `JiraLogReporter.postman_collection.json` con todas las peticiones preconfiguradas. Impórtalo en Postman para probar los endpoints fácilmente.

---

## Stack Tecnológico

| Componente        | Tecnología                  |
|-------------------|-----------------------------|
| Framework         | Spring Boot 3.x             |
| Lenguaje          | Java 17                     |
| Gestor de deps.   | Maven                       |
| HTTP Client       | RestTemplate (Spring Web)   |
| JSON              | Jackson                     |
| Generación PDF    | OpenHTMLtoPDF               |
| Lectura Excel     | Apache POI                  |
