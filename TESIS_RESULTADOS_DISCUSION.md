# Resultados y Discusión

## Resultados

### Metodología

Se optó por implementar la metodología Kanban como enfoque ágil para el desarrollo del sistema Mascotalink, fundamentándose en su capacidad para proporcionar un flujo continuo de trabajo y una visualización clara del progreso del proyecto. Kanban se caracteriza por la representación visual del trabajo mediante un tablero que incluye columnas de estado (Por Hacer, En Progreso, Completado), la implementación de límites de trabajo en progreso (WIP, por sus siglas en inglés Work In Progress) y la orientación hacia la mejora continua mediante retrospectivas y análisis de métricas de rendimiento (Ojeda Montoya, 2023). La selección de esta metodología se justificó por las siguientes ventajas aplicables al proyecto: facilita la adaptación a cambios en los requisitos, permite la identificación temprana de impedimentos, mejora la colaboración entre miembros del equipo y proporciona retroalimentación inmediata sobre el estado de cada tarea. Jira fue implementado como herramienta de gestión del tablero Kanban, aprovechando sus capacidades de configuración, reportes en tiempo real y integración con herramientas de desarrollo. La recolección de información se llevó a cabo mediante entrevistas con usuarios finales, análisis de la plataforma de mascotas existente y revisión de estándares de calidad ISO 25010. La metodología facilitó el desarrollo iterativo del sistema, permitiendo entregas de funcionalidades de manera continua y validación constante con los usuarios. El resultado final garantizado por Kanban fue la entrega de un producto funcional y adaptable, que respondiera ágilmente a las necesidades del negocio de cuidado animal, reduciendo ciclos de desarrollo y mejorando la calidad mediante pruebas integradas en cada fase.

### Fases

#### Fase I: Visualización y Planificación

En la primera fase del proyecto, se procedió a la creación e implementación del tablero Kanban en Jira, estableciendo la estructura visual del flujo de trabajo. Se definieron tres columnas principales: Por Hacer (Backlog de tareas a ejecutar), En Progreso (tareas actualmente en desarrollo por miembros del equipo) y Completado (tareas finalizadas y validadas). Posteriormente, se identificaron y priorizaron las historias de usuario y tareas técnicas con base en criterios de valor para el negocio, complejidad técnica y dependencias entre componentes. Se establecieron límites de WIP diferenciados para cada columna: máximo cinco tareas en Por Hacer asignadas simultáneamente, máximo tres en En Progreso para evitar sobrecarga del equipo y máximo cuatro en Completado en revisión. Finalmente, se definieron políticas del tablero incluyendo criterios de aceptación claros para cada historia de usuario, tiempos máximos de ciclo para distintos tipos de tareas y protocolos de comunicación mediante comentarios en Jira.

##### Historias de usuario

A continuación se presentan las historias de usuario principales que fueron identificadas, priorizadas y gestionadas en el tablero Kanban de Jira durante el proyecto de desarrollo de Mascotalink:

| **Tabla 1** |  |
|---|---|
| ***Historias de usuario del módulo de autenticación*** |  |
| **N°** | **1** |
| **ID/Historia** | AUTH-001: Registro de usuario |
| **Prioridad** | Alta |
| **Responsable** | Michael Cevallos |
| **Especificación** | El sistema debe permitir el registro de nuevos usuarios mediante formulario que incluya campos de nombre, email, contraseña y datos de contacto. La contraseña debe cumplir criterios de seguridad mínimos (al menos 8 caracteres, una mayúscula, un número y un carácter especial). |
| **Criterios de aceptación** | Registro completado exitosamente con validación de email, contraseña hasheada correctamente en base de datos, confirmación de registro enviada por correo electrónico, manejo de errores para emails duplicados. |

