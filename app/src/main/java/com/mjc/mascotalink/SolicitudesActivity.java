package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SolicitudesActivity extends AppCompatActivity {

    private static final String TAG = "SolicitudesActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    // Views
    private ImageButton ivBack;
    private RecyclerView rvSolicitudes;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmptyView;
    private BottomNavigationView bottomNav;
    private String userRole = "PASEADOR";

    // Adapter
    private SolicitudesAdapter solicitudesAdapter;
    private List<Solicitud> solicitudesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solicitudes);

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
        setupRecyclerView();
        setupSwipeRefresh();

        // Cargar solicitudes iniciales
        cargarSolicitudes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        rvSolicitudes = findViewById(R.id.rv_solicitudes);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmptyView = findViewById(R.id.ll_empty_view);
        bottomNav = findViewById(R.id.bottom_nav);
        
        solicitudesList = new ArrayList<>();

        // Botón atrás
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        String roleForNav = userRole != null ? userRole : "PASEADOR";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_search);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSolicitudes.setLayoutManager(layoutManager);
        
        solicitudesAdapter = new SolicitudesAdapter(this, solicitudesList, new SolicitudesAdapter.OnSolicitudClickListener() {
            @Override
            public void onSolicitudClick(Solicitud solicitud) {
                // Navegar a detalle de solicitud
                Intent intent = new Intent(SolicitudesActivity.this, SolicitudDetalleActivity.class);
                intent.putExtra("id_reserva", solicitud.getReservaId());
                startActivity(intent);
            }

            @Override
            public void onAceptarClick(Solicitud solicitud) {
                // Navegar a detalle de solicitud (mismo comportamiento que click en tarjeta)
                Intent intent = new Intent(SolicitudesActivity.this, SolicitudDetalleActivity.class);
                intent.putExtra("id_reserva", solicitud.getReservaId());
                startActivity(intent);
            }
        });
        
        rvSolicitudes.setAdapter(solicitudesAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            cargarSolicitudes();
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    private void cargarSolicitudes() {
        swipeRefresh.setRefreshing(true);
        solicitudesList.clear();

        // Consultar reservas donde el paseador es el actual y el estado es PENDIENTE_ACEPTACION
        Query query = db.collection("reservas")
                .whereEqualTo("id_paseador", db.collection("usuarios").document(currentUserId))
                .whereEqualTo("estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION)
                .orderBy("fecha_creacion", Query.Direction.DESCENDING);

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            final int totalSolicitudes = querySnapshot.size();
            final int[] solicitudesProcesadas = {0};

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Solicitud solicitud = new Solicitud();
                solicitud.setReservaId(doc.getId());
                solicitud.setFechaCreacion(doc.getTimestamp("fecha_creacion") != null ? doc.getTimestamp("fecha_creacion").toDate() : new Date());
                solicitud.setHoraInicio(doc.getTimestamp("hora_inicio") != null ? doc.getTimestamp("hora_inicio").toDate() : new Date());

                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String idMascota = doc.getString("id_mascota");
                solicitud.setIdDueno(duenoRef);
                solicitud.setIdMascota(idMascota);

                // Tareas para obtener datos relacionados (dueño y mascota)
                Task<DocumentSnapshot> duenoTask = null;
                if (duenoRef != null) {
                    duenoTask = duenoRef.get();
                }

                Task<DocumentSnapshot> mascotaTask = null;
                if (duenoRef != null && idMascota != null && !idMascota.isEmpty()) {
                    String duenoId = duenoRef.getId();
                    mascotaTask = db.collection("duenos").document(duenoId).collection("mascotas").document(idMascota).get();
                }

                // Combinar tareas y procesar resultados
                Task<DocumentSnapshot> finalDuenoTask = duenoTask;
                Task<DocumentSnapshot> finalMascotaTask = mascotaTask;

                // Procesar tarea del dueño
                if (finalDuenoTask != null) {
                    finalDuenoTask.addOnSuccessListener(duenoDoc -> {
                        if (duenoDoc.exists()) {
                            solicitud.setDuenoNombre(duenoDoc.getString("nombre_display"));
                            solicitud.setDuenoFotoUrl(duenoDoc.getString("foto_perfil"));
                        } else {
                            solicitud.setDuenoNombre("Usuario desconocido");
                        }

                        // Una vez obtenido el dueño, procesar la mascota
                        if (finalMascotaTask != null) {
                            finalMascotaTask.addOnSuccessListener(mascotaDoc -> {
                                if (mascotaDoc.exists()) {
                                    solicitud.setMascotaRaza(mascotaDoc.getString("raza"));
                                } else {
                                    solicitud.setMascotaRaza("Mascota");
                                }
                                solicitudesList.add(solicitud);
                                if (++solicitudesProcesadas[0] == totalSolicitudes) {
                                    actualizarYFinalizar();
                                }
                            }).addOnFailureListener(e -> {
                                solicitud.setMascotaRaza("Error");
                                solicitudesList.add(solicitud);
                                if (++solicitudesProcesadas[0] == totalSolicitudes) {
                                    actualizarYFinalizar();
                                }
                            });
                        } else {
                            solicitud.setMascotaRaza("Mascota no especificada");
                            solicitudesList.add(solicitud);
                            if (++solicitudesProcesadas[0] == totalSolicitudes) {
                                actualizarYFinalizar();
                            }
                        }
                    }).addOnFailureListener(e -> {
                        solicitud.setDuenoNombre("Error al cargar");
                        solicitudesList.add(solicitud);
                        if (++solicitudesProcesadas[0] == totalSolicitudes) {
                            actualizarYFinalizar();
                        }
                    });
                } else {
                    solicitud.setDuenoNombre("Dueño no especificado");
                    solicitudesList.add(solicitud);
                    if (++solicitudesProcesadas[0] == totalSolicitudes) {
                        actualizarYFinalizar();
                    }
                }
            }
        }).addOnFailureListener(this::manejarError);
    }

    private void actualizarYFinalizar() {
        // Ordenar la lista por fecha de creación descendente antes de mostrar
        solicitudesList.sort((s1, s2) -> s2.getFechaCreacion().compareTo(s1.getFechaCreacion()));
        if (solicitudesAdapter != null) {
            solicitudesAdapter.notifyDataSetChanged();
        }
        finalizarCarga();
    }

    private void finalizarCarga() {
        swipeRefresh.setRefreshing(false);
        
        if (solicitudesList.isEmpty()) {
            rvSolicitudes.setVisibility(View.GONE);
            llEmptyView.setVisibility(View.VISIBLE);
        } else {
            rvSolicitudes.setVisibility(View.VISIBLE);
            llEmptyView.setVisibility(View.GONE);
        }
    }

    private void manejarError(Exception e) {
        Log.e(TAG, "Error al cargar solicitudes", e);
        swipeRefresh.setRefreshing(false);
        Toast.makeText(this, "Error al cargar solicitudes", Toast.LENGTH_SHORT).show();
        rvSolicitudes.setVisibility(View.GONE);
        llEmptyView.setVisibility(View.VISIBLE);
    }

    // Clase modelo Solicitud
    public static class Solicitud {
        private String reservaId;
        private Date fechaCreacion;
        private Date horaInicio;
        private DocumentReference idDueno;
        private String idMascota;
        private String duenoNombre;
        private String duenoFotoUrl;
        private String mascotaRaza;

        public Solicitud() {}

        // Getters y Setters
        public String getReservaId() { return reservaId; }
        public void setReservaId(String reservaId) { this.reservaId = reservaId; }

        public Date getFechaCreacion() { return fechaCreacion; }
        public void setFechaCreacion(Date fechaCreacion) { this.fechaCreacion = fechaCreacion; }

        public Date getHoraInicio() { return horaInicio; }
        public void setHoraInicio(Date horaInicio) { this.horaInicio = horaInicio; }

        public DocumentReference getIdDueno() { return idDueno; }
        public void setIdDueno(DocumentReference idDueno) { this.idDueno = idDueno; }

        public String getIdMascota() { return idMascota; }
        public void setIdMascota(String idMascota) { this.idMascota = idMascota; }

        public String getDuenoNombre() { return duenoNombre; }
        public void setDuenoNombre(String duenoNombre) { this.duenoNombre = duenoNombre; }

        public String getDuenoFotoUrl() { return duenoFotoUrl; }
        public void setDuenoFotoUrl(String duenoFotoUrl) { this.duenoFotoUrl = duenoFotoUrl; }

        public String getMascotaRaza() { return mascotaRaza; }
        public void setMascotaRaza(String mascotaRaza) { this.mascotaRaza = mascotaRaza; }
    }
}
