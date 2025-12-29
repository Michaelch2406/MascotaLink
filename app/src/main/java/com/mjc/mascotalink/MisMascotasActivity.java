package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity que muestra la lista completa de mascotas del usuario.
 * Migrada del prototipo HTML mismascotas.html con marca azul Walky.
 */
public class MisMascotasActivity extends AppCompatActivity {

    private static final String TAG = "MisMascotasActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration mascotasListener;

    // Views
    private ImageView ivBack;
    private ImageView ivAddHeader;
    private TextView tvContadorMascotas;
    private RecyclerView rvMascotas;
    private LinearLayout contentLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout skeletonLayout;
    private SwipeRefreshLayout swipeRefresh;
    private FloatingActionButton fabAgregarMascota;

    // Adapter
    private MisMascotasAdapter adapter;
    private List<MisMascotasAdapter.MascotaCompleta> mascotasList = new ArrayList<>();

    // Rate limiter para clicks
    private final InputUtils.RateLimiter fabRateLimiter = new InputUtils.RateLimiter(1000);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_mascotas);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        initViews();
        setupRecyclerView();
        setupListeners();
        showSkeleton();
        cargarMascotas();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivAddHeader = findViewById(R.id.iv_add_header);
        tvContadorMascotas = findViewById(R.id.tv_contador_mascotas);
        rvMascotas = findViewById(R.id.rv_mascotas);
        contentLayout = findViewById(R.id.content_layout);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        skeletonLayout = findViewById(R.id.skeleton_layout);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        fabAgregarMascota = findViewById(R.id.fab_agregar_mascota);
    }

    private void setupRecyclerView() {
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MisMascotasAdapter(this, mascotasList);
        rvMascotas.setAdapter(adapter);

        // Click en mascota para ver perfil
        adapter.setOnMascotaClickListener(mascota -> {
            Intent intent = new Intent(MisMascotasActivity.this, PerfilMascotaActivity.class);
            intent.putExtra("mascota_id", mascota.getId());
            intent.putExtra("dueno_id", currentUserId);
            startActivity(intent);
        });
    }

    private void setupListeners() {
        // Botón atrás
        ivBack.setOnClickListener(v -> finish());

        // SwipeRefresh
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
        swipeRefresh.setOnRefreshListener(this::cargarMascotas);

        // FAB agregar mascota con rate limiting
        fabAgregarMascota.setOnClickListener(v -> {
            if (fabRateLimiter.shouldProcess()) {
                navegarAgregarMascota();
            }
        });

        // Botón agregar en estado vacío
        View btnAgregarEmpty = findViewById(R.id.btn_agregar_mascota_empty);
        if (btnAgregarEmpty != null) {
            btnAgregarEmpty.setOnClickListener(
                    InputUtils.createSafeClickListener(v -> navegarAgregarMascota())
            );
        }
    }

    private void navegarAgregarMascota() {
        Intent intent = new Intent(this, MascotaRegistroPaso1Activity.class);
        startActivity(intent);
    }

    private void cargarMascotas() {
        if (mascotasListener != null) {
            mascotasListener.remove();
        }

        mascotasListener = db.collection("duenos")
                .document(currentUserId)
                .collection("mascotas")
                .whereEqualTo("activo", true)
                .addSnapshotListener((value, error) -> {
                    // Ocultar refresh
                    if (swipeRefresh.isRefreshing()) {
                        swipeRefresh.setRefreshing(false);
                    }

                    if (error != null) {
                        Log.e(TAG, "Error cargando mascotas", error);
                        mostrarEstadoVacio();
                        return;
                    }

                    if (value == null || value.isEmpty()) {
                        Log.d(TAG, "No hay mascotas registradas");
                        mascotasList.clear();
                        mostrarEstadoVacio();
                        return;
                    }

                    mascotasList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        MisMascotasAdapter.MascotaCompleta mascota = parseMascota(doc);
                        mascotasList.add(mascota);
                    }

                    Log.d(TAG, "Mascotas cargadas: " + mascotasList.size());
                    actualizarUI();
                });
    }

    private MisMascotasAdapter.MascotaCompleta parseMascota(QueryDocumentSnapshot doc) {
        MisMascotasAdapter.MascotaCompleta mascota = new MisMascotasAdapter.MascotaCompleta();
        mascota.setId(doc.getId());
        mascota.setNombre(doc.getString("nombre"));
        mascota.setRaza(doc.getString("raza"));
        mascota.setSexo(doc.getString("sexo"));
        mascota.setOwnerId(currentUserId);

        // Foto URL
        String fotoUrl = doc.getString("foto_principal_url");
        mascota.setFotoUrl(MyApplication.getFixedUrl(fotoUrl));

        // Edad - calculada desde fecha_nacimiento
        com.google.firebase.Timestamp fechaNacimiento = doc.getTimestamp("fecha_nacimiento");
        if (fechaNacimiento != null) {
            java.util.Date birthDate = fechaNacimiento.toDate();
            java.time.LocalDate localBirthDate = birthDate.toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            java.time.LocalDate currentDate = java.time.LocalDate.now();
            int edad = java.time.Period.between(localBirthDate, currentDate).getYears();
            mascota.setEdadAnios(edad);
        } else {
            mascota.setEdadAnios(null);
        }

        // Peso - puede ser Double, Long o String
        Object pesoObj = doc.get("peso_kg");
        if (pesoObj == null) {
            pesoObj = doc.get("peso");
        }
        if (pesoObj instanceof Number) {
            mascota.setPesoKg(((Number) pesoObj).doubleValue());
        } else if (pesoObj instanceof String) {
            try {
                mascota.setPesoKg(Double.parseDouble((String) pesoObj));
            } catch (NumberFormatException e) {
                mascota.setPesoKg(null);
            }
        }

        return mascota;
    }

    private void actualizarUI() {
        skeletonLayout.setVisibility(View.GONE);

        if (mascotasList.isEmpty()) {
            mostrarEstadoVacio();
        } else {
            contentLayout.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);

            // Actualizar contador con formato del HTML
            int count = mascotasList.size();
            String contadorTexto = count + (count == 1 ? " Mascota" : " Mascotas");
            tvContadorMascotas.setText(contadorTexto);

            adapter.notifyDataSetChanged();
        }
    }

    private void mostrarEstadoVacio() {
        skeletonLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    private void showSkeleton() {
        skeletonLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar datos cuando se vuelve de agregar/editar mascota
        if (mascotasListener == null && currentUserId != null) {
            cargarMascotas();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mascotasListener != null) {
            mascotasListener.remove();
            mascotasListener = null;
        }
        InputUtils.cancelAllDebounces();
    }
}