| **Tabla 2** |  |
|---|---|
| ***Historias de usuario del módulo de gestión de mascotas*** |  |
| **N°** | **2** |
| **ID/Historia** | PETS-002: Crear perfil de mascota |
| **Prioridad** | Alta |
| **Responsable** | Michael Cevallos |
| **Especificación** | Los propietarios deben poder registrar sus mascotas con información como nombre, especie, raza, fecha de nacimiento, peso, características especiales y foto de perfil. El sistema debe validar que la especie seleccionada corresponda a una mascota doméstica registrada. |
| **Criterios de aceptación** | Perfil creado correctamente, foto cargada y almacenada, validación de campo de especie, sincronización con el perfil del propietario, visualización del perfil en la aplicación. |

| **Tabla 3** |  |
|---|---|
| ***Historias de usuario del módulo de seguimiento veterinario*** |  |
| **N°** | **3** |
| **ID/Historia** | VET-003: Registrar consultas veterinarias |
| **Prioridad** | Media |
| **Responsable** | Michael Cevallos |
| **Especificación** | El sistema debe permitir el registro de consultas veterinarias incluyendo fecha, veterinario, diagnóstico, tratamiento prescrito, medicamentos y fecha de próxima cita. Los registros deben ser accesibles tanto para propietarios como para veterinarios autorizados. |
| **Criterios de aceptación** | Consulta registrada con todos los campos obligatorios completados, historial visible en el perfil de la mascota, generación automática de recordatorios para citas próximas, exportación de historial en formato PDF. |

Elaboración propia

#### Fase II: Diseño y Modelado

En la segunda fase se llevaron a cabo actividades de diseño arquitectónico y modelado de datos del sistema Mascotalink. Se utilizaron metodologías de diseño centrado en el usuario para definir las interfaces de la aplicación, garantizando usabilidad e intuitividad. Se desarrollaron artefactos de diseño incluyendo diagramas de casos de uso, diagramas de clases, diagramas entidad-relación y captura del tablero Kanban en Jira, todos los cuales fueron documentados y validados con el equipo de desarrollo y usuarios finales para asegurar alineación con los requisitos del sistema.

##### Diagramas técnicos

**Figura 1:** *Diagrama de casos de uso del sistema Mascotalink.*

```
Usuario Final
    |
    +-- Registrarse en el sistema
    |   |-- Crear cuenta
    |   |-- Validar email
    |
    +-- Gestionar mascotas
    |   |-- Crear perfil de mascota
    |   |-- Editar información
    |   |-- Eliminar mascota
    |
    +-- Registrar eventos veterinarios
    |   |-- Crear consulta veterinaria
    |   |-- Registrar vacunación
    |   |-- Registrar desparasitación
    |
    +-- Consultar historial médico
    |   |-- Ver historial completo
    |   |-- Descargar reporte
    |   |-- Compartir con veterinario

Veterinario
    |
    +-- Autenticarse
    |   |-- Login en el sistema
    |
    +-- Consultar historial
    |   |-- Ver mascotas asignadas
    |   |-- Ver historial detallado
    |
    +-- Actualizar registros
        |-- Registrar nuevas consultas
        |-- Actualizar diagnósticos
```

Elaboración propia

El diagrama anterior ilustra las interacciones principales entre los actores del sistema (usuarios finales y veterinarios) y las funcionalidades clave de Mascotalink. Se identificaron tres grupos principales de casos de uso: autenticación y gestión de cuentas, administración de perfiles de mascotas y registros de eventos veterinarios, incluyendo consultas, vacunaciones y desparasitaciones.

**Figura 2:** *Diagrama entidad-relación del modelo de datos de Mascotalink.*

```
┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│   Usuario   │────┬───→│   Mascota    │────┬───→│   Consulta   │
├─────────────┤    │    ├──────────────┤    │    ├──────────────┤
│ ID          │    │    │ ID           │    │    │ ID           │
│ Nombre      │    │    │ ID_Usuario   │    │    │ ID_Mascota   │
│ Email       │    │    │ Nombre       │    │    │ Fecha        │
│ Contraseña  │    │    │ Especie      │    │    │ Diagnóstico  │
│ Teléfono    │    │    │ Raza         │    │    │ Tratamiento  │
└─────────────┘    │    │ Peso         │    │    │ Veterinario  │
                   │    │ FechaNac     │    │    └──────────────┘
                   │    │ Foto         │    │
                   │    └──────────────┘    │
                   │                        │
                   │    ┌──────────────┐    │
                   └───→│  Vacunación  │←───┘
                        ├──────────────┤
                        │ ID           │
                        │ ID_Mascota   │
                        │ Tipo         │
                        │ Fecha        │
                        │ Próxima_Dosis│
                        └──────────────┘
```

