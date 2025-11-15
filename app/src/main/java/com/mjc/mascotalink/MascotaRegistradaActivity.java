package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class MascotaRegistradaActivity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistrada";

    private RecyclerView rvMascotas;
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
        ImageView btnBack = findViewById(R.id.btnBack);
        Button btnAddAnother = findViewById(R.id.btnAddAnother);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        btnBack.setOnClickListener(v -> finish());

        btnAddAnother.setOnClickListener(v -> {
            Intent intent = new Intent(this, MascotaRegistroPaso1Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnGoHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
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
            // Handle not authenticated user
            return;
        }

        String userId = currentUser.getUid();
        db.collection("duenos").document(userId).collection("mascotas")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        petList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String id = document.getId();
                            String name = document.getString("nombre");
                            String breed = document.getString("raza"); // Get breed
                            String avatarUrl = document.getString("foto_principal_url");
                            petList.add(new Pet(id, name, breed, avatarUrl, userId)); // Pass all 5 arguments
                        }
                        mascotaAdapter.notifyDataSetChanged();
                    } else {
                        Log.e(TAG, "Error al obtener mascotas: ", task.getException());
                    }
                });
    }
}
