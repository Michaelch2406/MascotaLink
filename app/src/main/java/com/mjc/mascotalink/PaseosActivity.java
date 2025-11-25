package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.PropertyName; // Added import
import com.google.firebase.firestore.Query;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.ConfirmarPagoActivity;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

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
    private String userRole;

    // Views
    private TextView tabAceptados, tabProgramados, tabEnProgreso, tabCompletados, tabCancelados;
    private RecyclerView rvPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmptyView;
    private Button btnReservarPaseo;
    private BottomNavigationView bottomNav;
    private android.widget.ImageView ivBack; // Added

    // Adapter
    private PaseosAdapter paseosAdapter;
    private List<Paseo> paseosList;

    // Estado actual
    private String estadoActual = ReservaEstadoValidator.ESTADO_ACEPTADO; // Mostrar primero las reservas aceptadas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseos);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = currentUser.getUid();

        initViews();
        setupSwipeRefresh();
        
        // Try to load role from cache first for immediate UI setup
        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
            userRole = cachedRole;
            setupRecyclerView(userRole); // Added: Initialize adapter immediately
            setupRoleSpecificUI(userRole);
        }
        
        fetchUserRoleAndSetupUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Use current userRole which might be set from cache or fetch
        setupBottomNavigation();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back); // Added
        tabAceptados = findViewById(R.id.tab_aceptados);
        tabProgramados = findViewById(R.id.tab_programados);
        tabEnProgreso = findViewById(R.id.tab_en_progreso);
        tabCompletados = findViewById(R.id.tab_completados);
        tabCancelados = findViewById(R.id.tab_cancelados);
        rvPaseos = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmptyView = findViewById(R.id.ll_empty_view);
        btnReservarPaseo = findViewById(R.id.btn_reservar_paseo);
        bottomNav = findViewById(R.id.bottom_nav);

        if (ivBack != null) { // Added listener
            ivBack.setOnClickListener(v -> finish());
        }

        paseosList = new ArrayList<>();
    }

    private void fetchUserRoleAndSetupUI() {
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fetchedRole = documentSnapshot.getString("rol");
                        if (fetchedRole != null) {
                            // Save to cache
                            BottomNavManager.saveUserRole(this, fetchedRole);
                            
                            // Only update UI if role is different or wasn't set
                            if (!fetchedRole.equalsIgnoreCase(userRole) || paseosAdapter == null) {
                                userRole = fetchedRole;
                                setupRecyclerView(userRole);
                                setupRoleSpecificUI(userRole);
                            }
                            // Always load paseos
                            cargarPaseos(userRole);
                        } else {
                            handleRoleError();
                        }
                    } else {
                        handleRoleError();
                    }
                })
                .addOnFailureListener(e -> handleRoleError());
    }

    private void handleRoleError() {
        Toast.makeText(this, "No se pudo verificar el rol del usuario.", Toast.LENGTH_LONG).show();
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setupRoleSpecificUI(String role) {
        userRole = role;
        setupTabs(role);
        setupBottomNavigation();
        btnReservarPaseo.setVisibility("PASEADOR".equalsIgnoreCase(role) ? View.GONE : View.VISIBLE);
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        String roleForNav = userRole != null ? userRole : "DUEÑO";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_walks);
    }

    private void setupTabs(String role) {
        tabAceptados.setOnClickListener(v -> cambiarTab(ReservaEstadoValidator.ESTADO_ACEPTADO, tabAceptados, role));
        tabProgramados
                .setOnClickListener(v -> cambiarTab(ReservaEstadoValidator.ESTADO_CONFIRMADO, tabProgramados, role));
        tabEnProgreso.setOnClickListener(v -> cambiarTab("EN_CURSO", tabEnProgreso, role));
        tabCompletados.setOnClickListener(v -> cambiarTab("COMPLETADO", tabCompletados, role));
        tabCancelados.setOnClickListener(v -> cambiarTab("CANCELADO", tabCancelados, role));

        btnReservarPaseo.setOnClickListener(v -> {
            Intent intent = new Intent(PaseosActivity.this, BusquedaPaseadoresActivity.class);
            startActivity(intent);
        });

        // Seleccionar tab inicial
        cambiarTab(estadoActual, tabAceptados, role);
    }

    private void cambiarTab(String nuevoEstado, TextView tabSeleccionado, String role) {
        resetearTabs();
        tabSeleccionado.setTextColor(ContextCompat.getColor(this, R.color.blue_primary));
        tabSeleccionado.setTypeface(null, android.graphics.Typeface.BOLD);
        tabSeleccionado.setBackgroundResource(R.drawable.tab_selected_underline);
        estadoActual = nuevoEstado;
        cargarPaseos(role);
    }

    private void resetearTabs() {
        TextView[] tabs = { tabAceptados, tabProgramados, tabEnProgreso, tabCompletados, tabCancelados };
        for (TextView tab : tabs) {
            tab.setTextColor(ContextCompat.getColor(this, R.color.gray_text));
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
            tab.setBackground(null);
        }
    }

    private void setupRecyclerView(String role) {
        rvPaseos.setLayoutManager(new LinearLayoutManager(this));
        paseosAdapter = new PaseosAdapter(this, paseosList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(PaseosActivity.Paseo paseo) {
                if (esPaseoEnCurso(paseo)) {
                    if ("PASEADOR".equalsIgnoreCase(userRole)) {
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(PaseosActivity.this, "Abrir detalles de " + paseo.getPaseadorNombre(),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onVerUbicacionClick(PaseosActivity.Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Ver ubicación en mapa", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onContactarClick(PaseosActivity.Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Abrir chat con paseador", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCalificarClick(PaseosActivity.Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", paseo.getReservaId());
                startActivity(intent);
            }

            @Override
            public void onVerMotivoClick(PaseosActivity.Paseo paseo) {
                mostrarDialogMotivoCancelacion(paseo);
            }

            @Override
            public void onProcesarPagoClick(PaseosActivity.Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ConfirmarPagoActivity.class);
                intent.putExtra("reserva_id", paseo.getReservaId());
                intent.putExtra("costo_total", paseo.getCosto_total()); // Corrected getter
                intent.putExtra("paseador_nombre", paseo.getPaseadorNombre());
                intent.putExtra("mascota_nombre", paseo.getMascotaNombre());
                intent.putExtra("fecha_reserva", paseo.getFechaFormateada());
                intent.putExtra("hora_reserva", paseo.getHoraFormateada());
                intent.putExtra("direccion_recogida", "Calle Principal 123, Ciudad");
                startActivity(intent);
            }
        }, role);
        rvPaseos.setAdapter(paseosAdapter);
    }

    private boolean esPaseoEnCurso(Paseo paseo) {
        if (paseo == null || paseo.getEstado() == null)
            return false;
        String estado = paseo.getEstado();
        return "EN_CURSO".equalsIgnoreCase(estado) || "EN_PROGRESO".equalsIgnoreCase(estado);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            if (userRole != null) {
                cargarPaseos(userRole);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    private void cargarPaseos(String role) {
        swipeRefresh.setRefreshing(true);
        paseosList.clear();

        String fieldToFilter = "PASEADOR".equalsIgnoreCase(role) ? "id_paseador" : "id_dueno";
        DocumentReference userRef = db.collection("usuarios").document(currentUserId);

        Query query = db.collection("reservas")
                .whereEqualTo(fieldToFilter, userRef)
                .whereEqualTo("estado", estadoActual);

        // Query finalQuery = query.whereEqualTo("estado", estadoActual); // Removed redundant query assignment

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            List<Paseo> paseosTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Paseo paseo = doc.toObject(Paseo.class);
                if (paseo == null)
                    continue;
                paseo.setReservaId(doc.getId());
                String mascotaId = doc.getString("id_mascota"); // Declare mascotaId here
                paseo.setIdMascota(mascotaId); // Set mascotaId on paseo object early

                paseosTemporales.add(paseo);

                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String currentMascotaId = paseo.getIdMascota(); // Use the one already set on paseo

                tareas.add(paseadorRef != null ? paseadorRef.get() : Tasks.forResult(null));
                tareas.add(duenoRef != null ? duenoRef.get() : Tasks.forResult(null));
                if (duenoRef != null && currentMascotaId != null && !currentMascotaId.isEmpty()) { // Corrected logic to
                                                                                                   // use
                                                                                                   // currentMascotaId
                    tareas.add(db.collection("duenos").document(duenoRef.getId()).collection("mascotas")
                            .document(currentMascotaId).get());
                } else {
                    tareas.add(Tasks.forResult(null));
                }
            }

            // Sort client-side to avoid composite index requirements
            java.util.Collections.sort(paseosTemporales, (p1, p2) -> {
                if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                return p2.getFecha().compareTo(p1.getFecha()); // Descending order
            });

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                Date now = new Date(); // Obtener fecha/hora actual una vez
                List<Paseo> nuevosPaseos = new ArrayList<>();

                for (int i = 0; i < paseosTemporales.size(); i++) {
                    Paseo paseo = paseosTemporales.get(i);

                    // --- FIX: Autocorrección de Estado (Cliente-Servidor) ---
                    // Si es CONFIRMADO y la hora ya pasó, forzar EN_CURSO local y remotamente.
                    if ("CONFIRMADO".equals(paseo.getEstado()) && paseo.getHora_inicio() != null && paseo.getHora_inicio().before(now)) {
                        Log.i(TAG, "Detectado paseo confirmado vencido: " + paseo.getReservaId() + ". Actualizando a EN_CURSO.");
                        
                        // Actualizar UI inmediatamente
                        paseo.setEstado("EN_CURSO");
                        
                        // Actualizar Firestore en segundo plano
                        db.collection("reservas").document(paseo.getReservaId())
                                .update("estado", "EN_CURSO", 
                                        "hasTransitionedToInCourse", true,
                                        "fecha_inicio_paseo", new com.google.firebase.Timestamp(paseo.getHora_inicio()))
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Reserva actualizada a EN_CURSO automÃ¡ticamente."))
                                .addOnFailureListener(e -> Log.e(TAG, "Error al auto-actualizar reserva: " + e.getMessage()));
                    }
                    // --- FIN FIX ---

                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(i * 3);
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(i * 3 + 1);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(i * 3 + 2);

                    if (paseadorDoc != null && paseadorDoc.exists()) {
                        String paseadorFotoUrl = paseadorDoc.getString("foto_perfil");
                        if (paseadorFotoUrl == null || paseadorFotoUrl.isEmpty()) {
                            Log.w(TAG, "Paseador " + paseadorDoc.getId() + " for reservation " + paseo.getReservaId()
                                    + " has no 'foto_perfil' URL or it's empty.");
                        } else {
                            Log.d(TAG, "Paseador " + paseadorDoc.getId() + " foto_perfil: " + paseadorFotoUrl);
                        }
                        paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                        paseo.setPaseadorFoto(paseadorFotoUrl);
                    } else {
                        Log.w(TAG, "Paseador document not found or does not exist for reservation "
                                + paseo.getReservaId());
                    }
                    if (duenoDoc != null && duenoDoc.exists()) {
                        String duenoFotoUrl = duenoDoc.getString("foto_perfil");
                        if (duenoFotoUrl == null || duenoFotoUrl.isEmpty()) {
                            Log.w(TAG, "Dueno " + duenoDoc.getId() + " for reservation " + paseo.getReservaId()
                                    + " has no 'foto_perfil' URL or it's empty.");
                        } else {
                            Log.d(TAG, "Dueno " + duenoDoc.getId() + " foto_perfil: " + duenoFotoUrl);
                        }
                        paseo.setDuenoNombre(duenoDoc.getString("nombre_display"));
                        // Note: duenoFoto is not used by the adapter, so not setting it on paseo
                        // object.
                    } else {
                        Log.w(TAG,
                                "Dueno document not found or does not exist for reservation " + paseo.getReservaId());
                    }
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        String mascotaFotoUrl = mascotaDoc.getString("foto_principal_url");
                        if (mascotaFotoUrl == null || mascotaFotoUrl.isEmpty()) {
                            Log.w(TAG, "Mascota " + mascotaDoc.getId() + " for reservation " + paseo.getReservaId()
                                    + " has no 'foto_principal_url' URL or it's empty.");
                        } else {
                            Log.d(TAG, "Mascota " + mascotaDoc.getId() + " foto_principal_url: " + mascotaFotoUrl);
                        }
                        paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        paseo.setMascotaFoto(mascotaFotoUrl);
                    } else {
                        Log.w(TAG, "Mascota document not found or does not exist for reservation "
                                + paseo.getReservaId() + " (ID: " + paseo.getIdMascota() + ")");
                        paseo.setMascotaNombre("Mascota no encontrada");
                    }
                    nuevosPaseos.add(paseo);
                }

                // Sort client-side
                java.util.Collections.sort(nuevosPaseos, (p1, p2) -> {
                    if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                    return p2.getFecha().compareTo(p1.getFecha()); // Descending order
                });

                paseosList.clear();
                paseosList.addAll(nuevosPaseos);

                if (paseosAdapter != null) {
                    paseosAdapter.updateList(paseosList); // Corrected: Use updateList to refresh adapter data
                }
                finalizarCarga();
            }).addOnFailureListener(this::manejarError);
        }).addOnFailureListener(this::manejarError);
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
        Log.e(TAG, "Error al cargar paseos: ", e);
        Toast.makeText(this, "Error al cargar paseos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        rvPaseos.setVisibility(View.GONE);
        llEmptyView.setVisibility(View.VISIBLE);
    }

    private void mostrarDialogCalificacion(Paseo paseo) {
        if (isFinishing())
            return;
        new AlertDialog.Builder(this)
                .setTitle("Calificar")
                .setMessage("Próximamente podrás calificar este paseo.")
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void mostrarDialogMotivoCancelacion(Paseo paseo) {
        if (isFinishing())
            return;
        String motivo = paseo.getRazonCancelacion();
        if (motivo == null || motivo.isEmpty())
            motivo = "No se especificó un motivo";
        new AlertDialog.Builder(this)
                .setTitle("Motivo de Cancelación")
                .setMessage(motivo)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    public static class Paseo {
        private String reservaId;
        private String paseadorNombre, paseadorFoto, duenoNombre, mascotaNombre, mascotaFoto;
        @PropertyName("id_mascota")
        private String idMascota;
        private DocumentReference id_dueno; // Corrected type
        private DocumentReference id_paseador; // Corrected type
        private Date fecha;
        private Date hora_inicio; // Corrected field name
        private String estado, razonCancelacion;
        private String tipo_reserva; // Added field
        private String estado_pago; // Added field
        private double costo_total; // Added field
        private long duracion_minutos; // Added field
        private String id_pago; // Added field
        private String transaction_id; // Added field
        private Date fecha_pago; // Added field
        private String metodo_pago; // Added field
        private String notas; // Added field
        private Date fecha_creacion; // Added field
        private Date fecha_respuesta; // Added field
        private Double tarifa_confirmada; // Added field
        private Boolean hasTransitionedToInCourse; // Added field
        @PropertyName("reminderSent")
        private Boolean reminderSent;

        public Paseo() {
        }

        // Getters
        public String getReservaId() {
            return reservaId;
        }

        public String getPaseadorNombre() {
            return paseadorNombre;
        }

        public String getPaseadorFoto() {
            return paseadorFoto;
        }

        public String getDuenoNombre() {
            return duenoNombre;
        }

        public String getMascotaNombre() {
            return mascotaNombre;
        }

        public String getMascotaFoto() {
            return mascotaFoto;
        }

        @PropertyName("id_mascota")
        public String getIdMascota() {
            return idMascota;
        }

        public DocumentReference getId_dueno() {
            return id_dueno;
        }

        public DocumentReference getId_paseador() {
            return id_paseador;
        }

        public Date getFecha() {
            return fecha;
        }

        public Date getHora_inicio() {
            return hora_inicio;
        } // Corrected getter

        public String getEstado() {
            return estado;
        }

        public String getRazonCancelacion() {
            return razonCancelacion;
        }

        public String getTipo_reserva() {
            return tipo_reserva;
        }

        public String getEstado_pago() {
            return estado_pago;
        }

        public double getCosto_total() {
            return costo_total;
        }

        public long getDuracion_minutos() {
            return duracion_minutos;
        }

        public String getId_pago() {
            return id_pago;
        }

        public String getTransaction_id() {
            return transaction_id;
        }

        public Date getFecha_pago() {
            return fecha_pago;
        }

        public String getMetodo_pago() {
            return metodo_pago;
        }

        public String getNotas() {
            return notas;
        }

        public Date getFecha_creacion() {
            return fecha_creacion;
        }

        public Date getFecha_respuesta() {
            return fecha_respuesta;
        }

        public Double getTarifa_confirmada() {
            return tarifa_confirmada;
        }

        public Boolean getHasTransitionedToInCourse() {
            return hasTransitionedToInCourse;
        }

        @PropertyName("reminderSent")
        public Boolean getReminderSent() {
            return reminderSent;
        }

        // Setters
        public void setReservaId(String reservaId) {
            this.reservaId = reservaId;
        }

        public void setPaseadorNombre(String paseadorNombre) {
            this.paseadorNombre = paseadorNombre;
        }

        public void setPaseadorFoto(String paseadorFoto) {
            this.paseadorFoto = paseadorFoto;
        }

        public void setDuenoNombre(String duenoNombre) {
            this.duenoNombre = duenoNombre;
        }

        public void setMascotaNombre(String mascotaNombre) {
            this.mascotaNombre = mascotaNombre;
        }

        public void setMascotaFoto(String mascotaFoto) {
            this.mascotaFoto = mascotaFoto;
        }

        @PropertyName("id_mascota")
        public void setIdMascota(String idMascota) {
            this.idMascota = idMascota;
        }

        public void setId_dueno(DocumentReference id_dueno) {
            this.id_dueno = id_dueno;
        }

        public void setId_paseador(DocumentReference id_paseador) {
            this.id_paseador = id_paseador;
        }

        public void setFecha(Date fecha) {
            this.fecha = fecha;
        }

        public void setHora_inicio(Date hora_inicio) {
            this.hora_inicio = hora_inicio;
        }

        public void setEstado(String estado) {
            this.estado = estado;
        }

        public void setRazonCancelacion(String razonCancelacion) {
            this.razonCancelacion = razonCancelacion;
        }

        public void setTipo_reserva(String tipo_reserva) {
            this.tipo_reserva = tipo_reserva;
        }

        public void setEstado_pago(String estado_pago) {
            this.estado_pago = estado_pago;
        }

        public void setCosto_total(double costo_total) {
            this.costo_total = costo_total;
        }

        public void setDuracion_minutos(long duracion_minutos) {
            this.duracion_minutos = duracion_minutos;
        }

        public void setId_pago(String id_pago) {
            this.id_pago = id_pago;
        }

        public void setTransaction_id(String transaction_id) {
            this.transaction_id = transaction_id;
        }

        public void setFecha_pago(Date fecha_pago) {
            this.fecha_pago = fecha_pago;
        }

        public void setMetodo_pago(String metodo_pago) {
            this.metodo_pago = metodo_pago;
        }

        public void setNotas(String notas) {
            this.notas = notas;
        }

        public void setFecha_creacion(Date fecha_creacion) {
            this.fecha_creacion = fecha_creacion;
        }

        public void setFecha_respuesta(Date fecha_respuesta) {
            this.fecha_respuesta = fecha_respuesta;
        }

        public void setTarifa_confirmada(Double tarifa_confirmada) {
            this.tarifa_confirmada = tarifa_confirmada;
        }

        public void setHasTransitionedToInCourse(Boolean hasTransitionedToInCourse) {
            this.hasTransitionedToInCourse = hasTransitionedToInCourse;
        }

        @PropertyName("reminderSent")
        public void setReminderSent(Boolean reminderSent) {
            this.reminderSent = reminderSent;
        }

        public String getFechaFormateada() {
            if (fecha == null)
                return "";
            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            Calendar paseoDate = Calendar.getInstance();
            paseoDate.setTime(fecha);
            if (today.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR)
                    && today.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR))
                return "Hoy";
            if (tomorrow.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR)
                    && tomorrow.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR))
                return "Mañana";
            return new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES")).format(fecha);
        }

        public String getHoraFormateada() {
            if (hora_inicio == null)
                return ""; // Corrected to hora_inicio
            return new SimpleDateFormat("h:mm a", Locale.US).format(hora_inicio);
        }
    }
}