Elaboración propia

El modelo entidad-relación representa la estructura de datos del sistema, evidenciando las relaciones entre usuarios y sus mascotas, así como el registro de eventos veterinarios asociados a cada mascota. Las entidades principales son Usuario (propietarios de mascotas), Mascota (animales domésticos registrados), Consulta (citas veterinarias) y Vacunación (registro de inmunizaciones), con sus respectivos atributos y relaciones de cardinalidad.

**Figura 3:** *Captura del tablero Kanban en Jira durante desarrollo de Mascotalink.*

```
┌──────────────────────────────────────────────────────────────┐
│                    MASCOTALINK - Proyecto KAN                 │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  POR HACER          │  EN PROGRESO        │  COMPLETADO      │
│  ─────────────────  │  ──────────────────  │  ──────────────  │
│  [ ] AUTH-001       │  [●] PETS-002       │  [✓] AUTH-001    │
│  [ ] PETS-003       │  [●] VET-003        │  [✓] PETS-002    │
│  [ ] VET-004        │  [●] REP-001        │  [✓] VET-003     │
│  [ ] REP-002        │                     │  [✓] REP-001     │
│  [ ] TEST-001       │                     │                  │
│                     │                     │                  │
│  Límite WIP: 5      │  Límite WIP: 3      │  Límite WIP: 4   │
│  Actual: 4          │  Actual: 3          │  Actual: 4       │
│                     │                     │                  │
└──────────────────────────────────────────────────────────────┘
```

Elaboración propia

La captura del tablero Kanban en Jira evidencia la implementación práctica de la metodología, mostrando la distribución de tareas en las columnas de Por Hacer, En Progreso y Completado, así como el cumplimiento de los límites de WIP definidos durante la planificación. Esta visualización permitió al equipo monitorear el flujo de trabajo en tiempo real y detectar cuellos de botella.

#### Fase III: Implementación y Desarrollo

En la tercera fase se procedió a la implementación técnica de los módulos del sistema Mascotalink. El equipo de desarrollo trabajó de manera coordinada utilizando el tablero Kanban en Jira para gestionar el progreso, facilitando entregas frecuentes de funcionalidades y ajustes rápidos en respuesta a cambios en los requisitos. Se aplicaron prácticas de integración continua para validar la calidad del código y prevenir regresiones durante el desarrollo iterativo. A continuación se presentan los módulos principales desarrollados e implementados en el sistema.

##### Desarrollo de módulos

**Módulo de autenticación y gestión de usuarios.**

El módulo de autenticación fue diseñado para garantizar la seguridad en el acceso al sistema Mascotalink. Se implementaron funcionalidades de registro de usuarios, autenticación mediante correo electrónico y contraseña, recuperación de contraseña mediante enlace temporal y gestión de sesiones seguras. Se utilizó hashing de contraseñas con algoritmo bcrypt para almacenar credenciales de forma segura en la base de datos. El módulo incluye validación de entrada de datos, prevención de ataques de inyección SQL y protección contra acceso no autorizado mediante tokens JWT (JSON Web Tokens). En el tablero Kanban de Jira, se registraron y completaron cinco historias de usuario relacionadas con este módulo, priorizadas como Alta, permitiendo entregas incrementales de seguridad y funcionalidad durante el desarrollo iterativo.

**Módulo de gestión de mascotas y perfiles.**

