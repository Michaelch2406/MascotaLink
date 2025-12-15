package com.mjc.mascota.utils;

/**
 * Constantes centralizadas para colecciones, campos y valores de Firestore.
 * Creado para resolver issues de duplicación de código detectados por SonarQube.
 */
public final class FirestoreConstants {

    private FirestoreConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================
    // COLECCIONES DE FIRESTORE
    // ========================================
    public static final String COLLECTION_USUARIOS = "usuarios";
    public static final String COLLECTION_PASEADORES = "paseadores";
    public static final String COLLECTION_PASEADORES_SEARCH = "paseadores_search";
    public static final String COLLECTION_CHATS = "chats";
    public static final String COLLECTION_MENSAJES = "mensajes";
    public static final String COLLECTION_FAVORITOS = "favoritos";
    public static final String COLLECTION_DISPONIBILIDAD = "disponibilidad";
    public static final String COLLECTION_ZONAS_SERVICIO = "zonas_servicio";
    public static final String COLLECTION_DUENOS = "duenos";
    public static final String COLLECTION_MASCOTAS = "mascotas";
    public static final String COLLECTION_RECOMENDACIONES_IA_LOGS = "recomendaciones_ia_logs";

    // ========================================
    // CAMPOS DE DOCUMENTOS - USUARIO/PERFIL
    // ========================================
    public static final String FIELD_NOMBRE_DISPLAY = "nombre_display";
    public static final String FIELD_FOTO_PERFIL = "foto_perfil";
    public static final String FIELD_FOTO_URL = "foto_url";
    public static final String FIELD_ROL = "rol";
    public static final String FIELD_ACTIVO = "activo";
    public static final String FIELD_ESTADO = "estado";
    public static final String FIELD_EN_LINEA = "en_linea";
    public static final String FIELD_LAST_SEEN = "last_seen";
    public static final String FIELD_ACEPTA_SOLICITUDES = "acepta_solicitudes";
    public static final String FIELD_EN_PASEO = "en_paseo";

    // ========================================
    // CAMPOS DE DOCUMENTOS - PASEADOR
    // ========================================
    public static final String FIELD_CALIFICACION_PROMEDIO = "calificacion_promedio";
    public static final String FIELD_NUM_SERVICIOS_COMPLETADOS = "num_servicios_completados";
    public static final String FIELD_PRECIO_HORA = "precio_hora";
    public static final String FIELD_TARIFA_POR_HORA = "tarifa_por_hora";
    public static final String FIELD_ANOS_EXPERIENCIA = "anos_experiencia";
    public static final String FIELD_EXPERIENCIA_GENERAL = "experiencia_general";
    public static final String FIELD_VERIFICACION_ESTADO = "verificacion_estado";
    public static final String FIELD_TIPOS_PERRO_ACEPTADOS = "tipos_perro_aceptados";
    public static final String FIELD_ESPECIALIDAD_TIPO_MASCOTA = "especialidad_tipo_mascota";
    public static final String FIELD_ZONAS_PRINCIPALES = "zonas_principales";

    // ========================================
    // CAMPOS DE DOCUMENTOS - UBICACIÓN
    // ========================================
    public static final String FIELD_UBICACION_PRINCIPAL = "ubicacion_principal";
    public static final String FIELD_UBICACION_ACTUAL = "ubicacion_actual";
    public static final String FIELD_UBICACION_GEOHASH = "ubicacion_geohash";
    public static final String FIELD_UBICACION = "ubicacion";
    public static final String FIELD_DIRECCION_COORDENADAS = "direccion_coordenadas";
    public static final String FIELD_GEOPOINT = "geopoint";
    public static final String FIELD_LATITUDE = "latitude";
    public static final String FIELD_LONGITUDE = "longitude";
    public static final String FIELD_LATITUD = "latitud";
    public static final String FIELD_LONGITUD = "longitud";
    public static final String FIELD_DIRECCION = "direccion";

