package com.personal.jiralog.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para el manejo de actividades y sus identificadores en Jira.
 */
public class ActivityUtils {

    private static final Map<String, String> ACTIVITY_MAP;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("D: Administración, Seguimiento y Control", "12090");
        map.put("D: Análisis de Requerimientos, Impacto Funcional y/o Técnico", "12091");
        map.put("D: Arquitectura, Configuración Física o Ambientación", "12092");
        map.put("D: Atención a Incidencias: APS específico", "12093");
        map.put("D: Calidad y Pruebas", "12094");
        map.put("D: Ceremonias Ágiles", "12095");
        map.put("D: Desarrollo de la Solución y Codificación", "12096");
        map.put("D: Documentación", "12097");
        map.put("D: Investigación Tecnológica", "12098");
        map.put("I: Apoyo Itinerarios", "12099");
        map.put("I: Atención de Incidentes NO Escalados a Nivel 3", "12100");
        map.put("I: Auditoría", "12101");
        map.put("I: Capacitación", "12102");
        map.put("I: Capacitación Asignada", "12103");
        map.put("I: Capacitación Libre", "12104");
        map.put("I: Captura de actividades diarias", "12105");
        map.put("I: Comunicación", "12106");
        map.put("I: Desarrollo de Talento", "12107");
        map.put("I: Sin actividad", "12108");
        map.put("I: Consultoría para el Fortalecimiento de Seguridad", "14398");
        map.put("ND: Ausencias Legales", "12109");
        map.put("ND: Contrataciones y Salidas en el Mes", "12110");
        map.put("ND: Incapacidad", "12111");
        map.put("ND: Permisos / Tiempo por Tiempo", "12112");
        map.put("ND: Vacaciones", "12113");
        map.put("DS: Gestión de Respaldos", "16744");
        map.put("DS: Documentación", "16745");
        map.put("DS: Gestión de Ventanas de Mantenimiento", "16746");
        map.put("DS: Aprobación de Cambios", "16747");
        map.put("DS: Pruebas de Continuidad del Servicio", "16748");
        ACTIVITY_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Obtiene el ID de una actividad a partir de su nombre exacto.
     * @param name Nombre de la actividad.
     * @return ID de la actividad o null si no se encuentra.
     */
    public static String getActivityIdByName(String name) {
        return ACTIVITY_MAP.get(name);
    }

    /**
     * Obtiene el nombre de una actividad a partir de su ID.
     * @param id ID de la actividad.
     * @return Nombre de la actividad o null si no se encuentra.
     */
    public static String getActivityNameById(String id) {
        return ACTIVITY_MAP.entrySet().stream()
                .filter(entry -> entry.getValue().equals(id))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtiene el mapa completo de actividades (solo lectura).
     * @return Mapa de actividades.
     */
    public static Map<String, String> getActivityMap() {
        return ACTIVITY_MAP;
    }
}
