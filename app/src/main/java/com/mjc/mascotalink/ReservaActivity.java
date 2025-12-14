package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.DisponibilidadHelper;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

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

public class ReservaActivity extends AppCompatActivity {

    private static final String TAG = "ReservaActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private com.mjc.mascotalink.network.NetworkMonitorHelper networkMonitor;
    private DisponibilidadHelper disponibilidadHelper;
    private List<ListenerRegistration> realtimeListeners = new ArrayList<>();

    // Views
    private ImageView ivBack;
    private RecyclerView rvMascotas, rvHorarios;
    private View btnAgregarMascota;
    private Button btnConfirmarReserva;
    private ChipGroup chipGroupFecha;
    private ImageView ivMesAnterior, ivMesSiguiente;
    private TextView tvMesAnio, tvFechaSeleccionada, tvDisponibilidad;
    private GridView gvCalendario;
    private LinearLayout llDisponibilidad, calendarioContainer;
    private TextView tabPorHoras, tabPorMes;
    private TextView tvDuracionTitulo, tvDuracionSubtitulo;
    private Button btn1Hora, btn2Horas, btn3Horas, btnPersonalizado;
    private TextView tvCalculoResumen;
    private TextView tvTarifaValor, tvDuracionValor, tvTotalValor;
    private TextView tvPaseadorNombre, tvMascotaNombre, tvDetalleFecha, tvDetalleHora, tvDetalleDuracion;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "DUEÑO";
    private int bottomNavSelectedItem = R.id.menu_walks;

    // Adapters y listas
    private MascotaSelectorAdapter mascotaAdapter;
    private List<MascotaSelectorAdapter.Mascota> mascotaList;
    private HorarioSelectorAdapter horarioAdapter;
    private List<HorarioSelectorAdapter.Horario> horarioList;
    private CalendarioAdapter calendarioAdapter;
    private List<Date> datesList;
    private Calendar currentMonth;

