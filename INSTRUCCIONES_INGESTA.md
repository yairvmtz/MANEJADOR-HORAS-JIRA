# Instrucciones para la Ingesta Masiva desde Excel

Esta nueva funcionalidad permite crear múltiples subtareas en Jira a partir de un archivo Excel, gestionar sus estados y registrar horas automáticamente.

## 1. Formato del Archivo Excel
El archivo debe ser un `.xlsx` con las siguientes columnas en la primera hoja (comenzando desde la fila 2, la fila 1 es encabezado):

1.  **Parent Key:** Clave de la tarea padre (ej. `CDST05-3387`).
2.  **Summary:** Nombre descriptivo de la subtarea.
3.  **Activity Name:** Nombre de la actividad (debe coincidir con los nombres en `ACTIVIDADES_JIRA.md`).
4.  **Estimate:** Tiempo estimado (ej. `2h`, `1d`, `30m`).
5.  **Start Date:** Fecha de inicio (formato `YYYY-MM-DD` o celda de fecha).
6.  **Target Date:** Fecha de fin (formato `YYYY-MM-DD` o celda de fecha).
7.  **Status:** Estado deseado (ej. `Finalizada`, `En Curso`, `Por hacer`).

## 2. Uso del Endpoint
**URL:** `POST http://localhost:8080/api/report/ingest`
**Body:** `form-data`
*   `file`: Selecciona tu archivo Excel.

### Comportamiento del sistema:
1.  **Validación de Duplicados:** Antes de crear una tarea, el sistema busca si ya existe una tarea con el mismo nombre creada en la misma fecha de inicio. Si existe, la reutiliza.
2.  **Creación:** Si no existe, crea una nueva subtarea con los datos del Excel y el autorizador configurado en `application.properties`.
3.  **Transiciones:** El sistema intentará mover la tarea al estado indicado en la columna **Status**. 
    *   Si el estado es "Finalizada", "Done" o "Cerrada", el sistema moverá la tarea a ese estado (pasando por "En Curso" si es necesario) y registrará el tiempo indicado en **Estimate** como trabajo concluido.
4.  **Respuesta:** El endpoint devuelve un nuevo archivo Excel (descarga automática) con una columna adicional llamada **Clave Ticket**, que contiene el ID de la tarea creada o encontrada en Jira.

## 3. Estados Soportados
El sistema busca coincidencias por nombre de estado. Se recomienda usar:
*   `Por hacer` (To Do)
*   `En Curso` (In Progress)
*   `Finalizada` (Done)

## 4. Ejemplo en Postman
En la colección de Postman actualizada, utiliza la petición **"Ingesta Masiva Excel"**:
1.  Ve a la pestaña **Body**.
2.  Selecciona **form-data**.
3.  En la columna **Key**, cambia el tipo a **File** y escribe `file`.
4.  En **Value**, selecciona tu archivo `registro de actividades.xlsx`.
5.  Haz clic en **Send and Download**.
