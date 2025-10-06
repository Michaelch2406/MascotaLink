package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class PerfilDuenoActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ImageView ivAvatar, ivVerificado, ivBack;
    private TextView tvNombreCompleto, tvRol, tvEmail, tvTelefono;
    private RecyclerView rvMascotas;
    private MascotaPerfilAdapter mascotaAdapter;
    private List<Pet> petList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_dueno);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        initViews();
        setupListeners();

        if (currentUser != null) {
            cargarDatosDueno(currentUser.getUid());
            cargarMascotas(currentUser.getUid());
        } else {
            // Handle user not logged in
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivAvatar = findViewById(R.id.iv_avatar);
        ivVerificado = findViewById(R.id.iv_verificado);
        tvNombreCompleto = findViewById(R.id.tv_nombre_completo);
        tvRol = findViewById(R.id.tv_rol);
        tvEmail = findViewById(R.id.tv_email);
        tvTelefono = findViewById(R.id.tv_telefono);

        rvMascotas = findViewById(R.id.rv_mascotas);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        petList = new ArrayList<>();
        mascotaAdapter = new MascotaPerfilAdapter(this, petList);
        rvMascotas.setAdapter(mascotaAdapter);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        findViewById(R.id.tv_editar_perfil).setOnClickListener(v -> showToast("Próximamente: Editar perfil"));
        findViewById(R.id.tv_notificaciones).setOnClickListener(v -> showToast("Próximamente: Notificaciones"));
        findViewById(R.id.tv_metodos_pago).setOnClickListener(v -> showToast("Próximamente: Métodos de pago"));
        findViewById(R.id.tv_cerrar_sesion).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(PerfilDuenoActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // Add other listeners as needed
    }

    private void cargarDatosDueno(String uid) {
        db.collection("duenos").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String nombre = document.getString("nombre_display");
                    String fotoUrl = document.getString("foto_perfil");
                    String email = document.getString("email");
                    String telefono = document.getString("telefono");
                    String verificacion = document.getString("verificacion_estado");

                    tvNombreCompleto.setText(nombre);
                    tvEmail.setText(email);
                    tvTelefono.setText(telefono);
                    tvRol.setText("Dueño de mascotas");

                    if (fotoUrl != null && !fotoUrl.isEmpty()) {
                        Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatar);
                    }

                    if ("APROBADO".equals(verificacion)) {
                        ivVerificado.setVisibility(View.VISIBLE);
                    } else {
                        ivVerificado.setVisibility(View.GONE);
                    }
                }
            } else {
                // Log error
            }
        });
    }

    private void cargarMascotas(String uid) {
        db.collection("duenos").document(uid).collection("mascotas")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        petList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Pet pet = new Pet();
                            pet.setId(document.getId());
                            pet.setName(document.getString("nombre"));
                            pet.setBreed(document.getString("raza"));
                            pet.setAvatarUrl(document.getString("foto_principal_url"));
                            petList.add(pet);
                        }
                        mascotaAdapter.notifyDataSetChanged();
                    } else {
                        // Log error
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}