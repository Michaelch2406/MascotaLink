package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
    private TextView tvNombreCompleto, tvRol;
    private RecyclerView rvMascotas;
    private MascotaPerfilAdapter mascotaAdapter;
    private List<Pet> petList;
    private Button btnCerrarSesion;
    private TextView btnEditarPerfil, btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;


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
            // Asumiendo que las mascotas están en una subcolección del documento de usuario
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

        rvMascotas = findViewById(R.id.rv_mascotas);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        petList = new ArrayList<>();
        mascotaAdapter = new MascotaPerfilAdapter(this, petList);
        rvMascotas.setAdapter(mascotaAdapter);

        // Ajustes Views
        btnEditarPerfil = findViewById(R.id.btn_editar_perfil);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
        btnMetodosPago = findViewById(R.id.btn_metodos_pago);
        btnPrivacidad = findViewById(R.id.btn_privacidad);
        btnCentroAyuda = findViewById(R.id.btn_centro_ayuda);
        btnTerminos = findViewById(R.id.btn_terminos);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);


        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnEditarPerfil.setOnClickListener(v -> showToast("Próximamente: Editar perfil"));
        btnNotificaciones.setOnClickListener(v -> showToast("Próximamente: Notificaciones"));
        btnMetodosPago.setOnClickListener(v -> showToast("Próximamente: Métodos de pago"));
        btnPrivacidad.setOnClickListener(v -> showToast("Próximamente: Privacidad"));
        btnCentroAyuda.setOnClickListener(v -> showToast("Próximamente: Centro de Ayuda"));
        btnTerminos.setOnClickListener(v -> showToast("Próximamente: Términos y Condiciones"));

        btnCerrarSesion.setOnClickListener(v -> {
            // Limpiar preferencias de "recordar sesión"
            getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();

            mAuth.signOut();
            Intent intent = new Intent(PerfilDuenoActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void cargarDatosDueno(String uid) {
        db.collection("usuarios").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    tvNombreCompleto.setText(document.getString("nombre_display"));
                    tvRol.setText("Dueño de mascotas");

                    String fotoUrl = document.getString("foto_perfil");
                    if (fotoUrl != null && !fotoUrl.isEmpty()) {
                        Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatar);
                    }

                    // Suponiendo que el estado de verificación también está en el doc de usuario
                    String verificacion = document.getString("verificacion_estado");
                    if ("APROBADO".equals(verificacion)) {
                        ivVerificado.setVisibility(View.VISIBLE);
                    } else {
                        ivVerificado.setVisibility(View.GONE);
                    }
                }
            } else {
                Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarMascotas(String uid) {
        // La ruta correcta es la subcolección 'mascotas' dentro del documento del dueño en la colección 'duenos'
        db.collection("duenos").document(uid).collection("mascotas")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        petList.clear();
                        if (task.getResult().isEmpty()) {
                            // Opcional: mostrar un mensaje de que no hay mascotas
                        } else {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Pet pet = new Pet();
                                pet.setId(document.getId());
                                pet.setName(document.getString("nombre"));
                                pet.setBreed(document.getString("raza"));
                                pet.setAvatarUrl(document.getString("foto_principal_url"));
                                petList.add(pet);
                            }
                        }
                        mascotaAdapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "Error al cargar las mascotas.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