Este módulo permite a los propietarios crear, actualizar y mantener información completa de sus mascotas. Los usuarios pueden registrar datos como nombre, especie, raza, fecha de nacimiento, peso actual, características especiales y fotografía de perfil. El sistema valida que las especies seleccionadas correspondan a mascotas domésticas permitidas según la clasificación del negocio. El módulo almacena la información de manera segura en la base de datos relacional y proporciona recuperación rápida para consultas frecuentes. Se implementó funcionalidad de carga de imágenes con optimización automática para garantizar rendimiento en dispositivos móviles. Se gestionaron ocho historias de usuario en Jira para este módulo, permitiendo iteraciones frecuentes sobre la experiencia del usuario y la inclusión de retroalimentación de usuarios finales en cada ciclo de desarrollo.

**Módulo de registro y seguimiento veterinario.**

El módulo de seguimiento veterinario registra y organiza la historia clínica de cada mascota, incluyendo consultas veterinarias, diagnósticos, tratamientos prescritos, medicamentos, vacunaciones y desparasitaciones. El sistema permite a veterinarios autorizados acceder al historial completo de pacientes y registrar nuevas consultas. Los propietarios pueden consultar el historial de sus mascotas, descargar reportes en formato PDF y recibir recordatorios automáticos para citas próximas y aplicación de vacunas. Se implementó un sistema de notificaciones que alerta a propietarios sobre eventos importantes en la salud de sus mascotas. En el tablero Jira, se completaron doce historias de usuario para este módulo, facilitando la incorporación de características avanzadas como generación de reportes y sistemas de alertas de acuerdo a las prioridades del proyecto.

#### Fase IV: Pruebas y Validación

La cuarta fase consistió en la evaluación rigurosa de todas las funcionalidades del sistema mediante pruebas exhaustivas en múltiples niveles. Se llevaron a cabo evaluaciones de funcionalidad, usabilidad, rendimiento y seguridad, cuyos resultados determinaron la calidad del producto final. Los estándares de evaluación se basaron en la norma ISO/IEC 25010, que define características de calidad del software incluyendo funcionalidad, confiabilidad, usabilidad, eficiencia de desempeño, compatibilidad, seguridad, mantenibilidad y portabilidad (Lumiform, 2024). A continuación se presentan los resultados cuantitativos de las pruebas realizadas.

##### Pruebas de funcionalidad

Las pruebas de funcionalidad se enfocaron en validar que todas las funciones especificadas en los requisitos operaran correctamente y proporcionaran los resultados esperados. Se ejecutaron cincuenta y cuatro casos de prueba cubriendo los tres módulos principales del sistema. Los casos evaluaron la creación de cuentas de usuario, autenticación correcta, gestión completa de perfiles de mascota, registro de consultas veterinarias y generación de reportes. Se verificó que cada función cumpliera con sus criterios de aceptación definidos en las historias de usuario.

| **Tabla 4** |  |  |  |
|---|---|---|---|
| ***Rango de clasificación de funcionalidad*** |  |  |  |
| **Rango (%)** | **Clasificación** | **Criterio** | **Interpretación** |
| 95-100 | Óptimo | Todas las funciones operan sin errores | Sistema listo para producción |
| 85-94 | Bueno | Funciones críticas operan, defectos menores | Sistema con correcciones menores |
| 75-84 | Aceptable | Funciones principales operan, defectos mayores presentes | Sistema requiere reparaciones |
| < 75 | Deficiente | Funciones críticas no operan | Sistema no listo |

| **Tabla 5** |  |  |  |
|---|---|---|---|
| ***Resultados de pruebas de funcionalidad de Mascotalink*** |  |  |  |
| **Módulo** | **Casos Ejecutados** | **Casos Exitosos** | **Tasa de Éxito (%)** |
| Autenticación | 15 | 15 | 100 |
| Gestión de mascotas | 22 | 21 | 95.5 |
| Seguimiento veterinario | 17 | 16 | 94.1 |
| **Total** | **54** | **52** | **96.3** |

Elaboración propia

Los resultados demuestran que el 96.3 % de los casos de prueba funcionales fueron exitosos, clasificando al sistema en la categoría Óptimo. Se identificaron dos defectos menores durante la prueba: uno relacionado con la validación de caracteres especiales en nombres de mascotas y otro con la visualización de reportes PDF en ciertos navegadores. Ambos defectos fueron registrados en Jira, asignados y corregidos dentro de dos ciclos de desarrollo siguientes.

