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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.Query;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
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
    private TabLayout tabLayout;
    private RecyclerView rvPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout emptyView;
    private Button btnReservarPaseo; // Not used in layout but kept for logic logic if needed or removed
    private BottomNavigationView bottomNav;
    private android.widget.ImageView ivBack;

    // Adapter
    private PaseosAdapter paseosAdapter;
    private List<Paseo> paseosList;

    // Estado actual
    private String estadoActual = ReservaEstadoValidator.ESTADO_ACEPTADO;

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
        
        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
            userRole = cachedRole;
            setupRecyclerView(userRole);
            setupTabLayout(userRole);
        }
        
        fetchUserRoleAndSetupUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void initViews() {
        ivBack = findViewById(R.id.btn_back);
        tabLayout = findViewById(R.id.tab_layout);
        rvPaseos = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyView = findViewById(R.id.empty_view);
        // btnReservarPaseo removed from new layout, handled via Empty View logic or menu
        bottomNav = findViewById(R.id.bottom_nav);

        if (ivBack != null) {
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
                            BottomNavManager.saveUserRole(this, fetchedRole);
                            if (!fetchedRole.equalsIgnoreCase(userRole) || paseosAdapter == null) {
                                userRole = fetchedRole;
                                setupRecyclerView(userRole);
                                setupTabLayout(userRole);
                            }
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

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = userRole != null ? userRole : "DUEÑO";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_walks);
    }

    private void setupTabLayout(String role) {
        tabLayout.removeAllTabs();
        
        // Orden lógico de pestañas
        addTab("Aceptados", ReservaEstadoValidator.ESTADO_ACEPTADO);
        addTab("Programados", ReservaEstadoValidator.ESTADO_CONFIRMADO);
        addTab("En Curso", "EN_CURSO");
        addTab("Completados", "COMPLETADO");
        addTab("Cancelados", "CANCELADO");

        // Seleccionar tab correspondiente al estado actual
        int selectedIndex = getTabIndexForState(estadoActual);
        TabLayout.Tab tab = tabLayout.getTabAt(selectedIndex);
        if (tab != null) {
            tab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String nuevoEstado = (String) tab.getTag();
                if (nuevoEstado != null) {
                    estadoActual = nuevoEstado;
                    cargarPaseos(role);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {
                // Opcional: Recargar si se pulsa de nuevo
                cargarPaseos(role);
            }
        });
    }

    private void addTab(String title, String stateTag) {
        TabLayout.Tab tab = tabLayout.newTab();
        tab.setText(title);
        tab.setTag(stateTag);
        tabLayout.addTab(tab);
    }

    private int getTabIndexForState(String state) {
        switch (state) {
            case ReservaEstadoValidator.ESTADO_ACEPTADO: return 0;
            case ReservaEstadoValidator.ESTADO_CONFIRMADO: return 1;
            case "EN_CURSO": return 2;
            case "COMPLETADO": return 3;
            case "CANCELADO": return 4;
            default: return 0;
        }
    }

    private void setupRecyclerView(String role) {
        rvPaseos.setLayoutManager(new LinearLayoutManager(this));
        paseosAdapter = new PaseosAdapter(this, paseosList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(Paseo paseo) {
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
                    // Expandir detalles o mostrar algo más
                }
            }

            @Override
            public void onVerUbicacionClick(Paseo paseo) {
                // Implementar navegación a mapa si aplica
            }

            @Override
            public void onContactarClick(Paseo paseo) {
                // Implementar chat o llamada
            }

            @Override
            public void onCalificarClick(Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", paseo.getReservaId());
                startActivity(intent);
            }

            @Override
            public void onVerMotivoClick(Paseo paseo) {
                mostrarDialogMotivoCancelacion(paseo);
            }

            @Override
            public void onProcesarPagoClick(Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ConfirmarPagoActivity.class);
                intent.putExtra("reserva_id", paseo.getReservaId());
                intent.putExtra("costo_total", paseo.getCosto_total());
                intent.putExtra("paseador_nombre", paseo.getPaseadorNombre());
                intent.putExtra("mascota_nombre", paseo.getMascotaNombre());
                intent.putExtra("fecha_reserva", paseo.getFechaFormateada());
                intent.putExtra("hora_reserva", paseo.getHoraFormateada());
                intent.putExtra("direccion_recogida", "Calle Principal 123, Ciudad"); // TODO: Get real address
                startActivity(intent);
            }
        }, role);
        rvPaseos.setAdapter(paseosAdapter);
    }

    private boolean esPaseoEnCurso(Paseo paseo) {
        if (paseo == null || paseo.getEstado() == null) return false;
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

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            List<Paseo> paseosTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Paseo paseo = doc.toObject(Paseo.class);
                if (paseo == null) continue;
                paseo.setReservaId(doc.getId());
                String mascotaId = doc.getString("id_mascota");
                paseo.setIdMascota(mascotaId);

                paseosTemporales.add(paseo);

                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String currentMascotaId = paseo.getIdMascota();

                tareas.add(paseadorRef != null ? paseadorRef.get() : Tasks.forResult(null));
                tareas.add(duenoRef != null ? duenoRef.get() : Tasks.forResult(null));
                if (duenoRef != null && currentMascotaId != null && !currentMascotaId.isEmpty()) {
                    tareas.add(db.collection("duenos").document(duenoRef.getId()).collection("mascotas")
                            .document(currentMascotaId).get());
                } else {
                    tareas.add(Tasks.forResult(null));
                }
            }

            java.util.Collections.sort(paseosTemporales, (p1, p2) -> {
                if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                return p2.getFecha().compareTo(p1.getFecha());
            });

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                Date now = new Date();
                List<Paseo> nuevosPaseos = new ArrayList<>();

                for (int i = 0; i < paseosTemporales.size(); i++) {
                    Paseo paseo = paseosTemporales.get(i);

                    // Auto-corrección de estado (EN_CURSO)
                    if ("CONFIRMADO".equals(paseo.getEstado()) && paseo.getHora_inicio() != null && paseo.getHora_inicio().before(now)) {
                        paseo.setEstado("EN_CURSO");
                        db.collection("reservas").document(paseo.getReservaId())
                                .update("estado", "EN_CURSO", 
                                        "hasTransitionedToInCourse", true,
                                        "fecha_inicio_paseo", new com.google.firebase.Timestamp(paseo.getHora_inicio()));
                    }

                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(i * 3);
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(i * 3 + 1);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(i * 3 + 2);

                    if (paseadorDoc != null && paseadorDoc.exists()) {
                        paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                        paseo.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                    }
                    if (duenoDoc != null && duenoDoc.exists()) {
                        paseo.setDuenoNombre(duenoDoc.getString("nombre_display"));
                    }
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        paseo.setMascotaFoto(mascotaDoc.getString("foto_principal_url"));
                    } else {
                        paseo.setMascotaNombre("Mascota no encontrada");
                    }
                    nuevosPaseos.add(paseo);
                }

                java.util.Collections.sort(nuevosPaseos, (p1, p2) -> {
                    if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                    return p2.getFecha().compareTo(p1.getFecha());
                });

                paseosList.clear();
                paseosList.addAll(nuevosPaseos);

                if (paseosAdapter != null) {
                    paseosAdapter.updateList(paseosList);
                }
                finalizarCarga();
            }).addOnFailureListener(this::manejarError);
        }).addOnFailureListener(this::manejarError);
    }

    private void finalizarCarga() {
        swipeRefresh.setRefreshing(false);
        if (paseosList.isEmpty()) {
            rvPaseos.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            rvPaseos.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void manejarError(Exception e) {
        swipeRefresh.setRefreshing(false);
        Log.e(TAG, "Error al cargar paseos: ", e);
        Toast.makeText(this, "Error al cargar paseos.", Toast.LENGTH_SHORT).show();
        rvPaseos.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void mostrarDialogMotivoCancelacion(Paseo paseo) {
        if (isFinishing()) return;
        String motivo = paseo.getRazonCancelacion();
        if (motivo == null || motivo.isEmpty()) motivo = "No se especificó un motivo";
        new AlertDialog.Builder(this)
                .setTitle("Motivo de Cancelación")
                .setMessage(motivo)
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