    // ========================================
    // CAMPOS DE DOCUMENTOS - MENSAJES/CHAT
    // ========================================
    public static final String FIELD_ID_REMITENTE = "id_remitente";
    public static final String FIELD_ID_DESTINATARIO = "id_destinatario";
    public static final String FIELD_TEXTO = "texto";
    public static final String FIELD_TIPO = "tipo";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_LEIDO = "leido";
    public static final String FIELD_ENTREGADO = "entregado";
    public static final String FIELD_IMAGEN_URL = "imagen_url";
    public static final String FIELD_ULTIMO_MENSAJE = "ultimo_mensaje";
    public static final String FIELD_ULTIMO_TIMESTAMP = "ultimo_timestamp";
    public static final String FIELD_MENSAJES_NO_LEIDOS = "mensajes_no_leidos";
    public static final String FIELD_ESTADO_USUARIOS = "estado_usuarios";
    public static final String FIELD_CHAT_ABIERTO = "chat_abierto";
    public static final String FIELD_ULTIMA_ACTIVIDAD = "ultima_actividad";
    public static final String FIELD_PARTICIPANTES = "participantes";
    public static final String FIELD_FECHA_CREACION = "fecha_creacion";
    public static final String FIELD_FECHA_ELIMINACION = "fecha_eliminacion";

    // ========================================
    // VALORES CONSTANTES - ROLES
    // ========================================
    public static final String ROLE_PASEADOR = "PASEADOR";
    public static final String ROLE_DUENO = "DUEÑO";

    // ========================================
    // VALORES CONSTANTES - ESTADOS
    // ========================================
    public static final String STATUS_APROBADO = "APROBADO";
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";
    public static final String STATUS_ESCRIBIENDO = "escribiendo";

    // ========================================
    // VALORES CONSTANTES - TIPOS DE MENSAJE
    // ========================================
    public static final String MESSAGE_TYPE_TEXTO = "texto";
    public static final String MESSAGE_TYPE_IMAGEN = "imagen";
    public static final String MESSAGE_TYPE_UBICACION = "ubicacion";

    // ========================================
    // VALORES CONSTANTES - DEFAULTS
    // ========================================
    public static final String DEFAULT_NAME = "N/A";
    public static final String DEFAULT_ZONE = "Sin zona especificada";
    public static final String DEFAULT_STATUS_TEXT = "Desconectado";
    public static final String TYPING_STATUS_TEXT = "Escribiendo...";
    public static final String ONLINE_STATUS_TEXT = "En línea";

    // ========================================
    // CAMPOS DE DOCUMENTOS - RECOMENDACIONES IA
    // ========================================
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_PASEADOR_ID = "paseadorId";
    public static final String FIELD_MATCH_SCORE = "matchScore";
    public static final String FIELD_ERROR_MSG = "errorMsg";
    public static final String FIELD_EVENTO = "evento";
    public static final String FIELD_RAZON_IA = "razon_ia";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_NOMBRE = "nombre";
    public static final String FIELD_MATCH_SCORE_LOWER = "match_score";
    public static final String FIELD_ID = "id";

    // ========================================
    // DOCUMENTOS ESPECIALES
    // ========================================
    public static final String DOCUMENT_HORARIO_DEFAULT = "horario_default";

    // ========================================
    // CAMPOS DE HORARIOS
    // ========================================
    public static final String FIELD_DISPONIBLE = "disponible";
    public static final String FIELD_HORA_INICIO = "hora_inicio";
    public static final String FIELD_HORA_FIN = "hora_fin";
    public static final String FIELD_DIAS = "dias";

    // ========================================
    // DÍAS DE LA SEMANA (lowercase para horario_default)
    // ========================================
    public static final String DAY_LUNES = "lunes";
    public static final String DAY_MARTES = "martes";
    public static final String DAY_MIERCOLES = "miercoles";
    public static final String DAY_JUEVES = "jueves";
    public static final String DAY_VIERNES = "viernes";
    public static final String DAY_SABADO = "sabado";
    public static final String DAY_DOMINGO = "domingo";

    // ========================================
    // DÍAS DE LA SEMANA (capitalizados para datos legacy)
    // ========================================
    public static final String DAY_LUNES_CAP = "Lunes";
    public static final String DAY_MARTES_CAP = "Martes";
    public static final String DAY_MIERCOLES_CAP = "Miércoles";
    public static final String DAY_MIERCOLES_ALT = "Miercoles";
    public static final String DAY_JUEVES_CAP = "Jueves";
    public static final String DAY_VIERNES_CAP = "Viernes";
    public static final String DAY_SABADO_CAP = "Sábado";
    public static final String DAY_SABADO_ALT = "Sabado";
    public static final String DAY_DOMINGO_CAP = "Domingo";
}
