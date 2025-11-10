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
        setupBottomNavigation();

        // Cargar solicitudes iniciales
        cargarSolicitudes();
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

    private void setupBottomNavigation() {
        bottomNav.setSelectedItemId(R.id.menu_walks);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_walks) {
                // Ya estamos aquí
                return true;
            } else if (itemId == R.id.menu_home) {
                Intent intent = new Intent(this, PerfilPaseadorActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_search) {
                Intent intent = new Intent(this, com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(this, "Próximamente: Mensajes", Toast.LENGTH_SHORT).show();
                return false;
            } else if (itemId == R.id.menu_perfil) {
                Intent intent = new Intent(this, PerfilPaseadorActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void cargarSolicitudes() {
        swipeRefresh.setRefreshing(true);
        solicitudesList.clear();

        // Consultar reservas donde el paseador es el actual y el estado es PENDIENTE_ACEPTACION
        Query query = db.collection("reservas")
                .whereEqualTo("id_paseador", db.collection("usuarios").document(currentUserId))
                .whereEqualTo("estado", "PENDIENTE_ACEPTACION")
                .orderBy("fecha_creacion", Query.Direction.DESCENDING);

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            List<Solicitud> solicitudesTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Solicitud solicitud = new Solicitud();
                solicitud.setReservaId(doc.getId());
                solicitud.setFechaCreacion(doc.getTimestamp("fecha_creacion") != null ? doc.getTimestamp("fecha_creacion").toDate() : new Date());
                solicitud.setHoraInicio(doc.getTimestamp("hora_inicio") != null ? doc.getTimestamp("hora_inicio").toDate() : new Date());
                solicitud.setIdDueno(doc.getDocumentReference("id_dueno"));
                solicitud.setIdMascota(doc.getString("id_mascota"));
                
                solicitudesTemporales.add(solicitud);

                // Añadir tareas de obtención de datos relacionados
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                if (duenoRef != null) {
                    tareas.add(duenoRef.get());
                } else {
                    tareas.add(null);
                }

                String idMascota = doc.getString("id_mascota");
                if (idMascota != null && !idMascota.isEmpty() && duenoRef != null) {
                    // Obtener ID del dueño desde la referencia (el path es usuarios/{duenoId})
                    String duenoId = duenoRef.getId();
                    // Las mascotas están en duenos/{duenoId}/mascotas/{idMascota}
                    // Nota: El ID en usuarios y duenos debería ser el mismo
                    tareas.add(db.collection("duenos").document(duenoId).collection("mascotas").document(idMascota).get());
                } else {
                    tareas.add(null);
                }
            }

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            // Ejecutar todas las tareas en paralelo
            com.google.android.gms.tasks.Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                for (int i = 0; i < solicitudesTemporales.size(); i++) {
                    Solicitud solicitud = solicitudesTemporales.get(i);
                    
                    // El resultado corresponde a duenoRef.get()
                    Object duenoResult = results.get(i * 2);
                    if (duenoResult instanceof DocumentSnapshot) {
                        DocumentSnapshot duenoDoc = (DocumentSnapshot) duenoResult;
                        if (duenoDoc.exists()) {
                            solicitud.setDuenoNombre(duenoDoc.getString("nombre_display"));
                            solicitud.setDuenoFotoUrl(duenoDoc.getString("foto_perfil"));
                        } else {
                            solicitud.setDuenoNombre("Usuario desconocido");
                        }
                    }

                    // El resultado corresponde a mascota.get()
                    Object mascotaResult = results.get(i * 2 + 1);
                    if (mascotaResult instanceof DocumentSnapshot) {
                        DocumentSnapshot mascotaDoc = (DocumentSnapshot) mascotaResult;
                        if (mascotaDoc.exists()) {
                            solicitud.setMascotaRaza(mascotaDoc.getString("raza"));
                        } else {
                            solicitud.setMascotaRaza("Mascota");
                        }
                    } else {
                        solicitud.setMascotaRaza("Mascota");
                    }
                    
                    solicitudesList.add(solicitud);
                }

                if (solicitudesAdapter != null) {
                    solicitudesAdapter.notifyDataSetChanged();
                }
                finalizarCarga();

            }).addOnFailureListener(this::manejarError);

        }).addOnFailureListener(this::manejarError);
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

