package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaseosActivity extends AppCompatActivity {

    private static final String TAG = "PaseosActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    // Views
    private TextView tabActivos, tabEnProgreso, tabCompletados, tabCancelados;
    private RecyclerView rvPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmptyView;
    private Button btnReservarPaseo;
    private BottomNavigationView bottomNav;

    // Adapter
    private PaseosAdapter paseosAdapter;
    private List<Paseo> paseosList;

    // Estado actual
    private String estadoActual = "EN_CURSO"; // Tab inicial: EN PROGRESO

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseos);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentUserId = currentUser.getUid();

        initViews();
        setupTabs();
        setupRecyclerView();
        setupSwipeRefresh();
        setupBottomNavigation();

        // Cargar paseos iniciales (EN PROGRESO)
        cargarPaseos();
    }

    private void initViews() {
        tabActivos = findViewById(R.id.tab_activos);
        tabEnProgreso = findViewById(R.id.tab_en_progreso);
        tabCompletados = findViewById(R.id.tab_completados);
        tabCancelados = findViewById(R.id.tab_cancelados);
        rvPaseos = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmptyView = findViewById(R.id.ll_empty_view);
        btnReservarPaseo = findViewById(R.id.btn_reservar_paseo);
        bottomNav = findViewById(R.id.bottom_nav);
        
        paseosList = new ArrayList<>();
    }

    private void setupTabs() {
        tabActivos.setOnClickListener(v -> cambiarTab("ACTIVOS", tabActivos));
        tabEnProgreso.setOnClickListener(v -> cambiarTab("EN_CURSO", tabEnProgreso));
        tabCompletados.setOnClickListener(v -> cambiarTab("COMPLETADO", tabCompletados));
        tabCancelados.setOnClickListener(v -> cambiarTab("CANCELADO", tabCancelados));
        
        btnReservarPaseo.setOnClickListener(v -> {
            // Navegar a BusquedaPaseadoresActivity
            Intent intent = new Intent(PaseosActivity.this, com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity.class);
            startActivity(intent);
        });
    }

    private void cambiarTab(String nuevoEstado, TextView tabSeleccionado) {
        // Resetear todos los tabs
        resetearTabs();
        
        // Activar tab seleccionado
        tabSeleccionado.setTextColor(getResources().getColor(R.color.blue_primary));
        tabSeleccionado.setTypeface(null, android.graphics.Typeface.BOLD);
        tabSeleccionado.setBackgroundResource(R.drawable.tab_selected_underline);
        
        // Actualizar estado y recargar
        estadoActual = nuevoEstado;
        cargarPaseos();
    }

    private void resetearTabs() {
        TextView[] tabs = {tabActivos, tabEnProgreso, tabCompletados, tabCancelados};
        for (TextView tab : tabs) {
            tab.setTextColor(getResources().getColor(R.color.gray_text));
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
            tab.setBackground(null);
        }
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvPaseos.setLayoutManager(layoutManager);
        
        paseosAdapter = new PaseosAdapter(this, paseosList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(Paseo paseo) {
                // Abrir detalles del paseo
                Toast.makeText(PaseosActivity.this, "Abrir detalles de " + paseo.getPaseadorNombre(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerUbicacionClick(Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Ver ubicación en mapa", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onContactarClick(Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Abrir chat con paseador", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCalificarClick(Paseo paseo) {
                mostrarDialogCalificacion(paseo);
            }

            @Override
            public void onVerMotivoClick(Paseo paseo) {
                mostrarDialogMotivoCancelacion(paseo);
            }
        });
        
        rvPaseos.setAdapter(paseosAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            cargarPaseos();
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    private void setupBottomNavigation() {
        bottomNav.setSelectedItemId(R.id.menu_walks);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_walks) {
                // Ya estamos aquí
                return true;
            } else if (itemId == R.id.menu_home) { // 'Inicio' te lleva al perfil
                Intent intent = new Intent(this, PerfilDuenoActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_search) { // 'Buscar'
                Intent intent = new Intent(this, com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(this, "Próximamente: Mensajes", Toast.LENGTH_SHORT).show();
                return false; // No cambiar la selección
            } else if (itemId == R.id.menu_perfil) {
                Intent intent = new Intent(this, PerfilDuenoActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void cargarPaseos() {
        swipeRefresh.setRefreshing(true);
        paseosList.clear();

        // --- FIX INICIO: Refactorización completa de la carga de datos ---
        // RIESGO: El método anterior realizaba múltiples llamadas a la red en secuencia
        // y notificaba al adaptador dentro de un bucle, causando un rendimiento muy bajo
        // y un alto consumo de recursos (N+1 queries problem).
        // SOLUCIÓN: Se utiliza Tasks.whenAllSuccess para paralelizar las consultas.
        // 1. Se obtiene la lista de reservas.
        // 2. Se crea una lista de tareas para buscar todos los paseadores y mascotas a la vez.
        // 3. Solo cuando TODAS las tareas terminan, se procesan los datos y se notifica
        //    al adaptador UNA SOLA VEZ. Esto mejora drásticamente el tiempo de carga.

        Query query = db.collection("reservas")
                .whereEqualTo("id_dueno", db.collection("usuarios").document(currentUserId))
                .orderBy("fecha", Query.Direction.DESCENDING);

        // El filtro para "ACTIVOS" requiere un manejo especial post-consulta
        boolean filtrarActivosLocalmente = "ACTIVOS".equals(estadoActual);

        Query finalQuery = filtrarActivosLocalmente ? query : query.whereEqualTo("estado", estadoActual);

        finalQuery.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            List<Paseo> paseosTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                // Filtro local para el estado "ACTIVOS"
                String estado = doc.getString("estado");
                String estadoPago = doc.getString("estado_pago");
                if (filtrarActivosLocalmente) {
                    if (!("PROCESADO".equals(estadoPago) && ("CONFIRMADO".equals(estado) || "EN_CURSO".equals(estado)))) {
                        continue; // Saltar este documento si no cumple la condición de "ACTIVO"
                    }
                } else {
                    // Para otros tabs, solo verificar que esté pagado
                    if (!"PROCESADO".equals(estadoPago)) {
                        continue;
                    }
                }

                Paseo paseo = new Paseo();
                paseo.setReservaId(doc.getId());
                paseo.setEstado(doc.getString("estado"));
                paseo.setFecha(doc.getTimestamp("fecha") != null ? doc.getTimestamp("fecha").toDate() : new Date());
                paseo.setHoraInicio(doc.getTimestamp("hora_inicio") != null ? doc.getTimestamp("hora_inicio").toDate() : new Date());
                paseo.setIdMascota(doc.getString("id_mascota"));
                paseo.setCostoTotal(doc.getDouble("costo_total"));
                paseo.setDuracionMinutos(doc.getLong("duracion_minutos") != null ? doc.getLong("duracion_minutos").intValue() : 0);
                paseo.setRazonCancelacion(doc.getString("razon_cancelacion"));
                paseo.setTipoReserva(doc.getString("tipo_reserva"));
                
                paseosTemporales.add(paseo);

                // Añadir tareas de obtención de datos relacionados
                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                if (paseadorRef != null) {
                    tareas.add(paseadorRef.get());
                } else {
                    tareas.add(null); // Placeholder para mantener el orden
                }

                if (paseo.getIdMascota() != null && !paseo.getIdMascota().isEmpty()) {
                    // FIX: Corregir la ruta de la subcolección de mascotas
                    tareas.add(db.collection("duenos").document(currentUserId).collection("mascotas").document(paseo.getIdMascota()).get());
                } else {
                    tareas.add(null); // Placeholder
                }
            }

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            // Ejecutar todas las tareas en paralelo
            com.google.android.gms.tasks.Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                for (int i = 0; i < paseosTemporales.size(); i++) {
                    Paseo paseo = paseosTemporales.get(i);
                    
                    // El resultado corresponde a paseadorRef.get()
                    Object paseadorResult = results.get(i * 2);
                    if (paseadorResult instanceof DocumentSnapshot) {
                        DocumentSnapshot paseadorDoc = (DocumentSnapshot) paseadorResult;
                        if (paseadorDoc.exists()) {
                            // FIX: Usar 'nombre_display' para obtener el nombre completo
                            paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                            paseo.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                        }
                    }

                    // El resultado corresponde a mascota.get()
                    Object mascotaResult = results.get(i * 2 + 1);
                    if (mascotaResult instanceof DocumentSnapshot) {
                        DocumentSnapshot mascotaDoc = (DocumentSnapshot) mascotaResult;
                        if (mascotaDoc.exists()) {
                            paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        } else {
                            paseo.setMascotaNombre("Mascota eliminada");
                        }
                    } else {
                        paseo.setMascotaNombre("Mascota no especificada");
                    }
                    
                    paseosList.add(paseo);
                }

                if (paseosAdapter != null) {
                    paseosAdapter.notifyDataSetChanged();
                }
                finalizarCarga();

            }).addOnFailureListener(this::manejarError);

        }).addOnFailureListener(this::manejarError);
        // --- FIX FIN ---
    }

    private void finalizarCarga() {
        swipeRefresh.setRefreshing(false);
        
        if (paseosList.isEmpty()) {
            rvPaseos.setVisibility(View.GONE);
            llEmptyView.setVisibility(View.VISIBLE);
        } else {
            rvPaseos.setVisibility(View.VISIBLE);
            llEmptyView.setVisibility(View.GONE);
        }
    }

    private void manejarError(Exception e) {
        swipeRefresh.setRefreshing(false);
        Toast.makeText(this, "Error al cargar paseos", Toast.LENGTH_SHORT).show();
        rvPaseos.setVisibility(View.GONE);
        llEmptyView.setVisibility(View.VISIBLE);
    }

    private void mostrarDialogCalificacion(Paseo paseo) {
        // --- FIX: Añadir check de isFinishing() ---
        // RIESGO: Mostrar un diálogo si la actividad se está cerrando puede causar un crash.
        // SOLUCIÓN: Se verifica el estado de la actividad antes de mostrar el diálogo.
        if (isFinishing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Calificar Paseador");
        builder.setMessage("¿Cómo fue tu experiencia con " + paseo.getPaseadorNombre() + "?");
        
        // Aquí se puede implementar un layout custom con RatingBar
        builder.setPositiveButton("Calificar", (dialog, which) -> {
            Toast.makeText(this, "Calificación guardada", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void mostrarDialogMotivoCancelacion(Paseo paseo) {
        // --- FIX: Añadir check de isFinishing() ---
        if (isFinishing()) {
            return;
        }
        String motivo = paseo.getRazonCancelacion();
        if (motivo == null || motivo.isEmpty()) {
            motivo = "No se especificó un motivo";
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Motivo de Cancelación")
                .setMessage(motivo)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    // Clase modelo Paseo
    public static class Paseo {
        private String reservaId;
        private String paseadorNombre;
        private String paseadorFoto;
        private String mascotaNombre;
        private String idMascota;
        private Date fecha;
        private Date horaInicio;
        private String estado;
        private double costoTotal;
        private int duracionMinutos;
        private String razonCancelacion;
        private String tipoReserva;

        public Paseo() {}

        // Getters y Setters
        public String getReservaId() { return reservaId; }
        public void setReservaId(String reservaId) { this.reservaId = reservaId; }

        public String getPaseadorNombre() { return paseadorNombre; }
        public void setPaseadorNombre(String paseadorNombre) { this.paseadorNombre = paseadorNombre; }

        public String getPaseadorFoto() { return paseadorFoto; }
        public void setPaseadorFoto(String paseadorFoto) { this.paseadorFoto = paseadorFoto; }

        public String getMascotaNombre() { return mascotaNombre; }
        public void setMascotaNombre(String mascotaNombre) { this.mascotaNombre = mascotaNombre; }

        public String getIdMascota() { return idMascota; }
        public void setIdMascota(String idMascota) { this.idMascota = idMascota; }

        public Date getFecha() { return fecha; }
        public void setFecha(Date fecha) { this.fecha = fecha; }

        public Date getHoraInicio() { return horaInicio; }
        public void setHoraInicio(Date horaInicio) { this.horaInicio = horaInicio; }

        public String getEstado() { return estado; }
        public void setEstado(String estado) { this.estado = estado; }

        public double getCostoTotal() { return costoTotal; }
        public void setCostoTotal(double costoTotal) { this.costoTotal = costoTotal; }

        public int getDuracionMinutos() { return duracionMinutos; }
        public void setDuracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; }

        public String getRazonCancelacion() { return razonCancelacion; }
        public void setRazonCancelacion(String razonCancelacion) { this.razonCancelacion = razonCancelacion; }

        public String getTipoReserva() { return tipoReserva; }
        public void setTipoReserva(String tipoReserva) { this.tipoReserva = tipoReserva; }


        public String getFechaFormateada() {
            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            
            Calendar paseoDate = Calendar.getInstance();
            paseoDate.setTime(fecha);
            
            if (today.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR)) {
                return "Hoy";
            } else if (tomorrow.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR) &&
                       tomorrow.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR)) {
                return "Mañana";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
                return sdf.format(fecha);
            }
        }

        public String getHoraFormateada() {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
            return sdf.format(horaInicio);
        }
    }
}