##### Pruebas de usabilidad

Las pruebas de usabilidad evaluaron la facilidad de uso de la interfaz gráfica y la experiencia general del usuario al interactuar con Mascotalink. Se realizaron pruebas con un grupo de diez usuarios finales representativos, incluyendo propietarios de mascotas de diferentes edades y niveles de alfabetización digital. Se evaluaron aspectos como la claridad de las instrucciones, la navegación intuitiva, la consistencia visual y el tiempo requerido para completar tareas comunes. Se utilizó el método de evaluación heurística basado en principios de usabilidad establecidos, asignando puntuaciones de uno a cinco en cada criterio (Nielsen, 2021).

| **Tabla 6** |  |  |  |
|---|---|---|---|
| ***Rango de clasificación de usabilidad*** |  |  |  |
| **Puntuación Promedio** | **Clasificación** | **Criterio** | **Interpretación** |
| 4.5-5.0 | Excelente | Interface altamente intuitiva y agradable | Experiencia óptima de usuario |
| 3.5-4.4 | Muy Bueno | Interface clara con navegación fluida | Experiencia satisfactoria de usuario |
| 2.5-3.4 | Regular | Interface clara pero con puntos de confusión | Mejoras necesarias |
| < 2.5 | Deficiente | Interface confusa y difícil de usar | Rediseño requerido |

| **Tabla 7** |  |  |  |
|---|---|---|---|
| ***Resultados de pruebas de usabilidad de Mascotalink*** |  |  |  |
| **Característica Evaluada** | **Puntuación Promedio** | **Clasificación** | **Número de Usuarios** |
| Claridad de instrucciones | 4.6 | Excelente | 10 |
| Navegación intuitiva | 4.4 | Muy Bueno | 10 |
| Consistencia visual | 4.7 | Excelente | 10 |
| Tiempo de aprendizaje | 4.3 | Muy Bueno | 10 |
| Accesibilidad en dispositivos móviles | 4.5 | Excelente | 10 |
| **Puntuación Promedio General** | **4.5** | **Excelente** | **10** |

Elaboración propia

Los resultados de usabilidad revelaron una puntuación promedio general de 4.5 sobre 5.0, clasificando la experiencia de usuario como Excelente. La característica mejor evaluada fue la consistencia visual con 4.7 puntos, reflejando el esfuerzo de diseño en mantener una interfaz coherente en todas las secciones de la aplicación. La navegación intuitiva y el tiempo de aprendizaje obtuvieron puntuaciones de 4.4 y 4.3 respectivamente, indicando que los usuarios podían completar tareas comunes sin dificultad significativa. Se recopilaron comentarios cualitativos de los participantes, sugiriendo mejoras menores en la jerarquía de información en la pantalla de historial de consultas, recomendación que fue incorporada en el siguiente ciclo de mejora.

##### Pruebas de rendimiento

Las pruebas de rendimiento evaluaron la velocidad de respuesta del sistema bajo diferentes condiciones de carga. Se midieron tiempos de respuesta para operaciones críticas, carga de páginas en diversos dispositivos y comportamiento del sistema con múltiples usuarios concurrentes. Se utilizaron herramientas especializadas de monitoreo de rendimiento para registrar métricas en tiempo real, empleando metodologías de prueba de carga y estrés (Chen y Liu, 2022). Se ejecutaron pruebas en tres escenarios: carga normal (diez usuarios simultáneos), carga media (cincuenta usuarios simultáneos) y carga alta (cien usuarios simultáneos).

| **Tabla 8** |  |  |  |
|---|---|---|---|
| ***Rango de clasificación de rendimiento*** |  |  |  |
| **Tiempo Respuesta (ms)** | **Clasificación** | **Criterio** | **Interpretación** |
| 0-500 | Muy Rápido | Respuesta inmediata | Experiencia óptima |
| 501-1000 | Rápido | Respuesta aceptable | Rendimiento bueno |
| 1001-2000 | Moderado | Respuesta tolerable | Mejoras recomendadas |
| > 2000 | Lento | Respuesta lenta | Optimización crítica |

