package com.mjc.mascotalink;

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
import com.mjc.mascotalink.adapters.ConfiguracionesAdapter;
import com.mjc.mascotalink.fragments.DialogBloquearDiasFragment;
import com.mjc.mascotalink.fragments.DialogHorarioDefaultFragment;
import com.mjc.mascotalink.fragments.DialogHorarioEspecialFragment;
import com.mjc.mascotalink.modelo.Bloqueo;
import com.mjc.mascotalink.modelo.HorarioEspecial;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
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
        // Navegación de mes
        btnMesAnterior.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, -1);
            actualizarCalendario();
        });

        btnMesSiguiente.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, 1);
            actualizarCalendario();
        });

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
        calendarioAdapter.setDiasBloqueados(diasBloqueados);
        calendarioAdapter.setDiasParciales(diasParciales);

        gridCalendario.setAdapter(calendarioAdapter);

        // Recargar datos de disponibilidad para el mes actual
        cargarDisponibilidadDelMes();
    }

    private List<Date> generarFechasDelMes(Calendar mes) {
        List<Date> fechas = new ArrayList<>();
        Calendar cal = (Calendar) mes.clone();

        // Primer día del mes
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int primerDiaSemana = cal.get(Calendar.DAY_OF_WEEK); // 1=Dom, 2=Lun, ...

        // Calcular días vacíos al inicio (para alinear con día de la semana)
        int diasVacios = primerDiaSemana - 1; // 0=Dom, 1=Lun, ...
        for (int i = 0; i < diasVacios; i++) {
            fechas.add(null); // Días vacíos
        }

        // Agregar todos los días del mes
        int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int dia = 1; dia <= diasDelMes; dia++) {
            cal.set(Calendar.DAY_OF_MONTH, dia);
            fechas.add(cal.getTime());
        }

        return fechas;
    }

    private void mostrarDetalleDelDia(Date fecha) {
        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            return;
        }
        // Mostrar diálogo con opciones para el día seleccionado
        DialogHorarioEspecialFragment dialog = DialogHorarioEspecialFragment.newInstance(currentUserId, fecha);
        dialog.setOnHorarioEspecialGuardadoListener(() -> {
            cargarDatosDisponibilidad();
            Toast.makeText(this, "Horario especial guardado", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getSupportFragmentManager(), "horario_especial");
    }

    private void setupTarjetasAccion() {
        // Tarjeta 1: Horario Estándar
        cardHorarioEstandar.setOnClickListener(v -> {
            if (currentUserId == null) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            DialogHorarioDefaultFragment dialog = DialogHorarioDefaultFragment.newInstance(currentUserId);
            dialog.setOnHorarioGuardadoListener(() -> {
                cargarDatosDisponibilidad();
                Toast.makeText(this, "Horario por defecto actualizado", Toast.LENGTH_SHORT).show();
            });
            dialog.show(getSupportFragmentManager(), "horario_default");
        });

        // Tarjeta 2: Bloquear Días
        cardBloquearDias.setOnClickListener(v -> {
            if (currentUserId == null) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            DialogBloquearDiasFragment dialog = DialogBloquearDiasFragment.newInstance(currentUserId);
            dialog.setOnDiasBloqueadosListener(cantidad -> {
                cargarDatosDisponibilidad();
            });
            dialog.show(getSupportFragmentManager(), "bloquear_dias");
        });

        // Tarjeta 3: Horarios Especiales
        cardHorariosEspeciales.setOnClickListener(v -> {
            if (currentUserId == null) {
                Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }
            // Mostrar diálogo para seleccionar fecha primero
            DialogHorarioEspecialFragment dialog = DialogHorarioEspecialFragment.newInstance(currentUserId, null);
            dialog.setOnHorarioEspecialGuardadoListener(() -> {
                cargarDatosDisponibilidad();
            });
            dialog.show(getSupportFragmentManager(), "horario_especial");
        });
    }

    private void setupConfiguracionesActivas() {
        rvConfiguracionesActivas.setLayoutManager(new LinearLayoutManager(this));
        configuracionesAdapter = new ConfiguracionesAdapter(this);
        rvConfiguracionesActivas.setAdapter(configuracionesAdapter);
    }

    private void cargarDatosDisponibilidad() {
        if (currentUserId == null) return;

        // Cargar horario por defecto
        cargarHorarioDefault();

        // Cargar bloqueos y horarios especiales
        cargarConfiguracionesActivas();

        // Cargar disponibilidad del mes actual en calendario
        cargarDisponibilidadDelMes();
    }

    private void cargarHorarioDefault() {
        if (currentUserId == null || tvHorarioEstandarDesc == null) return;

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

    private String construirDescripcionHorario(Map<String, Object> data) {
        // Construir descripción legible del horario
        // Por ejemplo: "Lun-Vie: 9:00 - 18:00"
        StringBuilder desc = new StringBuilder();

        Map<String, Object> lunes = (Map<String, Object>) data.get("lunes");
        if (lunes != null && Boolean.TRUE.equals(lunes.get("disponible"))) {
            String inicio = (String) lunes.get("hora_inicio");
            String fin = (String) lunes.get("hora_fin");
            desc.append("Lun-Vie: ").append(inicio).append(" - ").append(fin);
        } else {
            desc.append("Sin configurar");
        }

        return desc.toString();
    }

    private void cargarConfiguracionesActivas() {
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
        if (currentUserId == null) return;

        // Limpiar sets
        diasDisponibles.clear();
        diasBloqueados.clear();
        diasParciales.clear();

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
                                } else {
                                    diasParciales.add(fechaNormalizada);
                                }
                            }
                        }
                    }

                    // Actualizar calendario
                    if (calendarioAdapter != null) {
                        calendarioAdapter.setDiasBloqueados(diasBloqueados);
                        calendarioAdapter.setDiasParciales(diasParciales);
                        calendarioAdapter.notifyDataSetChanged();
                    }
                });

        // TODO: Cargar días disponibles basados en horario por defecto
        // Por ahora, todos los días no bloqueados se consideran disponibles si hay horario configurado
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

    private String formatearFecha(Date fecha) {
        if (fecha == null) return "Fecha no disponible";

        SimpleDateFormat sdf = new SimpleDateFormat("EEE d 'de' MMM", new Locale("es", "ES"));
        return sdf.format(fecha);
    }
}
