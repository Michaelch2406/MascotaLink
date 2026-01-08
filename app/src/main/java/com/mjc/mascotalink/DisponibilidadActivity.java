package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascotalink.adapters.ConfiguracionesAdapter;
import com.mjc.mascotalink.fragments.DialogBloquearDiasFragment;
import com.mjc.mascotalink.fragments.DialogHorarioDefaultFragment;
import com.mjc.mascotalink.fragments.DialogHorarioEspecialFragment;
import com.mjc.mascotalink.modelo.Bloqueo;
import com.mjc.mascotalink.modelo.HorarioEspecial;
import com.mjc.mascotalink.utils.CalendarioUtils;
import com.mjc.mascotalink.utils.InputUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Activity para gestionar la disponibilidad completa del paseador
 * Incluye: calendario visual, horarios por defecto, bloqueos y horarios especiales
 */
public class DisponibilidadActivity extends AppCompatActivity {

    private static final String TAG = "DisponibilidadActivity";
    private static final String PREFS = "WizardPaseador";

    // UI Components
    private GridView gridCalendario;
    private TextView tvMesActual;
    private TextView btnMesAnterior, btnMesSiguiente;
    private MaterialCardView cardHorarioEstandar, cardBloquearDias, cardHorariosEspeciales;
    private TextView tvHorarioEstandarDesc;
    private RecyclerView rvConfiguracionesActivas;
    private TextView tvSinConfiguraciones;

    // Adapters
    private CalendarioAdapter calendarioAdapter;
    private ConfiguracionesAdapter configuracionesAdapter;

    // Data
    private Calendar mesActual;
    private Set<Date> diasDisponibles = new HashSet<>();
    private Set<Date> diasBloqueados = new HashSet<>();
    private Set<Date> diasParciales = new HashSet<>();

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private List<ListenerRegistration> realtimeListeners = new ArrayList<>();

    // Registration Mode
    private boolean esRegistroMode = false;
    private android.content.SharedPreferences encryptedPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar EncryptedPreferences
        try {
            encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                PREFS,
                androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
                this,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error al crear EncryptedPreferences, usando SharedPreferences normal", e);
            encryptedPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        }