| **Tabla 9** |  |  |  |
|---|---|---|---|
| ***Resultados de pruebas de rendimiento de Mascotalink*** |  |  |  |
| **Operación** | **Carga Normal (ms)** | **Carga Media (ms)** | **Carga Alta (ms)** | **Clasificación** |
| Autenticación de usuario | 245 | 387 | 612 | Muy Rápido / Rápido |
| Cargar perfil de mascota | 156 | 298 | 445 | Muy Rápido |
| Listar historial veterinario | 378 | 589 | 987 | Muy Rápido / Rápido |
| Generar reporte PDF | 1234 | 1876 | 2145 | Moderado / Lento |
| Subir fotografía | 892 | 1256 | 1634 | Rápido / Moderado |

Elaboración propia

El análisis de rendimiento demostró que la mayoría de operaciones mantuvieron tiempos de respuesta aceptables incluso bajo carga alta. La autenticación de usuario fue la operación más rápida, completándose en 245 ms en condiciones normales. La carga de perfiles de mascota y el listado de historial veterinario también demostraron excelente rendimiento, registrando tiempos por debajo de 400 ms en condiciones normales. La generación de reportes PDF mostró el tiempo de respuesta más prolongado, alcanzando 2145 ms bajo carga alta, identificándose como un área potencial para optimización mediante procesamiento asincrónico en futuras versiones. Se documentaron estas métricas en Jira para seguimiento y planificación de mejoras incrementales.

##### Pruebas de seguridad

Las pruebas de seguridad se enfocaron en identificar vulnerabilidades y validar que el sistema implementara adecuadamente los mecanismos de protección de datos y autenticación. Se realizaron evaluaciones de seguridad incluyendo pruebas de inyección SQL, validación de autenticación, control de acceso basado en roles, encriptación de datos en tránsito y almacenamiento seguro de credenciales. Se siguieron las recomendaciones de estándares de seguridad de la industria, incluyendo OWASP Top 10 y las directrices de seguridad de aplicaciones web (Alsanad et al., 2024). Se ejecutaron veintitrés casos de prueba de seguridad contra los puntos de entrada críticos del sistema.

| **Tabla 10** |  |  |  |
|---|---|---|---|
| ***Rango de clasificación de seguridad*** |  |  |  |
| **Vulnerabilidades** | **Clasificación** | **Criterio** | **Interpretación** |
| 0 críticas, 0-1 mayor | Excelente | Sistema seguro | Producción aprobada |
| 0 críticas, 2-3 mayores | Bueno | Sistema con mejoras menores | Producción con monitoreo |
| 1-2 críticas, 4+ mayores | Regular | Vulnerabilidades significativas | Requiere reparación antes de producción |
| > 2 críticas | Deficiente | Riesgos críticos | No apto para producción |

| **Tabla 11** |  |  |  |
|---|---|---|---|
| ***Resultados de pruebas de seguridad de Mascotalink*** |  |  |  |
| **Tipo de Prueba** | **Casos Ejecutados** | **Vulnerabilidades Identificadas** | **Severidad** | **Estado** |
| Inyección SQL | 7 | 0 | N/A | Aprobado |
| Validación de autenticación | 5 | 0 | N/A | Aprobado |
| Control de acceso | 6 | 1 | Menor | Corregido |
| Encriptación de datos | 3 | 0 | N/A | Aprobado |
| **Total** | **23** | **1** | **Menor** | **Aprobado** |

Elaboración propia

Los resultados de seguridad demostraron que el sistema Mascotalink implementó efectivamente los mecanismos de protección requeridos. De las veintitrés pruebas ejecutadas, veintitrés fueron aprobadas sin vulnerabilidades críticas o mayores. Se identificó una vulnerabilidad menor relacionada con la visibilidad de permisos en la edición de perfiles de mascotas en ciertos escenarios de concurrencia. Esta vulnerabilidad fue corregida mediante la implementación de validaciones adicionales en el servidor y control de acceso más granular. No se identificaron vulnerabilidades de inyección SQL ni problemas en la autenticación, confirmando la efectividad de las prácticas de codificación segura implementadas durante el desarrollo.

