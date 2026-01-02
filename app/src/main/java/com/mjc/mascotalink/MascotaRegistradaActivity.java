package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.ArrayList;
import java.util.List;

public class MascotaRegistradaActivity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistrada";

    private NestedScrollView scrollView;
    private RecyclerView rvMascotas;
    private LinearLayout layoutEmpty;
    private MascotaAdapter mascotaAdapter;
    private List<Pet> petList;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registrada);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupViews();
        setupRecyclerView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Se cargan las mascotas cada vez que la pantalla se vuelve visible
        // para asegurar que la lista esté siempre actualizada.
        fetchMascotas();
    }

    private void setupViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        scrollView = findViewById(R.id.scroll_view);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        MaterialButton btnAddAnother = findViewById(R.id.btnAddAnother);
        MaterialButton btnGoHome = findViewById(R.id.btnGoHome);

        // SafeClickListener para prevenir doble-click
        btnAddAnother.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            Intent intent = new Intent(this, MascotaRegistroPaso1Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }));

        btnGoHome.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            // Navegar a la pantalla principal
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }));
    }

    private void setupRecyclerView() {
        rvMascotas = findViewById(R.id.rvMascotas);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        petList = new ArrayList<>();
        mascotaAdapter = new MascotaAdapter(this, petList);
        rvMascotas.setAdapter(mascotaAdapter);
    }

    private void fetchMascotas() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No hay usuario autenticado para buscar mascotas.");
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            // Redirigir a login si no hay usuario
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        String userId = currentUser.getUid();
        db.collection("duenos").document(userId).collection("mascotas")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    petList.clear();

                    if (queryDocumentSnapshots.isEmpty()) {
                        // No hay mascotas, mostrar estado vacío
                        Log.d(TAG, "No se encontraron mascotas para el usuario: " + userId);
                        showEmptyState(true);
                    } else {
                        // Hay mascotas, procesar y mostrar
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            try {
                                String id = document.getId();
                                String name = document.getString("nombre");
                                String breed = document.getString("raza");
                                String avatarUrl = document.getString("foto_principal_url");

                                // Validar que los campos críticos no sean nulos
                                if (name != null && !name.isEmpty()) {
                                    petList.add(new Pet(id, name, breed != null ? breed : "Sin raza", avatarUrl, userId));
                                } else {
                                    Log.w(TAG, "Mascota con ID " + id + " tiene nombre nulo o vacío, omitiendo.");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error al procesar mascota: " + document.getId(), e);
                            }
                        }

                        showEmptyState(petList.isEmpty());
                        mascotaAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Se cargaron " + petList.size() + " mascotas.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al obtener mascotas de Firestore", e);
                    Toast.makeText(this, "Error al cargar mascotas. Intenta nuevamente.", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
    }

    /**
     * Muestra u oculta el estado vacío según si hay mascotas o no
     * @param show true para mostrar estado vacío, false para ocultarlo
     */
    private void showEmptyState(boolean show) {
        if (show) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvMascotas.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvMascotas.setVisibility(View.VISIBLE);
        }
    }
}