        // Detectar si está en modo registro o edición
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
            esRegistroMode = false;
        } else {
            currentUserId = null;
            esRegistroMode = true;
        }

        mesActual = Calendar.getInstance();

        setupViews();
        setupCalendario();
        setupTarjetasAccion();
        setupConfiguracionesActivas();
        cargarDatosDisponibilidad();
    }

    private void setupViews() {
        // Back button
        View ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        // Calendario
        gridCalendario = findViewById(R.id.gridCalendario);
        tvMesActual = findViewById(R.id.tvMesActual);
        btnMesAnterior = findViewById(R.id.btnMesAnterior);
        btnMesSiguiente = findViewById(R.id.btnMesSiguiente);

        // Tarjetas
        cardHorarioEstandar = findViewById(R.id.cardHorarioEstandar);
        cardBloquearDias = findViewById(R.id.cardBloquearDias);
        cardHorariosEspeciales = findViewById(R.id.cardHorariosEspeciales);
        tvHorarioEstandarDesc = findViewById(R.id.tvHorarioEstandarDesc);

        // Lista de configuraciones
        rvConfiguracionesActivas = findViewById(R.id.rvConfiguracionesActivas);
        tvSinConfiguraciones = findViewById(R.id.tvSinConfiguraciones);
    }

    private void setupCalendario() {
        // Navegación de mes con SafeClickListener para evitar clicks múltiples
        btnMesAnterior.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            mesActual.add(Calendar.MONTH, -1);
            actualizarCalendario();
        }));

        btnMesSiguiente.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            mesActual.add(Calendar.MONTH, 1);
            actualizarCalendario();
        }));

        actualizarCalendario();
    }

    private void actualizarCalendario() {
        // Actualizar título del mes
        String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        tvMesActual.setText(meses[mesActual.get(Calendar.MONTH)] + " " + mesActual.get(Calendar.YEAR));

        // Generar lista de fechas para el calendario
        List<Date> fechasDelMes = generarFechasDelMes(mesActual);

        // Configurar adapter
        calendarioAdapter = new CalendarioAdapter(this, fechasDelMes, mesActual, (date, position) -> {
            // Callback cuando se selecciona una fecha
            mostrarDetalleDelDia(date);
        });
        calendarioAdapter.setEsVistaPaseador(true); // Es vista de paseador
        calendarioAdapter.setDiasBloqueados(diasBloqueados);
        calendarioAdapter.setDiasParciales(diasParciales);

        gridCalendario.setAdapter(calendarioAdapter);

        // Recargar datos de disponibilidad para el mes actual
        cargarDisponibilidadDelMes();
    }

    private List<Date> generarFechasDelMes(Calendar mes) {
        return CalendarioUtils.generarFechasDelMes(mes);
    }

    private void mostrarDetalleDelDia(Date fecha) {
        if (fecha == null) {
            Toast.makeText(this, "Error: Fecha no válida", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId == null && !esRegistroMode) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Normalizar fecha para comparación
        Date fechaNormalizada = normalizarFecha(fecha);

        // Determinar estado del día
        String estadoDia;
        int colorEstado;
        if (diasBloqueados.contains(fechaNormalizada)) {
            estadoDia = "Bloqueado (día completo)";
            colorEstado = getResources().getColor(R.color.calendario_bloqueado);
        } else if (diasParciales.contains(fechaNormalizada)) {
            estadoDia = "Bloqueado parcialmente";
            colorEstado = getResources().getColor(R.color.calendario_parcial);
        } else {
            estadoDia = "Disponible";
            colorEstado = getResources().getColor(R.color.calendario_disponible);
        }

        // Crear diálogo con opciones
        String fechaFormateada = formatearFecha(fecha);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Gestionar " + fechaFormateada);

        // Crear vista personalizada para mostrar el estado
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        TextView tvEstado = new TextView(this);
        tvEstado.setText("Estado: " + estadoDia);
        tvEstado.setTextSize(16);
        tvEstado.setTextColor(colorEstado);
        tvEstado.setPadding(0, 0, 0, 20);
        layout.addView(tvEstado);

        builder.setView(layout);

        // Opciones
        String[] opciones = {
            "Configurar horario especial",
            "Bloquear día completo",
            "Bloquear solo mañana",
            "Bloquear solo tarde",
            "Ver configuraciones activas"
        };

        builder.setItems(opciones, (dialog, which) -> {
            switch (which) {
                case 0: // Horario especial
                    DialogHorarioEspecialFragment horarioDialog =
                        DialogHorarioEspecialFragment.newInstance(currentUserId, fecha);
                    horarioDialog.setOnHorarioEspecialGuardadoListener(() -> {
                        cargarDatosDisponibilidad();
                        Toast.makeText(this, "Horario especial guardado", Toast.LENGTH_SHORT).show();
                    });
                    horarioDialog.show(getSupportFragmentManager(), "horario_especial");
                    break;

                case 1: // Bloquear día completo
                    bloquearDiaRapido(fecha, Bloqueo.TIPO_DIA_COMPLETO);
                    break;

                case 2: // Bloquear mañana
                    bloquearDiaRapido(fecha, Bloqueo.TIPO_MANANA);
                    break;

                case 3: // Bloquear tarde
                    bloquearDiaRapido(fecha, Bloqueo.TIPO_TARDE);
                    break;

                case 4: // Ver configuraciones
                    // Scroll a la lista de configuraciones
                    rvConfiguracionesActivas.smoothScrollToPosition(0);
                    break;
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void bloquearDiaRapido(Date fecha, String tipo) {
        if (fecha == null) return;

        if (esRegistroMode) {
            // Modo registro: guardar a EncryptedPreferences
            guardarBloqueoEnPrefs(fecha, tipo);
        } else {
            // Modo edición: guardar a Firestore
            if (currentUserId == null) return;

            Bloqueo bloqueo = new Bloqueo();
            bloqueo.setFecha(new Timestamp(fecha));
            bloqueo.setTipo(tipo);
            bloqueo.setRazon("Bloqueado desde calendario");
            bloqueo.setRepetir(false);
            bloqueo.setActivo(true);

            db.collection("paseadores").document(currentUserId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .add(bloqueo)
                .addOnSuccessListener(documentReference -> {
                    String mensaje = tipo.equals(Bloqueo.TIPO_DIA_COMPLETO)
                        ? "Día bloqueado completamente"
                        : "Bloqueado " + (tipo.equals(Bloqueo.TIPO_MANANA) ? "mañana" : "tarde");
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
                    cargarDatosDisponibilidad();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al bloquear: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void guardarBloqueoEnPrefs(Date fecha, String tipo) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();

            // Leer bloqueos existentes
            String bloqueosJson = encryptedPrefs.getString("disponibilidad_bloqueos", "[]");
            java.util.List<Map<String, Object>> bloqueos = gson.fromJson(bloqueosJson, java.util.List.class);

            // Null-check de seguridad
            if (bloqueos == null) {
                bloqueos = new ArrayList<>();
            }

            // Crear nuevo bloqueo
            Map<String, Object> nuevoBloqueo = new HashMap<>();
            nuevoBloqueo.put("fecha", fecha.getTime());
            nuevoBloqueo.put("tipo", tipo);
            nuevoBloqueo.put("razon", "Bloqueado desde calendario");
            nuevoBloqueo.put("repetir", false);
            nuevoBloqueo.put("activo", true);

            bloqueos.add(nuevoBloqueo);

            // Guardar a EncryptedPreferences
            android.content.SharedPreferences.Editor editor = encryptedPrefs.edit();
            editor.putString("disponibilidad_bloqueos", gson.toJson(bloqueos));
            editor.apply();

            String mensaje = tipo.equals(Bloqueo.TIPO_DIA_COMPLETO)
                ? "Día bloqueado completamente"
                : "Bloqueado " + (tipo.equals(Bloqueo.TIPO_MANANA) ? "mañana" : "tarde");
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
            cargarDatosDisponibilidad();
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar bloqueo en prefs", e);
            Toast.makeText(this, "Error al guardar bloqueo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupTarjetasAccion() {
        // Tarjeta 1: Horario Estándar con SafeClickListener
        cardHorarioEstandar.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            if (currentUserId == null && !esRegistroMode) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            String uid = esRegistroMode ? null : currentUserId;
            DialogHorarioDefaultFragment dialog = DialogHorarioDefaultFragment.newInstance(uid, esRegistroMode);
            dialog.setOnHorarioGuardadoListener(() -> {
                cargarDatosDisponibilidad();
                Toast.makeText(this, "Horario por defecto actualizado", Toast.LENGTH_SHORT).show();
            });
            dialog.show(getSupportFragmentManager(), "horario_default");
        }));

        // Tarjeta 2: Bloquear Días con SafeClickListener
        cardBloquearDias.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            if (currentUserId == null && !esRegistroMode) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            if (esRegistroMode) {
                Toast.makeText(this, "Nota: Estos cambios se guardarán temporalmente. Después del registro podrá editarlos.", Toast.LENGTH_LONG).show();
            }
            String uid = esRegistroMode ? null : currentUserId;
            DialogBloquearDiasFragment dialog = DialogBloquearDiasFragment.newInstance(uid);
            dialog.setOnDiasBloqueadosListener(cantidad -> {
                cargarDatosDisponibilidad();
            });
            dialog.show(getSupportFragmentManager(), "bloquear_dias");
        }));

        // Tarjeta 3: Horarios Especiales con SafeClickListener
        cardHorariosEspeciales.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            if (currentUserId == null && !esRegistroMode) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            if (esRegistroMode) {
                Toast.makeText(this, "Nota: Estos cambios se guardarán temporalmente. Después del registro podrá editarlos.", Toast.LENGTH_LONG).show();
            }
            String uid = esRegistroMode ? null : currentUserId;
            // Mostrar diálogo para seleccionar fecha primero
            DialogHorarioEspecialFragment dialog = DialogHorarioEspecialFragment.newInstance(uid, null);
            dialog.setOnHorarioEspecialGuardadoListener(this::cargarDatosDisponibilidad);
            dialog.show(getSupportFragmentManager(), "horario_especial");
        }));
    }

    private void setupConfiguracionesActivas() {
        rvConfiguracionesActivas.setLayoutManager(new LinearLayoutManager(this));
        configuracionesAdapter = new ConfiguracionesAdapter(this);
        rvConfiguracionesActivas.setAdapter(configuracionesAdapter);
    }

    private void cargarDatosDisponibilidad() {
        if (currentUserId == null && !esRegistroMode) return;

        // Cargar horario por defecto
        cargarHorarioDefault();

        // Cargar bloqueos y horarios especiales
        cargarConfiguracionesActivas();

        // Cargar disponibilidad del mes actual en calendario
        cargarDisponibilidadDelMes();

        // Configurar listeners en tiempo real para detectar cambios (solo en modo edición)
        if (!esRegistroMode) {
            setupRealtimeListeners();
        }
    }

    private void cargarHorarioDefault() {
        if (tvHorarioEstandarDesc == null) return;

        if (esRegistroMode) {
            cargarHorarioDefaultDesdePrefs();
        } else {
            if (currentUserId == null) return;

            db.collection("paseadores").document(currentUserId)
                    .collection("disponibilidad").document("horario_default")
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Extraer información para mostrar en la tarjeta
                            Map<String, Object> data = documentSnapshot.getData();
                            if (data != null && tvHorarioEstandarDesc != null) {
                                String desc = construirDescripcionHorario(data);
                                tvHorarioEstandarDesc.setText(desc);
                            }
                        } else {
                            if (tvHorarioEstandarDesc != null) {
                                tvHorarioEstandarDesc.setText("No configurado");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (tvHorarioEstandarDesc != null) {
                            tvHorarioEstandarDesc.setText("Error al cargar");
                        }
                    });
        }
    }

    private void cargarHorarioDefaultDesdePrefs() {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String horarioJson = encryptedPrefs.getString("disponibilidad_horario_default", null);

            if (horarioJson != null && tvHorarioEstandarDesc != null) {
                Map<String, Object> data = gson.fromJson(horarioJson, Map.class);
                if (data != null) {
                    String desc = construirDescripcionHorario(data);
                    tvHorarioEstandarDesc.setText(desc);
                }
            } else if (tvHorarioEstandarDesc != null) {
                tvHorarioEstandarDesc.setText("No configurado");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al cargar horario default desde prefs", e);
            if (tvHorarioEstandarDesc != null) {
                tvHorarioEstandarDesc.setText("Error al cargar");
            }
        }
    }

    private String construirDescripcionHorario(Map<String, Object> data) {
        // Construir descripción legible del horario dinámicamente
        String[] diasKeys = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};
        String[] diasAbrev = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

        List<String> diasDisponibles = new ArrayList<>();
        String horaInicio = null;
        String horaFin = null;

        // Recopilar días disponibles y horarios
        for (int i = 0; i < diasKeys.length; i++) {
            Map<String, Object> diaData = (Map<String, Object>) data.get(diasKeys[i]);
            if (diaData != null && Boolean.TRUE.equals(diaData.get("disponible"))) {
                diasDisponibles.add(diasAbrev[i]);
                if (horaInicio == null) {
                    horaInicio = (String) diaData.get("hora_inicio");
                    horaFin = (String) diaData.get("hora_fin");
                }
            }
        }

        if (diasDisponibles.isEmpty()) {
            return "Sin configurar";
        }

        // Intentar agrupar días consecutivos
        String diasTexto = agruparDiasConsecutivos(diasDisponibles, diasAbrev);
        return diasTexto + ": " + horaInicio + " - " + horaFin;
    }

    /**
     * Agrupa días consecutivos para mostrar rangos como "Lun-Vie" en lugar de "Lun, Mar, Mié, Jue, Vie"
     */
    private String agruparDiasConsecutivos(List<String> diasDisponibles, String[] diasAbrev) {
        if (diasDisponibles.isEmpty()) return "";
        if (diasDisponibles.size() == 1) return diasDisponibles.get(0);

        // Si son exactamente Lun-Vie, mostrar como rango
        if (diasDisponibles.size() == 5 &&
            diasDisponibles.contains("Lun") && diasDisponibles.contains("Mar") &&
            diasDisponibles.contains("Mié") && diasDisponibles.contains("Jue") &&
            diasDisponibles.contains("Vie")) {
            return "Lun-Vie";
        }

        // Si son todos los días, mostrar "Todos los días"
        if (diasDisponibles.size() == 7) {
            return "Todos los días";
        }

        // Si son Lun-Sáb
        if (diasDisponibles.size() == 6 && !diasDisponibles.contains("Dom")) {
            return "Lun-Sáb";
        }

        // En otros casos, simplemente unir con comas
        return String.join(", ", diasDisponibles);
    }

    private void cargarConfiguracionesActivas() {
        if (esRegistroMode) {
            cargarConfiguracionesActivasDesdePrefs();
        } else {
            if (currentUserId == null) return;

            List<ConfiguracionesAdapter.ConfiguracionItem> items = new ArrayList<>();

            // Cargar bloqueos próximos (próximos 30 días)
            Calendar hoy = Calendar.getInstance();
            hoy.set(Calendar.HOUR_OF_DAY, 0);
            hoy.set(Calendar.MINUTE, 0);
            hoy.set(Calendar.SECOND, 0);
            Timestamp inicioRango = new Timestamp(hoy.getTime());

            Calendar fin30Dias = (Calendar) hoy.clone();
            fin30Dias.add(Calendar.DAY_OF_MONTH, 30);
            Timestamp finRango = new Timestamp(fin30Dias.getTime());

            db.collection("paseadores").document(currentUserId)
                    .collection("disponibilidad").document("bloqueos")
                    .collection("items")
                    .whereGreaterThanOrEqualTo("fecha", inicioRango)
                    .whereLessThanOrEqualTo("fecha", finRango)
                    .whereEqualTo("activo", true)
                    .orderBy("fecha")
                    .limit(10)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Bloqueo bloqueo = doc.toObject(Bloqueo.class);
                            if (bloqueo != null && bloqueo.getFecha() != null) {
                                String titulo = formatearFecha(bloqueo.getFecha().toDate()) + " - Bloqueado";
                                String desc = bloqueo.getDescripcion() != null ? bloqueo.getDescripcion() : "Sin descripción";
                                items.add(new ConfiguracionesAdapter.ConfiguracionItem(
                                        doc.getId(), titulo, desc, "bloqueo"));
                            }
                        }

                        // Cargar horarios especiales
                        cargarHorariosEspeciales(items);
                    });
        }
    }

    private void cargarConfiguracionesActivasDesdePrefs() {
        try {
            List<ConfiguracionesAdapter.ConfiguracionItem> items = new ArrayList<>();
            com.google.gson.Gson gson = new com.google.gson.Gson();

            // Cargar bloqueos
            String bloqueosJson = encryptedPrefs.getString("disponibilidad_bloqueos", "[]");
            java.util.List<Map<String, Object>> bloqueos = gson.fromJson(bloqueosJson, java.util.List.class);

            if (bloqueos != null) {
                int count = 0;
                for (Map<String, Object> bloqueo : bloqueos) {
                    if (count >= 10) break; // Limitar a 10 items

                    try {
                        Double fechaMs = (Double) bloqueo.get("fecha");
                        Boolean activo = (Boolean) bloqueo.get("activo");

                        if (fechaMs != null && Boolean.TRUE.equals(activo)) {
                            Date fecha = new Date(fechaMs.longValue());
                            String titulo = formatearFecha(fecha) + " - Bloqueado";
                            String razon = (String) bloqueo.get("razon");
                            String desc = razon != null ? razon : "Sin descripción";
                            items.add(new ConfiguracionesAdapter.ConfiguracionItem(
                                    String.valueOf(count), titulo, desc, "bloqueo"));
                            count++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar bloqueo desde prefs", e);
                    }
                }
            }

            // Cargar horarios especiales desde EncryptedPreferences
            cargarHorariosEspecialesDesdePrefs(items);
        } catch (Exception e) {
            Log.e(TAG, "Error al cargar configuraciones activas desde prefs", e);
        }
    }

    private void cargarHorariosEspecialesDesdePrefs(List<ConfiguracionesAdapter.ConfiguracionItem> items) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String horariosJson = encryptedPrefs.getString("disponibilidad_horarios_especiales", "[]");
            java.util.List<Map<String, Object>> horarios = gson.fromJson(horariosJson, java.util.List.class);

            if (horarios != null) {
                int count = 0;
                for (Map<String, Object> horario : horarios) {
                    if (items.size() >= 10) break; // Limitar total a 10 items

                    try {
                        Double fechaMs = (Double) horario.get("fecha");
                        Boolean activo = (Boolean) horario.get("activo");
                        String horaInicio = (String) horario.get("hora_inicio");
                        String horaFin = (String) horario.get("hora_fin");
                        String nota = (String) horario.get("nota");

                        if (fechaMs != null && Boolean.TRUE.equals(activo) && horaInicio != null && horaFin != null) {
                            Date fecha = new Date(fechaMs.longValue());
                            String titulo = formatearFecha(fecha);
                            String desc = horaInicio + " - " + horaFin;
                            if (nota != null && !nota.isEmpty()) {
                                desc += " (" + nota + ")";
                            }
                            items.add(new ConfiguracionesAdapter.ConfiguracionItem(
                                    String.valueOf(count), titulo, desc, "horario_especial"));
                            count++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar horario especial desde prefs", e);
                    }
                }
            }

            // Actualizar adapter
            if (configuracionesAdapter != null) {
                configuracionesAdapter.setItems(items);
            }
            if (tvSinConfiguraciones != null && rvConfiguracionesActivas != null) {
                if (items.isEmpty()) {
                    tvSinConfiguraciones.setVisibility(View.VISIBLE);
                    rvConfiguracionesActivas.setVisibility(View.GONE);
                } else {
                    tvSinConfiguraciones.setVisibility(View.GONE);
                    rvConfiguracionesActivas.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al cargar horarios especiales desde prefs", e);
        }
    }

    private void cargarHorariosEspeciales(List<ConfiguracionesAdapter.ConfiguracionItem> items) {
        if (currentUserId == null || items == null) return;

        Calendar hoy = Calendar.getInstance();
        hoy.set(Calendar.HOUR_OF_DAY, 0);
        hoy.set(Calendar.MINUTE, 0);
        hoy.set(Calendar.SECOND, 0);
        Timestamp inicioRango = new Timestamp(hoy.getTime());

        db.collection("paseadores").document(currentUserId)
                .collection("disponibilidad").document("horarios_especiales")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioRango)
                .whereEqualTo("activo", true)
                .orderBy("fecha")
                .limit(10)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        HorarioEspecial horario = doc.toObject(HorarioEspecial.class);
                        if (horario != null && horario.getFecha() != null &&
                            horario.getHora_inicio() != null && horario.getHora_fin() != null) {
                            String titulo = formatearFecha(horario.getFecha().toDate());
                            String desc = horario.getHora_inicio() + " - " + horario.getHora_fin();
                            if (horario.getNota() != null && !horario.getNota().isEmpty()) {
                                desc += " (" + horario.getNota() + ")";
                            }
                            items.add(new ConfiguracionesAdapter.ConfiguracionItem(
                                    doc.getId(), titulo, desc, "horario_especial"));
                        }
                    }

                    // Actualizar adapter
                    if (configuracionesAdapter != null) {
                        configuracionesAdapter.setItems(items);
                    }
                    if (tvSinConfiguraciones != null && rvConfiguracionesActivas != null) {
                        if (items.isEmpty()) {
                            tvSinConfiguraciones.setVisibility(View.VISIBLE);
                            rvConfiguracionesActivas.setVisibility(View.GONE);
                        } else {
                            tvSinConfiguraciones.setVisibility(View.GONE);
                            rvConfiguracionesActivas.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void cargarDisponibilidadDelMes() {
        // Limpiar sets
        diasDisponibles.clear();
        diasBloqueados.clear();
        diasParciales.clear();

        if (esRegistroMode) {
            // Modo registro: cargar de EncryptedPreferences
            cargarDisponibilidadDelMesDesdePrefs();
        } else {
            if (currentUserId == null) return;

            // PASO 1: Cargar horario por defecto primero
            db.collection("paseadores").document(currentUserId)
                    .collection("disponibilidad").document("horario_default")
                    .get()
                    .addOnSuccessListener(horarioDoc -> {
                        // Marcar días disponibles según horario estándar
                        if (horarioDoc.exists()) {
                            marcarDiasDisponiblesSegunHorario(horarioDoc);
                        }

                        // PASO 2: Cargar bloqueos del mes
                        cargarBloqueosDelMes();
                    })
                    .addOnFailureListener(e -> {
                        // Si falla, al menos intentar cargar bloqueos
                        cargarBloqueosDelMes();
                    });
        }
    }

    private void cargarDisponibilidadDelMesDesdePrefs() {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String horarioJson = encryptedPrefs.getString("disponibilidad_horario_default", null);

            if (horarioJson != null) {
                Map<String, Object> horarioData = gson.fromJson(horarioJson, Map.class);
                marcarDiasDisponiblesSegunHorarioPrefs(horarioData);
            }

            // Cargar bloqueos de EncryptedPreferences
            String bloqueosJson = encryptedPrefs.getString("disponibilidad_bloqueos", "[]");
            java.util.List<Map<String, Object>> bloqueos = gson.fromJson(bloqueosJson, java.util.List.class);

            if (bloqueos != null) {
                for (Map<String, Object> bloqueo : bloqueos) {
                    try {
                        Double fechaMs = (Double) bloqueo.get("fecha");
                        String tipo = (String) bloqueo.get("tipo");
                        Boolean activo = (Boolean) bloqueo.get("activo");

                        if (fechaMs != null && tipo != null && Boolean.TRUE.equals(activo)) {
                            Date fecha = new Date(fechaMs.longValue());
                            Date fechaNormalizada = normalizarFecha(fecha);

                            if (Bloqueo.TIPO_DIA_COMPLETO.equals(tipo)) {
                                diasBloqueados.add(fechaNormalizada);
                                diasDisponibles.remove(fechaNormalizada);
                            } else {
                                diasParciales.add(fechaNormalizada);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar bloqueo desde prefs", e);
                    }
                }
            }

            // Actualizar calendario
            if (calendarioAdapter != null) {
                calendarioAdapter.setDiasDisponibles(diasDisponibles);
                calendarioAdapter.setDiasBloqueados(diasBloqueados);
                calendarioAdapter.setDiasParciales(diasParciales);
                calendarioAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al cargar disponibilidad del mes desde prefs", e);
        }
    }

    private void marcarDiasDisponiblesSegunHorarioPrefs(Map<String, Object> horarioData) {
        try {
            Map<Integer, Boolean> diasLaborales = new HashMap<>();
            diasLaborales.put(Calendar.MONDAY, extraerDisponibilidad(horarioData, "lunes"));
            diasLaborales.put(Calendar.TUESDAY, extraerDisponibilidad(horarioData, "martes"));
            diasLaborales.put(Calendar.WEDNESDAY, extraerDisponibilidad(horarioData, "miercoles"));
            diasLaborales.put(Calendar.THURSDAY, extraerDisponibilidad(horarioData, "jueves"));
            diasLaborales.put(Calendar.FRIDAY, extraerDisponibilidad(horarioData, "viernes"));
            diasLaborales.put(Calendar.SATURDAY, extraerDisponibilidad(horarioData, "sabado"));
            diasLaborales.put(Calendar.SUNDAY, extraerDisponibilidad(horarioData, "domingo"));

            // Marcar cada día del mes según configuración
            Calendar cal = (Calendar) mesActual.clone();
            int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            for (int dia = 1; dia <= diasDelMes; dia++) {
                cal.set(Calendar.DAY_OF_MONTH, dia);
                int diaSemana = cal.get(Calendar.DAY_OF_WEEK);
                Boolean estaDisponible = diasLaborales.get(diaSemana);

                if (estaDisponible != null && estaDisponible) {
                    diasDisponibles.add(normalizarFecha(cal.getTime()));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al marcar días disponibles desde prefs", e);
        }
    }

    private Boolean extraerDisponibilidad(Map<String, Object> horarioData, String dia) {
        try {
            Map<String, Object> diaData = (Map<String, Object>) horarioData.get(dia);
            if (diaData != null) {
                Object disponible = diaData.get("disponible");
                return disponible instanceof Boolean ? (Boolean) disponible : false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al extraer disponibilidad para " + dia, e);
        }
        return false;
    }

    private void marcarDiasDisponiblesSegunHorario(DocumentSnapshot horarioDoc) {
        // Obtener configuración de días de la semana
        Map<Integer, Boolean> diasLaborales = new HashMap<>();
        diasLaborales.put(Calendar.MONDAY, horarioDoc.get("lunes.disponible") != null && (Boolean) horarioDoc.get("lunes.disponible"));
        diasLaborales.put(Calendar.TUESDAY, horarioDoc.get("martes.disponible") != null && (Boolean) horarioDoc.get("martes.disponible"));
        diasLaborales.put(Calendar.WEDNESDAY, horarioDoc.get("miercoles.disponible") != null && (Boolean) horarioDoc.get("miercoles.disponible"));
        diasLaborales.put(Calendar.THURSDAY, horarioDoc.get("jueves.disponible") != null && (Boolean) horarioDoc.get("jueves.disponible"));
        diasLaborales.put(Calendar.FRIDAY, horarioDoc.get("viernes.disponible") != null && (Boolean) horarioDoc.get("viernes.disponible"));
        diasLaborales.put(Calendar.SATURDAY, horarioDoc.get("sabado.disponible") != null && (Boolean) horarioDoc.get("sabado.disponible"));
        diasLaborales.put(Calendar.SUNDAY, horarioDoc.get("domingo.disponible") != null && (Boolean) horarioDoc.get("domingo.disponible"));

        // Marcar cada día del mes según configuración
        Calendar cal = (Calendar) mesActual.clone();
        int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int dia = 1; dia <= diasDelMes; dia++) {
            cal.set(Calendar.DAY_OF_MONTH, dia);
            int diaSemana = cal.get(Calendar.DAY_OF_WEEK);
            Boolean estaDisponible = diasLaborales.get(diaSemana);

            if (estaDisponible != null && estaDisponible) {
                diasDisponibles.add(normalizarFecha(cal.getTime()));
            }
        }
    }

    private void cargarBloqueosDelMes() {
        // Calcular rango del mes actual
        Calendar inicioMes = (Calendar) mesActual.clone();
        inicioMes.set(Calendar.DAY_OF_MONTH, 1);
        inicioMes.set(Calendar.HOUR_OF_DAY, 0);
        inicioMes.set(Calendar.MINUTE, 0);
        inicioMes.set(Calendar.SECOND, 0);

        Calendar finMes = (Calendar) mesActual.clone();
        finMes.set(Calendar.DAY_OF_MONTH, mesActual.getActualMaximum(Calendar.DAY_OF_MONTH));
        finMes.set(Calendar.HOUR_OF_DAY, 23);
        finMes.set(Calendar.MINUTE, 59);
        finMes.set(Calendar.SECOND, 59);

        Timestamp inicioRango = new Timestamp(inicioMes.getTime());
        Timestamp finRango = new Timestamp(finMes.getTime());

        // Cargar bloqueos del mes
        db.collection("paseadores").document(currentUserId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioRango)
                .whereLessThanOrEqualTo("fecha", finRango)
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Bloqueo bloqueo = doc.toObject(Bloqueo.class);
                        if (bloqueo != null && bloqueo.getFecha() != null && bloqueo.getTipo() != null) {
                            Date fecha = bloqueo.getFecha().toDate();
                            Date fechaNormalizada = normalizarFecha(fecha);
                            if (fechaNormalizada != null) {
                                if (Bloqueo.TIPO_DIA_COMPLETO.equals(bloqueo.getTipo())) {
                                    diasBloqueados.add(fechaNormalizada);
                                    diasDisponibles.remove(fechaNormalizada); // Quitar de disponibles
                                } else {
                                    diasParciales.add(fechaNormalizada);
                                }
                            }
                        }
                    }

                    // Actualizar calendario
                    if (calendarioAdapter != null) {
                        calendarioAdapter.setDiasDisponibles(diasDisponibles);
                        calendarioAdapter.setDiasBloqueados(diasBloqueados);
                        calendarioAdapter.setDiasParciales(diasParciales);
                        calendarioAdapter.notifyDataSetChanged();
                    }
                });
    }

    private Date normalizarFecha(Date fecha) {
        return CalendarioUtils.normalizarFecha(fecha);
    }

    private String formatearFecha(Date fecha) {
        if (fecha == null) return "Fecha no disponible";

        SimpleDateFormat sdf = new SimpleDateFormat("EEE d 'de' MMM", new Locale("es", "ES"));
        return sdf.format(fecha);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeRealtimeListeners();
    }

    private void setupRealtimeListeners() {
        if (currentUserId == null) return;

        // Listener 1: Escuchar cambios en bloqueos del paseador
        ListenerRegistration bloqueosListener = db.collection("paseadores")
                .document(currentUserId)
                .collection("disponibilidad")
                .document("bloqueos")
                .collection("items")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        return;
                    }

                    // Verificar si la activity está activa antes de actualizar UI
                    if (snapshot != null && !isFinishing() && !isDestroyed()) {
                        cargarDisponibilidadDelMes();
                    }
                });
        realtimeListeners.add(bloqueosListener);

        // Listener 2: Escuchar cambios en horarios especiales del paseador
        ListenerRegistration horariosEspecialesListener = db.collection("paseadores")
                .document(currentUserId)
                .collection("disponibilidad")
                .document("horarios_especiales")
                .collection("items")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        return;
                    }

                    // Verificar si la activity está activa antes de actualizar UI
                    if (snapshot != null && !isFinishing() && !isDestroyed()) {
                        cargarDisponibilidadDelMes();
                    }
                });
        realtimeListeners.add(horariosEspecialesListener);

        // Listener 3: Escuchar cambios en horario por defecto
        ListenerRegistration horarioDefaultListener = db.collection("paseadores")
                .document(currentUserId)
                .collection("disponibilidad")
                .document("horario_default")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        return;
                    }

                    // Verificar si la activity está activa antes de actualizar UI
                    if (snapshot != null && !isFinishing() && !isDestroyed()) {
                        cargarHorarioDefault();
                        cargarDisponibilidadDelMes();
                    }
                });
        realtimeListeners.add(horarioDefaultListener);
    }

    private void removeRealtimeListeners() {
        for (ListenerRegistration listener : realtimeListeners) {
            if (listener != null) {
                listener.remove();
            }
        }
        realtimeListeners.clear();
    }
}