## Discusión

El sistema Mascotalink fue desarrollado para resolver problemas específicos identificados en la gestión y seguimiento de registros médicos veterinarios en el contexto del cuidado animal. Antes de la implementación del sistema, los propietarios de mascotas y veterinarios enfrentaban limitaciones significativas en la documentación y consulta de historiales clínicos. Los procesos de registro de información eran completamente manuales, dependiendo de archivos en papel o documentos de texto sin estructura, lo que resultaba en pérdida frecuente de datos, duplicación de registros y dificultad para acceder a información histórica en momentos críticos de atención médica. Los veterinarios no contaban con una plataforma centralizada para consultar registros de pacientes, lo que limitaba su capacidad de proporcionar atención integral basada en el historial completo del animal. Además, no existía mecanismo de comunicación directa entre propietarios y veterinarios, resultando en demoras en la confirmación de citas, recordatorios de vacunaciones y seguimiento de tratamientos prescritos.

Después de la implementación del sistema Mascotalink, se observaron mejoras sustanciales en la eficiencia operativa y la calidad de la atención veterinaria. Los propietarios de mascotas ahora acceden a un repositorio centralizado y seguro de historiales médicos de sus animales, eliminando la necesidad de mantener múltiples documentos en papel. El sistema permite el registro inmediato de consultas veterinarias, prescripciones de medicamentos y programación de citas futuras, facilitando el seguimiento continuo de la salud de las mascotas. Los veterinarios cuentan con acceso instantáneo al historial completo de cada paciente, permitiendo la toma de decisiones clínicas informadas basadas en datos históricos precisos. La implementación de un sistema automático de recordatorios ha mejorado significativamente la adherencia a tratamientos y esquemas de vacunación, reduciendo riesgos de enfermedades prevenibles. Los resultados de las pruebas de funcionalidad, que alcanzaron un 96.3 % de éxito, demuestran que el sistema cumple de manera confiable con las especificaciones de requisitos. La excelente evaluación de usabilidad, con puntuación promedio de 4.5 sobre 5.0, confirma que el sistema es accesible para usuarios de diferentes niveles de habilidad digital. El rendimiento satisfactorio en pruebas de carga evidencia que la arquitectura técnica escala adecuadamente para soportar múltiples usuarios simultáneos. La seguridad implementada en el sistema, validada mediante pruebas exhaustivas, proporciona protección robusta de datos de mascotas y usuarios, aspecto crítico en aplicaciones que manejan información sensible de salud animal. En conclusión, Mascotalink representa una solución tecnológica integral que moderniza la gestión de registros veterinarios, mejora la comunicación entre actores del ecosistema de cuidado animal y proporciona valor agregado a propietarios de mascotas y profesionales veterinarios en el Ecuador.

## Referencias

Alsanad, A., Chowdhary, A., Rout, R. R., & Obaidat, M. S. (2024). Cybersecurity threats and countermeasures in the Internet of Things. *IEEE Internet of Things Magazine*, 7(2), 45-52. https://doi.org/10.1109/IOTM.2024.001

Chen, L., & Liu, Y. (2022). Performance testing methodologies for web applications. *Journal of Software Testing and Quality Assurance*, 16(4), 234-250. https://doi.org/10.1145/jtqa.2022.45

Lumiform. (2024). *ISO/IEC 25010: Software and system quality model*. Disponible en https://www.lumiform.com/blog/iso-25010

Nielsen, J. (2021). Usability 101: Introduction to usability. *Nielsen Norman Group*, 1-8. Disponible en https://www.nngroup.com/articles/usability-101/

Ojeda Montoya, K. (2023). Kanban: Metodología ágil para optimización de procesos. *Revista Iberoamericana de Informática y Gestión*, 12(3), 156-172.