    // Variables de estado
    private String paseadorId, paseadorNombre;
    private double tarifaPorHora = 10.6;
    private MascotaSelectorAdapter.Mascota mascotaSeleccionada;
    private Date fechaSeleccionada;
    private HorarioSelectorAdapter.Horario horarioSeleccionado;
    private int duracionMinutos = 0;
    private double costoTotal = 0.0;
    private String tipoReserva = "PUNTUAL";
    private String modoFechaActual = "DIAS_ESPECIFICOS";
    private boolean tabPorMesActivo = false;
    private String notasAdicionalesMascota = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reserva);

        // --- FIX INICIO: Validaciones críticas de seguridad y datos ---
        // RIESGO: Si el usuario no está autenticado, currentUserId será nulo, causando fallos
        // en las consultas a Firebase.
        // SOLUCIÓN: Se verifica la sesión del usuario. Si es nula, se notifica y se
        // cierra la actividad para prevenir operaciones inválidas.
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        disponibilidadHelper = new DisponibilidadHelper();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Error: Sesión de usuario no válida.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // RIESGO: Si no se recibe 'paseador_id' o 'tarifa_por_hora', la reserva no puede
        // crearse correctamente, usando datos por defecto o nulos.
        // SOLUCIÓN: Se valida la presencia y validez de los extras del Intent. Si falta
        // información esencial, se notifica y se cierra la actividad.
        Intent intent = getIntent();
        paseadorId = intent.getStringExtra("paseador_id");
        paseadorNombre = intent.getStringExtra("paseador_nombre");

        if (paseadorId == null || paseadorId.isEmpty()) {
            Toast.makeText(this, "Error: No se ha proporcionado un paseador.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Se mantiene el valor por defecto para la tarifa, pero se asegura que no sea cero.
        tarifaPorHora = intent.getDoubleExtra("precio_hora", 0.0);
        if (tarifaPorHora <= 0) {
            Toast.makeText(this, "Error: Tarifa por hora no válida.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // --- FIX FIN ---

        // Initialize role from cache
        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
            bottomNavRole = cachedRole;
        }

        // Inicializar NetworkMonitorHelper para validar conexión antes de pagos
        setupNetworkMonitor();

        initViews();
        setupListeners();
        setupBottomNavigation();
        cargarMascotasUsuario();
        setupCalendario();
        setupHorarios();

        tvPaseadorNombre.setText(paseadorNombre != null ? paseadorNombre : "Alex");
        tvTarifaValor.setText(String.format(Locale.US, "$%.1f/hora", tarifaPorHora));

        // Auto-seleccionar 1 hora como duración por defecto
        seleccionarDuracion(60, btn1Hora);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void initViews() {
        // --- FIX INICIO: Verificación de nulidad para las vistas ---
        // RIESGO: Si una ID del layout XML no coincide con la del código Java,
        // findViewById() devuelve null, causando un NullPointerException al usar la vista.
        // SOLUCIÓN: Se comprueba cada vista después de buscarla. Si alguna es nula,
        // se lanza una excepción clara para un diagnóstico rápido.

        ivBack = findViewById(R.id.iv_back);
        rvMascotas = findViewById(R.id.rv_mascotas);
        btnAgregarMascota = findViewById(R.id.btn_agregar_mascota);
        chipGroupFecha = findViewById(R.id.chip_group_fecha);
        ivMesAnterior = findViewById(R.id.iv_mes_anterior);
        ivMesSiguiente = findViewById(R.id.iv_mes_siguiente);
        tvMesAnio = findViewById(R.id.tv_mes_anio);
        gvCalendario = findViewById(R.id.gv_calendario);
        calendarioContainer = findViewById(R.id.calendario_container);
        tvFechaSeleccionada = findViewById(R.id.tv_fecha_seleccionada);
        rvHorarios = findViewById(R.id.rv_horarios);
        llDisponibilidad = findViewById(R.id.ll_disponibilidad);
        tvDisponibilidad = findViewById(R.id.tv_disponibilidad);
        tabPorHoras = findViewById(R.id.tab_por_horas);
        tabPorMes = findViewById(R.id.tab_por_mes);
        tvDuracionTitulo = findViewById(R.id.tv_duracion_titulo);
        tvDuracionSubtitulo = findViewById(R.id.tv_duracion_subtitulo);
        btn1Hora = findViewById(R.id.btn_1_hora);
        btn2Horas = findViewById(R.id.btn_2_horas);
        btn3Horas = findViewById(R.id.btn_3_horas);
        btnPersonalizado = findViewById(R.id.btn_personalizado);
        tvCalculoResumen = findViewById(R.id.tv_calculo_resumen);
        tvTarifaValor = findViewById(R.id.tv_tarifa_valor);
        tvDuracionValor = findViewById(R.id.tv_duracion_valor);
        tvTotalValor = findViewById(R.id.tv_total_valor);
        tvPaseadorNombre = findViewById(R.id.tv_paseador_nombre);
        tvMascotaNombre = findViewById(R.id.tv_mascota_nombre);
        tvDetalleFecha = findViewById(R.id.tv_detalle_fecha);
        tvDetalleHora = findViewById(R.id.tv_detalle_hora);
        tvDetalleDuracion = findViewById(R.id.tv_detalle_duracion);
        btnConfirmarReserva = findViewById(R.id.btn_confirmar_reserva);
        bottomNav = findViewById(R.id.bottom_nav);

        if (ivBack == null || rvMascotas == null || btnAgregarMascota == null || chipGroupFecha == null ||
            ivMesAnterior == null || ivMesSiguiente == null || tvMesAnio == null || gvCalendario == null ||
            calendarioContainer == null || tvFechaSeleccionada == null || rvHorarios == null || llDisponibilidad == null ||
            tvDisponibilidad == null || tabPorHoras == null || tabPorMes == null || tvDuracionTitulo == null ||
            tvDuracionSubtitulo == null || btn1Hora == null || btn2Horas == null || btn3Horas == null ||
            btnPersonalizado == null || tvCalculoResumen == null || tvTarifaValor == null ||
            tvDuracionValor == null || tvTotalValor == null || tvPaseadorNombre == null ||
            tvMascotaNombre == null || tvDetalleFecha == null || tvDetalleHora == null ||
            tvDetalleDuracion == null || btnConfirmarReserva == null || bottomNav == null) {
            
            throw new IllegalStateException("Error de inicialización: Una o más vistas no se encontraron en el layout. " +
                    "Verifica que los IDs en activity_reserva.xml coincidan con los usados en ReservaActivity.java.");
        }

        mascotaList = new ArrayList<>();
        horarioList = new ArrayList<>();
        // --- FIX FIN ---
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        btnAgregarMascota.setOnClickListener(v -> {
            startActivity(new Intent(ReservaActivity.this, MascotaRegistroPaso1Activity.class));
        });

        chipGroupFecha.setOnCheckedChangeListener((group, checkedId) -> {
            // --- FIX INICIO: Lógica para llamar a los métodos de vista de calendario ---
            if (checkedId == R.id.chip_dias_especificos) {
                modoFechaActual = "DIAS_ESPECIFICOS";
                tipoReserva = "PUNTUAL";
                cargarVistaMensual(); // El modo "días específicos" muestra el calendario mensual.
            } else if (checkedId == R.id.chip_semana) {
                modoFechaActual = "SEMANA";
                tipoReserva = "SEMANAL";
                cargarVistaSemanales();
            } else if (checkedId == R.id.chip_mes) {
                modoFechaActual = "MES";
                tipoReserva = "MENSUAL";
                cargarVistaMensual(); // El modo "por mes" también usa la vista de calendario mensual.
            } else {
                // Por defecto, si no hay nada seleccionado, mostrar la vista mensual.
                modoFechaActual = "DIAS_ESPECIFICOS";
                tipoReserva = "PUNTUAL";
                cargarVistaMensual();
            }

            if (calendarioContainer != null) {
                calendarioContainer.setVisibility(View.VISIBLE);
            }

            // Resetear selección de fecha y horario al cambiar de modo para evitar inconsistencias
            fechaSeleccionada = null;
            horarioSeleccionado = null;
            tvFechaSeleccionada.setVisibility(View.GONE);
            llDisponibilidad.setVisibility(View.GONE);

            // Limpiar selecciones múltiples del calendario
            if (calendarioAdapter != null) {
                calendarioAdapter.setFechasSeleccionadas(new HashSet<>());
                // Configurar bloqueo de deselección según el modo
                boolean bloquear = modoFechaActual.equals("SEMANA") || modoFechaActual.equals("MES");
                calendarioAdapter.setBloquearDeseleccion(bloquear);
            }

            actualizarTextoDuracion();
            actualizarResumenCosto(); // Recalcular costo al cambiar de modo
            verificarCamposCompletos();

            // Auto-seleccionar DESPUÉS del reset para modos SEMANA y MES
            if (modoFechaActual.equals("SEMANA") || modoFechaActual.equals("MES")) {
                autoSeleccionarFechaInicialYAplicar();
            }
            // --- FIX FIN ---
        });

        ivMesAnterior.setOnClickListener(v -> cambiarMes(-1));
        ivMesSiguiente.setOnClickListener(v -> cambiarMes(1));
        tabPorHoras.setOnClickListener(v -> activarTabPorHoras());
        tabPorMes.setOnClickListener(v -> activarTabPorMes());
        btn1Hora.setOnClickListener(v -> seleccionarDuracion(60, btn1Hora));
        btn2Horas.setOnClickListener(v -> seleccionarDuracion(120, btn2Horas));
        btn3Horas.setOnClickListener(v -> seleccionarDuracion(180, btn3Horas));
        btnPersonalizado.setOnClickListener(v -> mostrarDialogDuracionPersonalizada());
        btnConfirmarReserva.setOnClickListener(v -> confirmarReserva());
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        BottomNavManager.setupBottomNav(this, bottomNav, bottomNavRole, bottomNavSelectedItem);
    }

    private void cargarMascotasUsuario() {
        if (currentUserId == null) return;

        db.collection("duenos").document(currentUserId).collection("mascotas")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    mascotaList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        MascotaSelectorAdapter.Mascota mascota = new MascotaSelectorAdapter.Mascota();
                        mascota.setId(doc.getId());
                        mascota.setNombre(doc.getString("nombre"));
                        mascota.setFotoUrl(doc.getString("foto_principal_url"));
                        mascota.setActivo(Boolean.TRUE.equals(doc.getBoolean("activo")));
                        mascotaList.add(mascota);
                    }

                    if (mascotaList.isEmpty()) {
                        rvMascotas.setVisibility(View.GONE);
                        btnAgregarMascota.setVisibility(View.VISIBLE);
                    } else {
                        rvMascotas.setVisibility(View.VISIBLE);
                        btnAgregarMascota.setVisibility(View.GONE);
                        setupMascotasRecyclerView();

                        // Auto-seleccionar si solo hay 1 mascota
                        if (mascotaList.size() == 1) {
                            mascotaSeleccionada = mascotaList.get(0);
                            tvMascotaNombre.setText(mascotaSeleccionada.getNombre());
                            cargarNotasAdicionalesMascota(mascotaSeleccionada.getId());
                            // Marcar visualmente en el adapter
                            if (mascotaAdapter != null) {
                                mascotaAdapter.setSelectedPosition(0);
                            }
                            verificarCamposCompletos();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar mascotas", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupMascotasRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvMascotas.setLayoutManager(layoutManager);
        
        // Optimización para scrolling suave
        // rvMascotas.setHasFixedSize(true); // Eliminado para resolver InvalidSetHasFixedSize
        rvMascotas.setItemViewCacheSize(20);
        rvMascotas.setDrawingCacheEnabled(true);
        rvMascotas.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        // Agregar espaciado consistente entre items (12dp)
        int spacingInPixels = (int) (12 * getResources().getDisplayMetrics().density);
        rvMascotas.addItemDecoration(new HorizontalSpaceItemDecoration(spacingInPixels));
        
        mascotaAdapter = new MascotaSelectorAdapter(this, mascotaList, (mascota, position) -> {
            mascotaSeleccionada = mascota;
            tvMascotaNombre.setText(mascota.getNombre());
            cargarNotasAdicionalesMascota(mascota.getId());
            verificarCamposCompletos();
        });
        rvMascotas.setAdapter(mascotaAdapter);
    }

    @SuppressWarnings("unchecked")
    private void cargarNotasAdicionalesMascota(String mascotaId) {
        if (currentUserId == null || mascotaId == null) {
            notasAdicionalesMascota = "";
            return;
        }
        db.collection("duenos").document(currentUserId).collection("mascotas").document(mascotaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> instruccionesMap = (Map<String, Object>) documentSnapshot.get("instrucciones");
                        if (instruccionesMap != null) {
                            String notas = (String) instruccionesMap.get("notas_adicionales");
                            if (notas != null && !notas.isEmpty()) {
                                notasAdicionalesMascota = notas;
                            } else {
                                notasAdicionalesMascota = "";
                            }
                        } else {
                            notasAdicionalesMascota = "";
                        }
                    } else {
                        notasAdicionalesMascota = "";
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar notas adicionales de la mascota", e);
                    notasAdicionalesMascota = ""; // Asegurarse de que esté vacío en caso de error
                });
    }


    private void setupCalendario() {
        currentMonth = Calendar.getInstance();
        datesList = new ArrayList<>();
        // Por defecto, se carga la vista mensual.
        cargarVistaMensual();

        // Auto-seleccionar fecha: mañana si son más de las 8 PM, hoy en caso contrario
        autoSeleccionarFechaInicial();
    }

    /**
     * Auto-selecciona la fecha inicial basándose en la hora actual
     * Si son más de las 8 PM, selecciona mañana. Si no, selecciona hoy.
     */
    private void autoSeleccionarFechaInicial() {
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        // Si son más de las 8 PM (20:00), seleccionar mañana
        if (currentHour >= 20) {
            now.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Normalizar la fecha (eliminar hora/minutos/segundos)
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        final Date fechaInicial = now.getTime();
        fechaSeleccionada = fechaInicial;
        mostrarFechaSeleccionada(fechaSeleccionada);

        // Pequeño delay para asegurar que el calendarioAdapter esté listo
        gvCalendario.postDelayed(() -> {
            if (calendarioAdapter != null) {
                // Para DIAS_ESPECIFICOS, usar multi-selección
                if (modoFechaActual.equals("DIAS_ESPECIFICOS")) {
                    Set<Date> fechasIniciales = new HashSet<>();
                    fechasIniciales.add(normalizarFecha(fechaInicial));
                    calendarioAdapter.setFechasSeleccionadas(fechasIniciales);
                } else {
                    // Para SEMANA y MES, usar selección simple
                    calendarioAdapter.setSelectedDate(fechaInicial);
                }
                calendarioAdapter.notifyDataSetChanged();
            }
            cargarHorariosDisponibles();
            verificarCamposCompletos();
        }, 300);
    }

    // --- FIX INICIO: Nuevos métodos para cargar las vistas del calendario ---

    /**
     * Carga la vista del calendario para mostrar 7 días consecutivos de servicio desde hoy.
     */
    private void cargarVistaSemanales() {
        // Ocultar flechas de navegación y cambiar título
        ivMesAnterior.setVisibility(View.INVISIBLE);
        ivMesSiguiente.setVisibility(View.INVISIBLE);
        tvMesAnio.setText("Próximos 7 Días");

        datesList.clear();
        Calendar cal = Calendar.getInstance();

        // Si son más de las 8 PM, comenzar desde mañana
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        if (currentHour >= 20) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Generar 7 días consecutivos desde hoy (o mañana)
        for (int i = 0; i < 7; i++) {
            datesList.add(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (calendarioAdapter == null) {
             calendarioAdapter = new CalendarioAdapter(this, datesList, currentMonth, (date, position) -> {
                onFechaSeleccionada(date);
            });
            calendarioAdapter.setEsVistaPaseador(false); // Vista de cliente
            // Activar selección múltiple para modo SEMANA
            calendarioAdapter.setSeleccionMultiple(true);
            calendarioAdapter.setBloquearDeseleccion(true); // Bloquear deselección en modo SEMANA
            gvCalendario.setAdapter(calendarioAdapter);
        } else {
            calendarioAdapter.updateDates(datesList, (Calendar) cal.clone());
            calendarioAdapter.setBloquearDeseleccion(true); // Asegurar bloqueo al actualizar
        }

        // Si hay una fecha previamente seleccionada, seleccionar la semana completa
        if (fechaSeleccionada != null) {
            seleccionarSemanaCompleta(fechaSeleccionada);
        }

        // Cargar estados de disponibilidad
        cargarEstadosDisponibilidadDelMes();
    }

    /**
     * Carga la vista del calendario para mostrar el mes completo.
     */
    private void cargarVistaMensual() {
        // Mostrar flechas de navegación
        ivMesAnterior.setVisibility(View.VISIBLE);
        ivMesSiguiente.setVisibility(View.VISIBLE);
        actualizarCalendario(); // Este método ya dibuja el mes completo
    }

    // --- FIX FIN ---

    private void actualizarCalendario() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
        tvMesAnio.setText(capitalizeFirst(sdf.format(currentMonth.getTime())));

        datesList.clear();
        Calendar cal = (Calendar) currentMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // --- FIX: Lógica robusta para calcular los días vacíos al inicio del mes ---
        int dayOfWeekForFirst = cal.get(Calendar.DAY_OF_WEEK); // ej. Miércoles es 4
        int firstDayOfWeekSetting = cal.getFirstDayOfWeek(); // ej. Lunes es 2
        int emptyDays = dayOfWeekForFirst - firstDayOfWeekSetting;
        if (emptyDays < 0) {
            emptyDays += 7;
        }
        for (int i = 0; i < emptyDays; i++) {
            datesList.add(null);
        }

        // Añadir los días del mes actual
        for (int i = 1; i <= daysInMonth; i++) {
            cal.set(Calendar.DAY_OF_MONTH, i);
            datesList.add(cal.getTime());
        }

        // Rellenar con días vacíos para completar la última fila
        while (datesList.size() % 7 != 0) {
            datesList.add(null);
        }

        if (calendarioAdapter == null) {
            calendarioAdapter = new CalendarioAdapter(this, datesList, currentMonth, (date, position) -> {
                onFechaSeleccionada(date);
            });
            calendarioAdapter.setEsVistaPaseador(false); // Vista de cliente
            // Activar selección múltiple para MES y DIAS_ESPECIFICOS
            boolean multiSelect = modoFechaActual.equals("MES") || modoFechaActual.equals("DIAS_ESPECIFICOS");
            calendarioAdapter.setSeleccionMultiple(multiSelect);
            // Bloquear deselección solo para MES, no para DIAS_ESPECIFICOS
            boolean bloquear = modoFechaActual.equals("MES");
            calendarioAdapter.setBloquearDeseleccion(bloquear);
            gvCalendario.setAdapter(calendarioAdapter);
        } else {
            calendarioAdapter.updateDates(datesList, currentMonth);
            // Actualizar modo de selección múltiple
            boolean multiSelect = modoFechaActual.equals("MES") || modoFechaActual.equals("DIAS_ESPECIFICOS");
            calendarioAdapter.setSeleccionMultiple(multiSelect);
            // Bloquear deselección solo para MES, no para DIAS_ESPECIFICOS
            boolean bloquear = modoFechaActual.equals("MES");
            calendarioAdapter.setBloquearDeseleccion(bloquear);
        }

        // Si modo MES, seleccionar todos los días del mes
        if (modoFechaActual.equals("MES") && fechaSeleccionada != null) {
            seleccionarMesCompleto(fechaSeleccionada);
        }

        // Cargar estados de disponibilidad del mes
        cargarEstadosDisponibilidadDelMes();
    }

    /**
     * Maneja la selección de fecha según el modo activo (DIAS_ESPECIFICOS, SEMANA, MES)
     */
    private void onFechaSeleccionada(Date date) {
        if (date == null) return;

        switch (modoFechaActual) {
            case "SEMANA":
                // Siempre seleccionar la semana completa (bloquea deselección individual)
                fechaSeleccionada = date;
                gvCalendario.post(() -> {
                    seleccionarSemanaCompleta(date);
                    mostrarRangoSemana(date);
                });
                break;

            case "MES":
                // Siempre seleccionar el mes completo (bloquea deselección individual)
                fechaSeleccionada = date;
                gvCalendario.post(() -> {
                    seleccionarMesCompleto(date);
                    mostrarRangoMes(date);
                });
                break;

            case "DIAS_ESPECIFICOS":
            default:
                // Selección múltiple libre (permite seleccionar/deseleccionar días individuales)
                // Establecer fechaSeleccionada para cargar horarios (aunque usamos multi-selección)
                fechaSeleccionada = date;
                mostrarTextoMultiplesDias();
                break;
        }

        cargarHorariosDisponibles();
        verificarCamposCompletos();
    }

    /**
     * Muestra el texto con los días seleccionados en modo "Días Específicos"
     */
    private void mostrarTextoMultiplesDias() {
        if (calendarioAdapter == null) return;

        Set<Date> diasSeleccionados = calendarioAdapter.getFechasSeleccionadas();

        if (diasSeleccionados.isEmpty()) {
            tvFechaSeleccionada.setVisibility(View.GONE);
            return;
        }

        int cantidadDias = diasSeleccionados.size();
        String textoResumen = cantidadDias + (cantidadDias == 1 ? " día seleccionado" : " días seleccionados");

        tvFechaSeleccionada.setText(textoResumen);
        tvFechaSeleccionada.setVisibility(View.VISIBLE);
        tvDetalleFecha.setText(textoResumen);
    }

    /**
     * Selecciona visualmente 7 días consecutivos desde la fecha dada
     */
    private void seleccionarSemanaCompleta(Date date) {
        if (calendarioAdapter == null || date == null) return;

        Set<Date> diasSemana = new HashSet<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        // Seleccionar 7 días consecutivos desde la fecha dada
        for (int i = 0; i < 7; i++) {
            diasSemana.add(normalizarFecha(cal.getTime()));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        calendarioAdapter.setFechasSeleccionadas(diasSemana);
    }

    /**
     * Selecciona visualmente 30 días consecutivos desde la fecha dada
     */
    private void seleccionarMesCompleto(Date date) {
        if (calendarioAdapter == null || date == null) return;

        Set<Date> diasMes = new HashSet<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        // Agregar 30 días consecutivos desde la fecha dada
        for (int i = 0; i < 30; i++) {
            diasMes.add(normalizarFecha(cal.getTime()));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        calendarioAdapter.setFechasSeleccionadas(diasMes);
    }

    /**
     * Muestra el rango de fechas para 7 días consecutivos desde la fecha dada
     */
    private void mostrarRangoSemana(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        Date inicioSemana = cal.getTime();

        // 6 días después para completar 7 días
        cal.add(Calendar.DATE, 6);
        Date finSemana = cal.getTime();

        // Formato con nombre del día incluido
        SimpleDateFormat sdfConDia = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));
        String inicioTexto = capitalizeFirst(sdfConDia.format(inicioSemana));
        String finTexto = capitalizeFirst(sdfConDia.format(finSemana));

        String rangoTexto = inicioTexto + " - " + finTexto + " (7 días)";

        tvFechaSeleccionada.setText(rangoTexto);
        tvFechaSeleccionada.setVisibility(View.VISIBLE);
        tvDetalleFecha.setText(rangoTexto);
    }

    /**
     * Auto-selecciona la fecha inicial y aplica la selección visual según el modo
     */
    private void autoSeleccionarFechaInicialYAplicar() {
        // Usar la lógica existente para determinar la fecha inicial
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        // Si son más de las 8 PM (20:00), seleccionar mañana
        if (currentHour >= 20) {
            now.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Capturar la fecha en variable local para evitar que se sobrescriba
        final Date fechaInicial = now.getTime();
        fechaSeleccionada = fechaInicial;

        // Aplicar selección según el modo
        gvCalendario.postDelayed(() -> {
            if (modoFechaActual.equals("SEMANA")) {
                seleccionarSemanaCompleta(fechaInicial);
                mostrarRangoSemana(fechaInicial);
                fechaSeleccionada = fechaInicial; // Restaurar después de reset
            } else if (modoFechaActual.equals("MES")) {
                seleccionarMesCompleto(fechaInicial);
                mostrarRangoMes(fechaInicial);
                fechaSeleccionada = fechaInicial; // Restaurar después de reset
            }
            actualizarResumenCosto(); // Actualizar costo después de auto-selección
            cargarHorariosDisponibles();
            verificarCamposCompletos();
        }, 100); // Pequeño delay para asegurar que el calendario esté listo
    }

    /**
     * Muestra el rango de fechas para 30 días consecutivos desde la fecha dada
     */
    private void mostrarRangoMes(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        Date inicioMes = cal.getTime();

        // 29 días después para completar 30 días
        cal.add(Calendar.DATE, 29);
        Date finMes = cal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
        String inicioTexto = sdf.format(inicioMes);
        String finTexto = sdf.format(finMes);

        String rangoTexto = capitalizeFirst(inicioTexto) + " - " + capitalizeFirst(finTexto) + " (30 días)";

        tvFechaSeleccionada.setText(rangoTexto);
        tvFechaSeleccionada.setVisibility(View.VISIBLE);
        tvDetalleFecha.setText(rangoTexto);
    }

    private void cargarEstadosDisponibilidadDelMes() {
        if (paseadorId == null) return;

        Set<Date> diasDisponibles = new HashSet<>();
        Set<Date> diasBloqueados = new HashSet<>();
        Set<Date> diasParciales = new HashSet<>();

        // PASO 1: Cargar horario por defecto primero
        db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horario_default")
                .get()
                .addOnSuccessListener(horarioDoc -> {
                    // Marcar días disponibles según horario estándar
                    if (horarioDoc.exists()) {
                        marcarDiasDisponiblesSegunHorario(horarioDoc, diasDisponibles);
                    } else {
                        // Si no existe horario_default, usar patrón por defecto: Lunes a Viernes
                        Log.w(TAG, "No se encontró horario_default, usando patrón Lunes-Viernes");
                        marcarDiasDisponiblesPatronDefecto(diasDisponibles);
                    }

                    // PASO 2: Cargar bloqueos del mes
                    cargarBloqueosDelMesReserva(diasDisponibles, diasBloqueados, diasParciales);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando horario default", e);
                    // Si falla, usar patrón por defecto y cargar bloqueos
                    marcarDiasDisponiblesPatronDefecto(diasDisponibles);
                    cargarBloqueosDelMesReserva(diasDisponibles, diasBloqueados, diasParciales);
                });
    }

    private void marcarDiasDisponiblesSegunHorario(DocumentSnapshot horarioDoc, Set<Date> diasDisponibles) {
        // Obtener configuración de días de la semana
        java.util.Map<Integer, Boolean> diasLaborales = new java.util.HashMap<>();
        diasLaborales.put(Calendar.MONDAY, horarioDoc.get("lunes.disponible") != null && (Boolean) horarioDoc.get("lunes.disponible"));
        diasLaborales.put(Calendar.TUESDAY, horarioDoc.get("martes.disponible") != null && (Boolean) horarioDoc.get("martes.disponible"));
        diasLaborales.put(Calendar.WEDNESDAY, horarioDoc.get("miercoles.disponible") != null && (Boolean) horarioDoc.get("miercoles.disponible"));
        diasLaborales.put(Calendar.THURSDAY, horarioDoc.get("jueves.disponible") != null && (Boolean) horarioDoc.get("jueves.disponible"));
        diasLaborales.put(Calendar.FRIDAY, horarioDoc.get("viernes.disponible") != null && (Boolean) horarioDoc.get("viernes.disponible"));
        diasLaborales.put(Calendar.SATURDAY, horarioDoc.get("sabado.disponible") != null && (Boolean) horarioDoc.get("sabado.disponible"));
        diasLaborales.put(Calendar.SUNDAY, horarioDoc.get("domingo.disponible") != null && (Boolean) horarioDoc.get("domingo.disponible"));

        // Marcar cada día del mes según configuración
        Calendar cal = (Calendar) currentMonth.clone();
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

    /**
     * Marca días disponibles con patrón por defecto: Lunes a Viernes
     * Se usa cuando el paseador no ha configurado su horario_default
     */
    private void marcarDiasDisponiblesPatronDefecto(Set<Date> diasDisponibles) {
        // Patrón por defecto: Lunes a Viernes (días laborales estándar)
        Calendar cal = (Calendar) currentMonth.clone();
        int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int dia = 1; dia <= diasDelMes; dia++) {
            cal.set(Calendar.DAY_OF_MONTH, dia);
            int diaSemana = cal.get(Calendar.DAY_OF_WEEK);

            // Lunes (2) a Viernes (6)
            if (diaSemana >= Calendar.MONDAY && diaSemana <= Calendar.FRIDAY) {
                diasDisponibles.add(normalizarFecha(cal.getTime()));
            }
        }
    }

    private void cargarBloqueosDelMesReserva(Set<Date> diasDisponibles, Set<Date> diasBloqueados, Set<Date> diasParciales) {
        // Calcular rango del mes actual
        Calendar inicioMes = (Calendar) currentMonth.clone();
        inicioMes.set(Calendar.DAY_OF_MONTH, 1);
        inicioMes.set(Calendar.HOUR_OF_DAY, 0);
        inicioMes.set(Calendar.MINUTE, 0);
        inicioMes.set(Calendar.SECOND, 0);

        Calendar finMes = (Calendar) currentMonth.clone();
        finMes.set(Calendar.DAY_OF_MONTH, currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH));
        finMes.set(Calendar.HOUR_OF_DAY, 23);
        finMes.set(Calendar.MINUTE, 59);
        finMes.set(Calendar.SECOND, 59);

        Timestamp inicioRango = new Timestamp(inicioMes.getTime());
        Timestamp finRango = new Timestamp(finMes.getTime());

        // Cargar bloqueos del mes
        db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioRango)
                .whereLessThanOrEqualTo("fecha", finRango)
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        com.mjc.mascotalink.modelo.Bloqueo bloqueo =
                            doc.toObject(com.mjc.mascotalink.modelo.Bloqueo.class);
                        if (bloqueo != null && bloqueo.getFecha() != null && bloqueo.getTipo() != null) {
                            Date fecha = bloqueo.getFecha().toDate();
                            Date fechaNormalizada = normalizarFecha(fecha);
                            if (fechaNormalizada != null) {
                                if (com.mjc.mascotalink.modelo.Bloqueo.TIPO_DIA_COMPLETO.equals(bloqueo.getTipo())) {
                                    diasBloqueados.add(fechaNormalizada);
                                    diasDisponibles.remove(fechaNormalizada); // Quitar de disponibles
                                } else {
                                    diasParciales.add(fechaNormalizada);
                                }
                            }
                        }
                    }

                    // Actualizar calendario con estados
                    if (calendarioAdapter != null) {
                        calendarioAdapter.setDiasDisponibles(diasDisponibles);
                        calendarioAdapter.setDiasBloqueados(diasBloqueados);
                        calendarioAdapter.setDiasParciales(diasParciales);
                        calendarioAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando bloqueos del mes", e);
                });
    }

    private Date normalizarFecha(Date fecha) {
        if (fecha == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private void cambiarMes(int offset) {
        currentMonth.add(Calendar.MONTH, offset);
        actualizarCalendario();
    }

    private void mostrarFechaSeleccionada(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d 'de' MMMM yyyy", new Locale("es", "ES"));
        String fechaFormateada = capitalizeFirst(sdf.format(date));
        tvFechaSeleccionada.setText(fechaFormateada);
        tvFechaSeleccionada.setVisibility(View.VISIBLE);
        tvDetalleFecha.setText(fechaFormateada);
    }

    private void setupHorarios() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvHorarios.setLayoutManager(layoutManager);
        generarHorariosBase();
        horarioAdapter = new HorarioSelectorAdapter(this, horarioList, (horario, position) -> {
            horarioSeleccionado = horario;
            tvDetalleHora.setText(horario.getHoraFormateada());
            actualizarIndicadorDisponibilidad(horario);
            verificarCamposCompletos();
        });
        rvHorarios.setAdapter(horarioAdapter);

        // Configurar listeners en tiempo real para detectar cambios en disponibilidad
        setupRealtimeListeners();
    }

    private void generarHorariosBase() {
        horarioList.clear();
        for (int hora = 6; hora <= 21; hora++) {
            for (int minuto = 0; minuto < 60; minuto += 30) {
                String horaFormateada = formatearHora(hora, minuto);
                HorarioSelectorAdapter.Horario horario = new HorarioSelectorAdapter.Horario(
                        horaFormateada, hora, minuto, true
                );
                horarioList.add(horario);
            }
        }
    }

    private void cargarHorariosDisponibles() {
        if (paseadorId == null || fechaSeleccionada == null) return;
        if (horarioList == null || horarioList.isEmpty()) return;

        Calendar today = Calendar.getInstance();
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(fechaSeleccionada);

        boolean isToday = selectedCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

        // Validar disponibilidad para cada horario usando DisponibilidadHelper
        for (HorarioSelectorAdapter.Horario horario : horarioList) {
            if (horario == null) continue;
            // Primero verificar si es hora pasada
            if (isToday && (horario.getHora() < today.get(Calendar.HOUR_OF_DAY) ||
                    (horario.getHora() == today.get(Calendar.HOUR_OF_DAY) &&
                            horario.getMinutos() <= today.get(Calendar.MINUTE)))) {
                horario.setDisponible(false);
                horario.setDisponibilidadEstado("NO_DISPONIBLE");
                continue;
            }

            // Validar disponibilidad del paseador
            if (duracionMinutos > 0) {
                validarDisponibilidadHorario(horario);
            } else {
                // Si no hay duración seleccionada, marcar como pendiente de validación
                horario.setDisponible(true);
                horario.setDisponibilidadEstado("PENDIENTE");
            }
        }

        if (horarioAdapter != null) {
            horarioAdapter.notifyDataSetChanged();

            // Scroll automático a la primera hora disponible
            scrollToFirstAvailableTime();

            // Auto-avanzar al siguiente día si no hay horarios disponibles
            autoAvanzarSiNoHayDisponibilidad();
        }
    }

    /**
     * Hace scroll automáticamente a la primera hora disponible y la selecciona
     */
    private void scrollToFirstAvailableTime() {
        if (horarioList == null || horarioList.isEmpty() || rvHorarios == null) return;

        // Buscar la primera hora disponible
        int firstAvailablePosition = -1;
        HorarioSelectorAdapter.Horario primeraHoraDisponible = null;

        for (int i = 0; i < horarioList.size(); i++) {
            HorarioSelectorAdapter.Horario horario = horarioList.get(i);
            if (horario != null && horario.isDisponible()) {
                firstAvailablePosition = i;
                primeraHoraDisponible = horario;
                break;
            }
        }

        // Si se encontró una hora disponible, hacer scroll y seleccionar
        if (firstAvailablePosition >= 0 && primeraHoraDisponible != null) {
            final int position = firstAvailablePosition;
            final HorarioSelectorAdapter.Horario horarioFinal = primeraHoraDisponible;

            // Usar post para asegurar que el RecyclerView esté listo
            rvHorarios.post(() -> {
                // Hacer scroll a la posición
                rvHorarios.smoothScrollToPosition(position);

                // Seleccionar automáticamente la hora
                if (horarioAdapter != null) {
                    horarioAdapter.setSelectedPosition(position);
                }

                // Actualizar variables de selección
                horarioSeleccionado = horarioFinal;
                tvDetalleHora.setText(horarioFinal.getHoraFormateada());
                actualizarIndicadorDisponibilidad(horarioFinal);
                verificarCamposCompletos();
            });
        }
    }

    /**
     * Auto-avanza al siguiente día si no hay horarios disponibles
     * Máximo 14 días hacia adelante para evitar loops infinitos
     */
    private void autoAvanzarSiNoHayDisponibilidad() {
        if (horarioList == null || horarioList.isEmpty() || fechaSeleccionada == null) return;

        // Verificar si HAY al menos una hora disponible
        boolean hayAlgunaHoraDisponible = false;
        for (HorarioSelectorAdapter.Horario horario : horarioList) {
            if (horario != null && horario.isDisponible()) {
                hayAlgunaHoraDisponible = true;
                break;
            }
        }

        // Si hay horas disponibles, no hacer nada
        if (hayAlgunaHoraDisponible) return;

        // Si NO hay horas disponibles, avanzar al siguiente día
        // Pero solo si todavía no hemos avanzado demasiado (máx 14 días)
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar selected = Calendar.getInstance();
        selected.setTime(fechaSeleccionada);

        long diffDays = (selected.getTimeInMillis() - today.getTimeInMillis()) / (24 * 60 * 60 * 1000);

        // Límite: no avanzar más de 14 días
        if (diffDays >= 14) {
            // Mostrar mensaje al usuario
            Toast.makeText(this, "No hay disponibilidad en los próximos 14 días", Toast.LENGTH_SHORT).show();
            return;
        }

        // Avanzar al siguiente día
        selected.add(Calendar.DAY_OF_MONTH, 1);
        fechaSeleccionada = selected.getTime();
        mostrarFechaSeleccionada(fechaSeleccionada);

        // Actualizar calendario visualmente
        if (calendarioAdapter != null) {
            calendarioAdapter.setSelectedDate(fechaSeleccionada);
            calendarioAdapter.notifyDataSetChanged();
        }

        // Recargar horarios para el nuevo día (esto llamará recursivamente a este método)
        cargarHorariosDisponibles();
    }

    private void validarDisponibilidadHorario(HorarioSelectorAdapter.Horario horario) {
        if (horario == null || fechaSeleccionada == null || paseadorId == null) return;

        // Calcular hora de inicio y fin
        String horaInicio = String.format(Locale.US, "%02d:%02d", horario.getHora(), horario.getMinutos());

        // Calcular hora de fin basada en la duración
        Calendar calFin = Calendar.getInstance();
        calFin.setTime(fechaSeleccionada);
        calFin.set(Calendar.HOUR_OF_DAY, horario.getHora());
        calFin.set(Calendar.MINUTE, horario.getMinutos());
        calFin.add(Calendar.MINUTE, duracionMinutos);

        String horaFin = String.format(Locale.US, "%02d:%02d",
            calFin.get(Calendar.HOUR_OF_DAY),
            calFin.get(Calendar.MINUTE));

        // Validar con DisponibilidadHelper
        disponibilidadHelper.esPaseadorDisponible(paseadorId, fechaSeleccionada, horaInicio, horaFin)
            .addOnSuccessListener(resultado -> {
                if (resultado.disponible) {
                    horario.setDisponible(true);
                    horario.setDisponibilidadEstado("DISPONIBLE");
                } else {
                    horario.setDisponible(false);
                    horario.setDisponibilidadEstado("NO_DISPONIBLE");
                    horario.setRazonNoDisponible(resultado.razon);
                }

                if (horarioAdapter != null) {
                    horarioAdapter.notifyDataSetChanged();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error validando disponibilidad para horario: " + horaInicio, e);
                // En caso de error, marcar como disponible para no bloquear la UI
                horario.setDisponible(true);
                horario.setDisponibilidadEstado("DISPONIBLE");
            });
    }

    private String formatearHora(int hora, int minuto) {
        String amPm = hora < 12 ? "AM" : "PM";
        int hora12 = hora == 0 ? 12 : (hora > 12 ? hora - 12 : hora);
        return String.format(Locale.US, "%d:%02d %s", hora12, minuto, amPm);
    }

    private void actualizarIndicadorDisponibilidad(HorarioSelectorAdapter.Horario horario) {
        llDisponibilidad.setVisibility(View.VISIBLE);
        switch (horario.getDisponibilidadEstado()) {
            case "DISPONIBLE":
                tvDisponibilidad.setText("✓ Paseador disponible en este horario");
                tvDisponibilidad.setTextColor(getResources().getColor(R.color.green_success));
                break;
            case "LIMITADO":
                tvDisponibilidad.setText("⚠ Disponibilidad limitada");
                tvDisponibilidad.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case "NO_DISPONIBLE":
                String razon = horario.getRazonNoDisponible();
                String mensaje = "✗ Paseador no disponible en este horario";
                if (razon != null && !razon.isEmpty()) {
                    mensaje = "✗ " + razon;
                }
                tvDisponibilidad.setText(mensaje);
                tvDisponibilidad.setTextColor(getResources().getColor(R.color.red_error));
                break;
            case "PENDIENTE":
            default:
                tvDisponibilidad.setText("ℹ️ Selecciona una duración para verificar disponibilidad");
                tvDisponibilidad.setTextColor(getResources().getColor(R.color.text_secondary));
                break;
        }
    }

    private void activarTabPorHoras() {
        tabPorMesActivo = false;
        tabPorHoras.setTextColor(getResources().getColor(R.color.blue_primary));
        tabPorHoras.setTypeface(null, android.graphics.Typeface.BOLD);
        tabPorMes.setTextColor(getResources().getColor(R.color.secondary));
        tabPorMes.setTypeface(null, android.graphics.Typeface.NORMAL);
        actualizarTextoDuracion();
        resetearDuracionSeleccionada();
    }

    private void activarTabPorMes() {
        if (!modoFechaActual.equals("MES")) {
            Toast.makeText(this, "Primero selecciona 'Por mes' en la fecha", Toast.LENGTH_SHORT).show();
            return;
        }
        tabPorMesActivo = true;
        tabPorMes.setTextColor(getResources().getColor(R.color.blue_primary));
        tabPorMes.setTypeface(null, android.graphics.Typeface.BOLD);
        tabPorHoras.setTextColor(getResources().getColor(R.color.secondary));
        tabPorHoras.setTypeface(null, android.graphics.Typeface.NORMAL);
        actualizarTextoDuracion();
        resetearDuracionSeleccionada();
    }

    private void actualizarTextoDuracion() {
        if (tabPorMesActivo) {
            tvDuracionTitulo.setText("¿Cuántas horas DIARIAS?");
            tvDuracionSubtitulo.setText("Se multiplicará por los días del mes");
            btn1Hora.setText("1 hora/día");
            btn2Horas.setText("2 horas/día");
            btn3Horas.setText("3 horas/día");
        } else {
            switch (modoFechaActual) {
                case "DIAS_ESPECIFICOS":
                    tvDuracionTitulo.setText("¿Cuántas horas para ese día?");
                    tvDuracionSubtitulo.setText("Duración total del paseo");
                    break;
                case "SEMANA":
                    tvDuracionTitulo.setText("¿Cuántas horas DIARIAS durante la semana?");
                    tvDuracionSubtitulo.setText("Se aplicará a todos los días");
                    break;
                case "MES":
                    tvDuracionTitulo.setText("¿Cuántas horas DIARIAS durante el mes?");
                    tvDuracionSubtitulo.setText("Se aplicará a todos los días");
                    break;
            }
            btn1Hora.setText("1 hora");
            btn2Horas.setText("2 horas");
            btn3Horas.setText("3 horas");
        }
    }

    private void seleccionarDuracion(int minutos, Button botonSeleccionado) {
        duracionMinutos = minutos;
        resetearBotonesDuracion();
        botonSeleccionado.setSelected(true);
        botonSeleccionado.setTextColor(getResources().getColor(android.R.color.white));
        actualizarResumenCosto();

        // Re-validar disponibilidad de horarios con la nueva duración
        cargarHorariosDisponibles();

        verificarCamposCompletos();
    }

    private void resetearBotonesDuracion() {
        Button[] botones = {btn1Hora, btn2Horas, btn3Horas, btnPersonalizado};
        for (Button btn : botones) {
            btn.setSelected(false);
            btn.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    private void resetearDuracionSeleccionada() {
        duracionMinutos = 0;
        resetearBotonesDuracion();
        tvCalculoResumen.setVisibility(View.GONE);
        actualizarResumenCosto();
        verificarCamposCompletos();
    }

    private void mostrarDialogDuracionPersonalizada() {
        // --- FIX INICIO: Reemplazar EditText por NumberPicker para una mejor UX ---
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_duracion_picker, null);
        builder.setView(dialogView);

        final android.widget.NumberPicker numberPicker = dialogView.findViewById(R.id.number_picker_horas);
        numberPicker.setMinValue(1); // Mínimo 1 hora
        numberPicker.setMaxValue(8); // Máximo 8 horas
        numberPicker.setValue(duracionMinutos > 0 ? duracionMinutos / 60 : 1); // Valor inicial

        builder.setTitle("Duración Personalizada");
        builder.setPositiveButton("Aceptar", (dialog, which) -> {
            int horasSeleccionadas = numberPicker.getValue();
            duracionMinutos = horasSeleccionadas * 60;

            resetearBotonesDuracion();
            btnPersonalizado.setSelected(true);
            btnPersonalizado.setTextColor(getResources().getColor(android.R.color.white));
            actualizarResumenCosto();

            // Re-validar disponibilidad de horarios con la nueva duración
            cargarHorariosDisponibles();

            verificarCamposCompletos();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
        // --- FIX FIN ---
    }

    private void actualizarResumenCosto() {
        if (duracionMinutos == 0) {
            tvDuracionValor.setText("-");
            tvTotalValor.setText("-");
            tvDetalleDuracion.setText("-");
            tvCalculoResumen.setVisibility(View.GONE);
            return;
        }

        double horas = duracionMinutos / 60.0;
        int diasCalculo = 1;

        if (tipoReserva.equals("SEMANAL")) {
            diasCalculo = 7; // 7 días consecutivos
        } else if (tipoReserva.equals("MENSUAL")) {
            diasCalculo = 30; // 30 días consecutivos
        }

        costoTotal = tarifaPorHora * horas * diasCalculo;

        tvDuracionValor.setText(String.format(Locale.US, "%.1f horas", horas));
        tvTotalValor.setText(String.format(Locale.US, "$%.2f", costoTotal));
        tvDetalleDuracion.setText(String.format(Locale.US, "%.1f horas", horas));

        String calculoTexto;
        if (diasCalculo > 1) {
            calculoTexto = String.format(Locale.US, "Tarifa: $%.1f/hora × %.1f horas/día × %d días = $%.2f",
                    tarifaPorHora, horas, diasCalculo, costoTotal);
        } else {
            calculoTexto = String.format(Locale.US, "Tarifa: $%.1f/hora × %.1f horas = $%.2f",
                    tarifaPorHora, horas, costoTotal);
        }
        tvCalculoResumen.setText(calculoTexto);
        tvCalculoResumen.setVisibility(View.VISIBLE);
    }

    private void verificarCamposCompletos() {
        // Para modo de selección múltiple (DIAS_ESPECIFICOS), verificar que haya fechas seleccionadas
        boolean hasFechaValida = fechaSeleccionada != null;
        if (modoFechaActual.equals("DIAS_ESPECIFICOS") && calendarioAdapter != null) {
            Set<Date> fechasSeleccionadas = calendarioAdapter.getFechasSeleccionadas();
            hasFechaValida = fechasSeleccionadas != null && !fechasSeleccionadas.isEmpty();
        }

        boolean todosCompletos = mascotaSeleccionada != null &&
                hasFechaValida &&
                horarioSeleccionado != null &&
                duracionMinutos > 0 &&
                horarioSeleccionado.isDisponible(); // Verificar que el horario esté disponible
        btnConfirmarReserva.setEnabled(todosCompletos);
    }

    private void mostrarDialogoConfirmacion() {
        // Calcular costo estimado para el diálogo
        double horas = duracionMinutos / 60.0;
        int diasCalculo = 1;
        if (tipoReserva.equals("SEMANAL")) diasCalculo = 7; // 7 días consecutivos
        else if (tipoReserva.equals("MENSUAL")) diasCalculo = 30; // 30 días consecutivos
        double costoEstimado = tarifaPorHora * horas * diasCalculo;

        // Construir mensaje
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Mascota: ").append(mascotaSeleccionada.getNombre()).append("\n");
        mensaje.append("Fecha: ").append(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fechaSeleccionada)).append("\n");
        mensaje.append("Hora: ").append(horarioSeleccionado.getHoraFormateada()).append("\n");
        mensaje.append("Duración: ").append(String.format(Locale.getDefault(), "%.1f horas", horas)).append("\n");
        if (diasCalculo > 1) {
            mensaje.append("Días: ").append(diasCalculo).append("\n");
        }
        mensaje.append("Costo Total: $").append(String.format(Locale.US, "%.2f", costoEstimado)).append("\n\n");
        mensaje.append("Al confirmar, se enviará una solicitud al paseador. El costo final se verificará antes de enviar.");

        new AlertDialog.Builder(this)
                .setTitle("Confirmar Solicitud")
                .setMessage(mensaje.toString())
                .setPositiveButton("Enviar Solicitud", (dialog, which) -> iniciarProcesoConfirmacion())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciarProcesoConfirmacion() {
        // Re-validar sesión por seguridad
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Deshabilitar botón para evitar doble click
        btnConfirmarReserva.setEnabled(false);

        // 1. Recalcular costo con tarifa real del servidor
        db.collection("usuarios").document(paseadorId).get()
            .addOnSuccessListener(paseadorDoc -> {
                if (!paseadorDoc.exists()) {
                    Toast.makeText(this, "El paseador ya no está disponible", Toast.LENGTH_SHORT).show();
                    btnConfirmarReserva.setEnabled(true);
                    return;
                }

                // Obtener precio real
                Double precioHoraReal = paseadorDoc.getDouble("precio_hora");
                if (precioHoraReal == null) {
                    // Fallback si no tiene precio configurado (usar el del intent)
                    precioHoraReal = tarifaPorHora; 
                }
                
                // Recalcular
                double horas = duracionMinutos / 60.0;
                int diasCalculo = 1;
                if (tipoReserva.equals("SEMANAL")) diasCalculo = 7; // 7 días consecutivos
                else if (tipoReserva.equals("MENSUAL")) diasCalculo = 30; // 30 días consecutivos

                final double costoTotalReal = precioHoraReal * horas * diasCalculo;

                // 2. Validar disponibilidad (Crucial para evitar overbooking)
                validarDisponibilidadYCrear(costoTotalReal, precioHoraReal);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Error verificando tarifa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                btnConfirmarReserva.setEnabled(true);
            });
    }

    private void confirmarReserva() {
        // CRÍTICO: Validar conexión ANTES de cualquier operación de pago/reserva
        if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
            new AlertDialog.Builder(this)
                .setTitle("Sin conexión")
                .setMessage("No se puede crear la reserva sin conexión a internet. Por favor, verifica tu conexión y vuelve a intentarlo.")
                .setPositiveButton("Entendido", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
            return;
        }

        if (!validarDatosReserva()) return;
        mostrarDialogoConfirmacion();
    }

    private void validarDisponibilidadYCrear(double costoTotalReal, double tarifaConfirmada) {
        Calendar inicioSolicitud = Calendar.getInstance();
        inicioSolicitud.setTime(fechaSeleccionada);
        inicioSolicitud.set(Calendar.HOUR_OF_DAY, horarioSeleccionado.getHora());
        inicioSolicitud.set(Calendar.MINUTE, horarioSeleccionado.getMinutos());
        inicioSolicitud.set(Calendar.SECOND, 0);
        
        // Se elimina la validación de solapamiento para permitir múltiples paseos simultáneos.
        // El paseador gestionará su propia capacidad aceptando o rechazando solicitudes.
        crearReservaFinal(costoTotalReal, tarifaConfirmada, inicioSolicitud.getTime());
    }

    private void crearReservaFinal(double costoTotalReal, double tarifaConfirmada, Date horaInicio) {
        Map<String, Object> reserva = new HashMap<>();
        reserva.put("id_dueno", db.collection("usuarios").document(currentUserId));
        reserva.put("id_mascota", mascotaSeleccionada.getId());
        reserva.put("id_paseador", db.collection("usuarios").document(paseadorId));
        reserva.put("fecha", new Timestamp(fechaSeleccionada));
        reserva.put("hora_inicio", new Timestamp(horaInicio));
        reserva.put("duracion_minutos", duracionMinutos);
        reserva.put("costo_total", costoTotalReal); // Precio seguro calculado
        reserva.put("estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION);
        reserva.put("tipo_reserva", tipoReserva);
        reserva.put("fecha_creacion", com.google.firebase.firestore.FieldValue.serverTimestamp());
        reserva.put("tarifa_confirmada", tarifaConfirmada);
        reserva.put("id_pago", null);
        reserva.put("estado_pago", ReservaEstadoValidator.ESTADO_PAGO_PENDIENTE);
        reserva.put("notas", notasAdicionalesMascota);
        reserva.put("reminderSent", false);
        reserva.put("timeZone", java.util.TimeZone.getDefault().getID());

        // Operación CRÍTICA: Creación de reserva con retry (5 intentos)
        com.mjc.mascotalink.util.FirestoreRetryHelper.executeCritical(
            () -> db.collection("reservas").add(reserva),
            documentReference -> {
                String reservaId = documentReference.getId();
                Toast.makeText(this, "Reserva creada exitosamente", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(ReservaActivity.this, PerfilDuenoActivity.class);
                intent.putExtra("reserva_id", reservaId);
                intent.putExtra("reserva_estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            },
            e -> {
                Toast.makeText(this, "Error al crear reserva después de varios intentos. Verifica tu conexión y vuelve a intentar.", Toast.LENGTH_LONG).show();
                btnConfirmarReserva.setEnabled(true);
                Log.e(TAG, "Error crítico al crear reserva después de reintentos", e);
            }
        );
    }

    private boolean validarDatosReserva() {
        // --- FIX INICIO: Validación de datos de la reserva ---
        // RIESGO: Datos incompletos o inválidos pueden llevar a la creación de una reserva
        // corrupta en Firebase, causando errores en otras partes de la app (pago, historial).
        // SOLUCIÓN: Se valida cada campo requerido antes de la confirmación. Se añade
        // una comprobación defensiva para la tarifa, aunque ya se valida en onCreate.
        if (mascotaSeleccionada == null) {
            Toast.makeText(this, "Selecciona una mascota", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar fecha según el modo
        if (modoFechaActual.equals("DIAS_ESPECIFICOS")) {
            // Para días específicos, verificar que haya al menos un día seleccionado
            if (calendarioAdapter == null || calendarioAdapter.getFechasSeleccionadas().isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un día", Toast.LENGTH_SHORT).show();
                return false;
            }
            // Para días específicos, usar la primera fecha seleccionada
            Set<Date> fechasSeleccionadas = calendarioAdapter.getFechasSeleccionadas();
            fechaSeleccionada = fechasSeleccionadas.iterator().next();
        } else {
            // Para semana y mes, validar fechaSeleccionada normal
            if (fechaSeleccionada == null) {
                Toast.makeText(this, "Selecciona una fecha", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if (horarioSeleccionado == null) {
            Toast.makeText(this, "Selecciona una hora", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (duracionMinutos <= 0) {
            Toast.makeText(this, "Selecciona una duración válida", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (tarifaPorHora <= 0) {
            Toast.makeText(this, "Error: La tarifa por hora es inválida.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // --- Validaciones de Negocio ---
        Calendar seleccion = Calendar.getInstance();
        seleccion.setTime(fechaSeleccionada);
        seleccion.set(Calendar.HOUR_OF_DAY, horarioSeleccionado.getHora());
        seleccion.set(Calendar.MINUTE, horarioSeleccionado.getMinutos());

        Calendar ahora = Calendar.getInstance();
        Calendar limiteMaximo = Calendar.getInstance();
        limiteMaximo.add(Calendar.DAY_OF_YEAR, 30); // Máximo 30 días a futuro

        // 1. Validación de tiempo pasado (mínimo 1 hora de anticipación)
        /*
        Calendar minAnticipacion = Calendar.getInstance();
        minAnticipacion.add(Calendar.HOUR_OF_DAY, 1);
        
        if (seleccion.before(minAnticipacion)) {
            Toast.makeText(this, "La reserva debe ser con al menos 1 hora de anticipación.", Toast.LENGTH_LONG).show();
            return false;
        }
        */

        // 2. Validación de tiempo futuro máximo
        if (seleccion.after(limiteMaximo)) {
            Toast.makeText(this, "No se pueden hacer reservas con más de 30 días de anticipación.", Toast.LENGTH_LONG).show();
            return false;
        }
        // ---------------------------------

        return true;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void setupNetworkMonitor() {
        com.mjc.mascotalink.network.SocketManager socketManager =
            com.mjc.mascotalink.network.SocketManager.getInstance(this);

        networkMonitor = new com.mjc.mascotalink.network.NetworkMonitorHelper(
            this, socketManager,
            new com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkCallback() {
                @Override
                public void onNetworkLost() {
                    runOnUiThread(() -> {
                        // Deshabilitar botón de confirmar durante pérdida de conexión
                        if (btnConfirmarReserva != null) {
                            btnConfirmarReserva.setEnabled(false);
                        }
                        Toast.makeText(ReservaActivity.this,
                            "⚠️ Sin conexión. No se puede crear la reserva.",
                            Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onNetworkAvailable() {
                    runOnUiThread(() -> {
                        // Re-habilitar botón si todos los datos están completos
                        verificarCamposCompletos();
                    });
                }

                @Override
                public void onReconnected() {
                    runOnUiThread(() -> {
                        Toast.makeText(ReservaActivity.this,
                            "✅ Conexión restaurada",
                            Toast.LENGTH_SHORT).show();
                        verificarCamposCompletos();
                    });
                }
            });

        networkMonitor.register();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
        // Dejar de escuchar cambios en tiempo real
        removeRealtimeListeners();
    }

    private void setupRealtimeListeners() {
        if (paseadorId == null) return;

        // Listener 1: Escuchar cambios en acepta_solicitudes del paseador
        ListenerRegistration aceptaSolicitudesListener = db.collection("usuarios")
                .document(paseadorId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error listening to acepta_solicitudes", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Boolean aceptaSolicitudes = snapshot.getBoolean("acepta_solicitudes");
                        boolean acepta = aceptaSolicitudes == null || aceptaSolicitudes;

                        if (!acepta) {
                            // El paseador ya no acepta solicitudes
                            Toast.makeText(ReservaActivity.this,
                                    "El paseador ha pausado la aceptación de solicitudes",
                                    Toast.LENGTH_SHORT).show();
                            btnConfirmarReserva.setEnabled(false);
                        } else {
                            // El paseador vuelve a aceptar solicitudes
                            if (fechaSeleccionada != null && horarioSeleccionado != null) {
                                cargarHorariosDisponibles();
                                verificarCamposCompletos();
                            }
                        }
                    }
                });
        realtimeListeners.add(aceptaSolicitudesListener);

        // Listener 2: Escuchar cambios en bloqueos del paseador
        ListenerRegistration bloqueosListener = db.collection("paseadores")
                .document(paseadorId)
                .collection("disponibilidad")
                .document("bloqueos")
                .collection("items")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error listening to bloqueos", e);
                        return;
                    }

                    // Si hay cambios en bloqueos, recalcular horarios disponibles
                    if (fechaSeleccionada != null && snapshot != null) {
                        cargarHorariosDisponibles();
                    }
                });
        realtimeListeners.add(bloqueosListener);

        // Listener 3: Escuchar cambios en horarios especiales del paseador
        ListenerRegistration horariosEspecialesListener = db.collection("paseadores")
                .document(paseadorId)
                .collection("disponibilidad")
                .document("horarios_especiales")
                .collection("items")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error listening to horarios_especiales", e);
                        return;
                    }

                    // Si hay cambios en horarios especiales, recalcular horarios disponibles
                    if (fechaSeleccionada != null && snapshot != null) {
                        cargarHorariosDisponibles();
                    }
                });
        realtimeListeners.add(horariosEspecialesListener);

        // Listener 4: Escuchar cambios en horario por defecto
        ListenerRegistration horarioDefaultListener = db.collection("paseadores")
                .document(paseadorId)
                .collection("disponibilidad")
                .document("horario_default")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error listening to horario_default", e);
                        return;
                    }

                    // Si hay cambios en horario por defecto, recalcular horarios disponibles
                    if (fechaSeleccionada != null && snapshot != null) {
                        cargarHorariosDisponibles();
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

    // Clase para espaciado horizontal consistente entre items del RecyclerView
    private static class HorizontalSpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontalSpaceWidth;

        public HorizontalSpaceItemDecoration(int horizontalSpaceWidth) {
            this.horizontalSpaceWidth = horizontalSpaceWidth;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, 
                                   RecyclerView parent, RecyclerView.State state) {
            // Agregar margen a la derecha de cada item (excepto el último)
            if (parent.getChildAdapterPosition(view) != parent.getAdapter().getItemCount() - 1) {
                outRect.right = horizontalSpaceWidth;
            }
        }
    }
}
