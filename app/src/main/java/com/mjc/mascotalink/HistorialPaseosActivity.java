package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mjc.mascotalink.util.BottomNavManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HistorialPaseosActivity extends AppCompatActivity {

    private static final String TAG = "HistorialPaseos";

    private RecyclerView rvHistorial;
    private HistorialPaseosAdapter adapter;
    private List<Paseo> listaPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout emptyView;
    private ProgressBar progressBar;
    private com.google.android.material.tabs.TabLayout tabLayout;
    
    private FirebaseFirestore db;
    private String currentUserId;
    private String userRole;
    private String filtroEstado = "TODOS"; // TODOS, COMPLETADO, CANCELADO, RECHAZADO

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial_paseos);

        // Init Firebase
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        } else {
            finish();
            return;
        }

        // Get role from Intent or Cache
        if (getIntent().hasExtra("rol_usuario")) {
            userRole = getIntent().getStringExtra("rol_usuario");
        } else {
            userRole = BottomNavManager.getUserRole(this);
            if (userRole == null) userRole = "DUEÑO"; // Default
        }

        initViews();
        setupListeners();
        cargarHistorial();
    }

    private void initViews() {
        rvHistorial = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyView = findViewById(R.id.empty_view);
        tabLayout = findViewById(R.id.tab_layout);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        listaPaseos = new ArrayList<>();
        adapter = new HistorialPaseosAdapter(this, listaPaseos, userRole, this::abrirDetallePaseo);
        rvHistorial.setLayoutManager(new LinearLayoutManager(this));
        rvHistorial.setAdapter(adapter);
        
        setupTabs();
    }
    
    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Todos"));
        tabLayout.addTab(tabLayout.newTab().setText("Completados"));
        tabLayout.addTab(tabLayout.newTab().setText("Cancelados"));
        tabLayout.addTab(tabLayout.newTab().setText("Rechazados"));
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::cargarHistorial);
        
        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                int position = tab.getPosition();
                switch (position) {
                    case 0: filtroEstado = "TODOS"; break;
                    case 1: filtroEstado = "COMPLETADO"; break;
                    case 2: filtroEstado = "CANCELADO"; break;
                    case 3: filtroEstado = "RECHAZADO"; break;
                }
                filtrarListaLocalmente();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void abrirDetallePaseo(Paseo paseo) {
        Intent intent = new Intent(this, DetallePaseoActivity.class);
        intent.putExtra("id_reserva", paseo.getReservaId());
        intent.putExtra("rol_usuario", userRole);
        intent.putExtra("paseo_obj", paseo); // Opcional: pasar objeto para carga rápida
        startActivity(intent);
    }

    // Listener para actualizaciones en tiempo real
    private com.google.firebase.firestore.ListenerRegistration firestoreListener;

    private void cargarHistorial() {
        // Detener listener anterior si existe
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }

        swipeRefresh.setRefreshing(true);
        // No limpiamos listaPaseos aquí para evitar parpadeo, se limpia al recibir datos

        String campoFiltro = "PASEADOR".equalsIgnoreCase(userRole) ? "id_paseador" : "id_dueno";
        DocumentReference userRef = db.collection("usuarios").document(currentUserId);

        // Consultamos paseos donde el usuario participa
        Query query = db.collection("reservas")
                .whereEqualTo(campoFiltro, userRef)
                .whereIn("estado", Arrays.asList("COMPLETADO", "CANCELADO", "RECHAZADO", "FINALIZADO"));
                //.orderBy("fecha", Query.Direction.DESCENDING); // Comentado por si falta índice

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                manejarError(e);
                return;
            }

            if (querySnapshot == null || querySnapshot.isEmpty()) {
                listaPaseos.clear();
                filtrarListaLocalmente();
                swipeRefresh.setRefreshing(false);
                mostrarEstadoVacio();
                return;
            }

            List<Paseo> paseosTemp = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Paseo paseo = doc.toObject(Paseo.class);
                if (paseo == null) continue;
                paseo.setReservaId(doc.getId());
                
                // Asegurar ID Mascota
                if (paseo.getIdMascota() == null && doc.contains("id_mascota")) {
                     paseo.setIdMascota(doc.getString("id_mascota"));
                }
                
                paseosTemp.add(paseo);

                // Referencias para obtener nombres/fotos
                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                
                tareas.add(paseadorRef != null ? paseadorRef.get() : Tasks.forResult(null));
                tareas.add(duenoRef != null ? duenoRef.get() : Tasks.forResult(null));
                
                if (duenoRef != null && paseo.getIdMascota() != null) {
                    tareas.add(db.collection("duenos").document(duenoRef.getId())
                            .collection("mascotas").document(paseo.getIdMascota()).get());
                } else {
                    tareas.add(Tasks.forResult(null));
                }
            }
            
            if (tareas.isEmpty()) {
                swipeRefresh.setRefreshing(false);
                mostrarEstadoVacio();
                return;
            }

            Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                if (isDestroyed() || isFinishing()) return;

                for (int i = 0; i < paseosTemp.size(); i++) {
                    Paseo p = paseosTemp.get(i);
                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(i * 3);
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(i * 3 + 1);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(i * 3 + 2);

                    if (paseadorDoc != null && paseadorDoc.exists()) {
                        p.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                        p.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                    }
                    if (duenoDoc != null && duenoDoc.exists()) {
                        p.setDuenoNombre(duenoDoc.getString("nombre_display"));
                    }
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        p.setMascotaNombre(mascotaDoc.getString("nombre"));
                        p.setMascotaFoto(mascotaDoc.getString("foto_principal_url"));
                    }
                }
                
                // Ordenar en cliente por fecha descendente
                Collections.sort(paseosTemp, (p1, p2) -> {
                    Date d1 = p1.getFecha();
                    Date d2 = p2.getFecha();
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d2.compareTo(d1);
                });
                
                // Actualizar lista maestra y filtrar
                listaPaseos.clear();
                listaPaseos.addAll(paseosTemp);
                filtrarListaLocalmente(); 
                swipeRefresh.setRefreshing(false);
                
            }).addOnFailureListener(ex -> {
                Log.e(TAG, "Error cargando detalles relacionados", ex);
                swipeRefresh.setRefreshing(false);
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
    }
    
    private void filtrarListaLocalmente() {
        if (filtroEstado.equals("TODOS")) {
            // IMPORTANTE: Pasar una COPIA de la lista para que DiffUtil detecte el cambio.
            // Si pasamos la misma referencia (listaPaseos), AsyncListDiffer ignora la actualización.
            actualizarAdapter(new ArrayList<>(listaPaseos));
        } else {
            List<Paseo> filtrados = new ArrayList<>();
            for (Paseo p : listaPaseos) {
                if (p.getEstado() != null && (p.getEstado().equalsIgnoreCase(filtroEstado) 
                    || (filtroEstado.equals("COMPLETADO") && p.getEstado().equalsIgnoreCase("FINALIZADO")))) {
                    filtrados.add(p);
                }
            }
            actualizarAdapter(filtrados);
        }
    }

    private void actualizarAdapter(List<Paseo> lista) {
        if (lista.isEmpty()) {
            rvHistorial.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            rvHistorial.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.updateList(lista);
        }
    }

    private void mostrarEstadoVacio() {
        rvHistorial.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void manejarError(Exception e) {
        swipeRefresh.setRefreshing(false);
        Log.e(TAG, "Error cargando historial", e);
        Toast.makeText(this, "Error al cargar el historial", Toast.LENGTH_SHORT).show();
    }
}