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

    // Network monitoring
    private com.mjc.mascotalink.network.NetworkMonitorHelper networkMonitor;

    // Views
    private android.widget.ImageView ivBack;
    private RecyclerView rvSolicitudes;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmptyView;
    private BottomNavigationView bottomNav;
    private String userRole = "PASEADOR";

    // Adapter - Usa PaseosAdapter (reutilizado) pero en contexto de solicitudes
    private PaseosAdapter solicitudesAdapter;
    private List<Paseo> solicitudesList;

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

        // Initialize role from cache
        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
            userRole = cachedRole;
        }

        // Inicializar NetworkMonitorHelper
        setupNetworkMonitor();

        initViews();
        // Setup Bottom Navigation immediately to prevent flicker
        if (bottomNav != null) {
             setupBottomNavigation();
        }
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
        ivBack = findViewById(R.id.btn_back);
        rvSolicitudes = findViewById(R.id.rv_solicitudes);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmptyView = findViewById(R.id.empty_view);
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

        // Usar PaseosAdapter (reutilizado) para manejar agrupaciones de solicitudes
        solicitudesAdapter = new PaseosAdapter(this, solicitudesList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(Paseo solicitud) {
                // Navegar a detalle de solicitud
                Intent intent = new Intent(SolicitudesActivity.this, SolicitudDetalleActivity.class);
                intent.putExtra("id_reserva", solicitud.getReservaId());
                // Si es grupo, pasar también el ID del grupo
                if (solicitud.getEs_grupo() != null && solicitud.getEs_grupo() && solicitud.getGrupo_reserva_id() != null) {
                    intent.putExtra("grupo_reserva_id", solicitud.getGrupo_reserva_id());
                }
                startActivity(intent);
            }

            @Override
            public void onVerUbicacionClick(Paseo solicitud) {
                // No aplica en solicitudes pendientes
            }

            @Override
            public void onContactarClick(Paseo solicitud) {
                // No aplica en solicitudes pendientes
            }

            @Override
            public void onCalificarClick(Paseo solicitud) {
                // No aplica en solicitudes pendientes
            }

            @Override
            public void onVerMotivoClick(Paseo solicitud) {
                // No aplica en solicitudes pendientes
            }

            @Override
            public void onProcesarPagoClick(Paseo solicitud) {
                // No aplica en solicitudes pendientes
            }
        }, userRole);

        rvSolicitudes.setAdapter(solicitudesAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            cargarSolicitudes();
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    // Listener para actualizaciones en tiempo real
    private com.google.firebase.firestore.ListenerRegistration firestoreListener;

    private void cargarSolicitudes() {
        // Detener listener anterior si existe
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }

        swipeRefresh.setRefreshing(true);
        // solicitudesList.clear(); // Opcional: evitar limpiar inmediatamente para reducir parpadeo

        // Consultar reservas donde el paseador es el actual y el estado es PENDIENTE_ACEPTACION
        Query query = db.collection("reservas")
                .whereEqualTo("id_paseador", db.collection("usuarios").document(currentUserId))
                .whereEqualTo("estado", ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION)
                .orderBy("fecha_creacion", Query.Direction.DESCENDING);

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                manejarError(e);
                return;
            }

            if (querySnapshot == null || querySnapshot.isEmpty()) {
                solicitudesList.clear();
                if (solicitudesAdapter != null) {
                    solicitudesAdapter.updateList(solicitudesList);
                }
                finalizarCarga();
                return;
            }

            List<Paseo> solicitudesTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                // Convertir DocumentSnapshot a objeto Paseo
                Paseo solicitud = doc.toObject(Paseo.class);
                if (solicitud == null) {
                    solicitud = new Paseo();
                }
                solicitud.setReservaId(doc.getId());

                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String idMascota = doc.getString("id_mascota");

                solicitudesTemporales.add(solicitud);

                // Preparar tareas para obtener datos relacionados
                if (duenoRef != null) {
                    tareas.add(duenoRef.get());
                    if (idMascota != null && !idMascota.isEmpty()) {
                        tareas.add(db.collection("duenos").document(duenoRef.getId()).collection("mascotas").document(idMascota).get());
                    } else {
                        tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
                    }
                } else {
                    tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
                    tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
                }
            }

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            com.google.android.gms.tasks.Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                if (isDestroyed() || isFinishing()) return;

                List<Paseo> nuevasSolicitudes = new ArrayList<>();
                int resultIndex = 0;

                for (Paseo solicitud : solicitudesTemporales) {
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(resultIndex++);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(resultIndex++);

                    // Setear datos del dueño en el objeto Paseo
                    if (duenoDoc != null && duenoDoc.exists()) {
                        solicitud.setDuenoNombre(duenoDoc.getString("nombre_display"));
                    } else {
                        solicitud.setDuenoNombre("Usuario desconocido");
                    }

                    // Setear datos de la mascota en el objeto Paseo
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        solicitud.setMascotaNombre(mascotaDoc.getString("nombre"));
                        solicitud.setMascotaFoto(mascotaDoc.getString("foto_principal_url"));
                    } else {
                        solicitud.setMascotaNombre("Mascota");
                    }

                    nuevasSolicitudes.add(solicitud);
                }

                // Reemplazar lista y notificar
                solicitudesList.clear();
                solicitudesList.addAll(nuevasSolicitudes);
                actualizarYFinalizar();

            }).addOnFailureListener(ex -> {
                Log.e(TAG, "Error cargando detalles relacionados", ex);
                finalizarCarga();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
    }

    private void setupNetworkMonitor() {
        com.mjc.mascotalink.network.SocketManager socketManager =
            com.mjc.mascotalink.network.SocketManager.getInstance(this);

        networkMonitor = new com.mjc.mascotalink.network.NetworkMonitorHelper(
            this, socketManager,
            new com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkCallback() {
                private com.google.android.material.snackbar.Snackbar connectionSnackbar = null;

                @Override
                public void onNetworkLost() {
                    runOnUiThread(() -> {
                        Log.d(TAG, "📶 Red perdida - solicitudes pueden estar desactualizadas");

                        // Mostrar Snackbar persistente
                        if (connectionSnackbar == null || !connectionSnackbar.isShown()) {
                            connectionSnackbar = com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content),
                                "⚠️ Sin conexión. Las solicitudes pueden estar desactualizadas.",
                                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
                            );
                            connectionSnackbar.setAction("Reintentar", v -> {
                                if (networkMonitor != null) {
                                    networkMonitor.forceReconnect();
                                }
                            });
                            connectionSnackbar.show();
                        }
                    });
                }

                @Override
                public void onNetworkAvailable() {
                    runOnUiThread(() -> {
                        Log.d(TAG, "📶 Red disponible");
                        if (connectionSnackbar != null && connectionSnackbar.isShown()) {
                            connectionSnackbar.setText("🔄 Conectando...");
                        }
                    });
                }

                @Override
                public void onReconnected() {
                    runOnUiThread(() -> {
                        Log.d(TAG, "🌐 Reconectado - actualizando solicitudes");

                        // Dismiss Snackbar de reconexión
                        if (connectionSnackbar != null && connectionSnackbar.isShown()) {
                            connectionSnackbar.dismiss();
                        }

                        // Mostrar confirmación y auto-refresh
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            "✅ Conexión restaurada. Actualizando...",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show();

                        // Auto-refresh de solicitudes
                        cargarSolicitudes();
                    });
                }

                @Override
                public void onRetrying(int attempt, long delayMs) {
                    runOnUiThread(() -> {
                        if (connectionSnackbar != null && connectionSnackbar.isShown()) {
                            connectionSnackbar.setText("Reintento " + attempt + "/5 en " + (delayMs/1000) + "s...");
                        }
                    });
                }

                @Override
                public void onReconnectionFailed(int attempts) {
                    runOnUiThread(() -> {
                        if (connectionSnackbar != null && connectionSnackbar.isShown()) {
                            connectionSnackbar.dismiss();
                        }

                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            "No se pudo conectar. Usa 'Deslizar para actualizar'.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).setAction("Reintentar", v -> {
                            if (networkMonitor != null) {
                                networkMonitor.forceReconnect();
                            }
                        }).show();
                    });
                }

                @Override
                public void onNetworkTypeChanged(com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkType type) {
                    Log.d(TAG, "📡 Tipo de red cambió a: " + type);
                }

                @Override
                public void onNetworkQualityChanged(com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkQuality quality) {
                    Log.d(TAG, "📶 Calidad de red: " + quality);
                }
            });

        networkMonitor.register();
    }

    private void actualizarYFinalizar() {
        // Ordenar la lista por fecha de creación descendente antes de mostrar
        solicitudesList.sort((s1, s2) -> {
            Date fecha1 = s1.getFecha_creacion();
            Date fecha2 = s2.getFecha_creacion();
            if (fecha1 == null) return 1;
            if (fecha2 == null) return -1;
            return fecha2.compareTo(fecha1);
        });
        if (solicitudesAdapter != null) {
            // Agrupar las reservas antes de pasarlas al adapter
            List<PaseoItem> items = PaseoItem.agruparReservas(solicitudesList);
            solicitudesAdapter.updateList(items);
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
        private String mascotaNombre;

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

        public String getMascotaNombre() { return mascotaNombre; }
        public void setMascotaNombre(String mascotaNombre) { this.mascotaNombre = mascotaNombre; }
    }
}
