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
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReservaActivity extends AppCompatActivity {

    private static final String TAG = "ReservaActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

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

        initViews();
        setupListeners();
        setupBottomNavigation();
        cargarMascotasUsuario();
        setupCalendario();
        setupHorarios();

        tvPaseadorNombre.setText(paseadorNombre != null ? paseadorNombre : "Alex");
        tvTarifaValor.setText(String.format(Locale.US, "$%.1f/hora", tarifaPorHora));
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

            actualizarTextoDuracion();
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
        rvMascotas.setHasFixedSize(true);
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
    }

    // --- FIX INICIO: Nuevos métodos para cargar las vistas del calendario ---

    /**
     * Carga la vista del calendario para mostrar solo los 7 días de la semana actual.
     */
    private void cargarVistaSemanales() {
        // Ocultar flechas de navegación y cambiar título
        ivMesAnterior.setVisibility(View.INVISIBLE);
        ivMesSiguiente.setVisibility(View.INVISIBLE);
        tvMesAnio.setText("Esta Semana");

        datesList.clear();
        Calendar cal = Calendar.getInstance();
        
        // --- FIX: Lógica robusta para encontrar el Lunes de la semana actual ---
        // Retroceder día por día hasta encontrar el Lunes.
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DATE, -1);
        }

        // Generar los 7 días de la semana desde el Lunes
        for (int i = 0; i < 7; i++) {
            datesList.add(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (calendarioAdapter == null) {
             calendarioAdapter = new CalendarioAdapter(this, datesList, currentMonth, (date, position) -> {
                fechaSeleccionada = date;
                mostrarFechaSeleccionada(date);
                cargarHorariosDisponibles();
                verificarCamposCompletos();
            });
            gvCalendario.setAdapter(calendarioAdapter);
        } else {
            calendarioAdapter.updateDates(datesList, (Calendar) cal.clone());
        }
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
                fechaSeleccionada = date;
                mostrarFechaSeleccionada(date);
                cargarHorariosDisponibles();
                verificarCamposCompletos();
            });
            gvCalendario.setAdapter(calendarioAdapter);
        } else {
            calendarioAdapter.updateDates(datesList, currentMonth);
        }
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
    }

    private void generarHorariosBase() {
        horarioList.clear();
        for (int hora = 7; hora <= 19; hora++) {
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

        Calendar today = Calendar.getInstance();
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(fechaSeleccionada);

        boolean isToday = selectedCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

        for (HorarioSelectorAdapter.Horario horario : horarioList) {
            if (isToday && (horario.getHora() < today.get(Calendar.HOUR_OF_DAY) ||
                    (horario.getHora() == today.get(Calendar.HOUR_OF_DAY) &&
                            horario.getMinutos() <= today.get(Calendar.MINUTE)))) {
                horario.setDisponible(false);
            } else {
                horario.setDisponible(true);
            }
        }

        if (horarioAdapter != null) {
            horarioAdapter.notifyDataSetChanged();
        }
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
                tvDisponibilidad.setText("✓ Disponible");
                tvDisponibilidad.setTextColor(getResources().getColor(R.color.green_success));
                break;
            case "LIMITADO":
                tvDisponibilidad.setText("⚠ Limitado");
                tvDisponibilidad.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case "NO_DISPONIBLE":
                tvDisponibilidad.setText("No disponible");
                tvDisponibilidad.setTextColor(getResources().getColor(R.color.red_error));
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
            diasCalculo = 7;
        } else if (tipoReserva.equals("MENSUAL") && fechaSeleccionada != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(fechaSeleccionada);
            diasCalculo = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
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
        boolean todosCompletos = mascotaSeleccionada != null &&
                fechaSeleccionada != null &&
                horarioSeleccionado != null &&
                duracionMinutos > 0;
        btnConfirmarReserva.setEnabled(todosCompletos);
    }

    private void confirmarReserva() {
        if (!validarDatosReserva()) return;

        Map<String, Object> reserva = new HashMap<>();
        reserva.put("id_dueno", db.collection("usuarios").document(currentUserId));
        reserva.put("id_mascota", mascotaSeleccionada.getId());
        reserva.put("id_paseador", db.collection("usuarios").document(paseadorId));

        Calendar cal = Calendar.getInstance();
        cal.setTime(fechaSeleccionada);
        cal.set(Calendar.HOUR_OF_DAY, horarioSeleccionado.getHora());
        cal.set(Calendar.MINUTE, horarioSeleccionado.getMinutos());
        cal.set(Calendar.SECOND, 0);

        reserva.put("fecha", new Timestamp(fechaSeleccionada));
        reserva.put("hora_inicio", new Timestamp(cal.getTime()));
        reserva.put("duracion_minutos", duracionMinutos);
        reserva.put("costo_total", costoTotal);
        reserva.put("estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION);
        reserva.put("tipo_reserva", tipoReserva);
        reserva.put("fecha_creacion", Timestamp.now());
        reserva.put("tarifa_confirmada", tarifaPorHora);
        reserva.put("id_pago", null);           // NULL inicialmente
        reserva.put("estado_pago", ReservaEstadoValidator.ESTADO_PAGO_PENDIENTE); // Estado inicial de pago
        reserva.put("notas", notasAdicionalesMascota);
        reserva.put("reminderSent", false);


        db.collection("reservas")
                .add(reserva)
                .addOnSuccessListener(documentReference -> {
                    String reservaId = documentReference.getId();
                    Toast.makeText(this, "Reserva creada exitosamente", Toast.LENGTH_SHORT).show();

                    // Tras crear la reserva y guardarla en Firestore, regresa al perfil del dueño
                    Intent intent = new Intent(ReservaActivity.this, PerfilDuenoActivity.class);
                    intent.putExtra("reserva_id", reservaId);
                    intent.putExtra("reserva_estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al crear reserva: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
        if (fechaSeleccionada == null) {
            Toast.makeText(this, "Selecciona una fecha", Toast.LENGTH_SHORT).show();
            return false;
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
        // --- FIX FIN ---
        return true;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
