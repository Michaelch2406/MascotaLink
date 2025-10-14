package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PerfilMascotaActivity extends AppCompatActivity {

    private static final String TAG = "PerfilMascotaActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView ivBack, ivAvatarMascota, ivEditMascota;
    private TextView tvNombreMascota, tvDescripcionMascota;
    private TextView tvRaza, tvSexo, tvEdad, tvTamano, tvPeso, tvEsterilizado;
    private RecyclerView rvGaleria;
    private View btnSalud, btnComportamiento, btnInstrucciones;
    private GaleriaAdapter galeriaAdapter;
    private List<String> galeriaUrls;

    private String duenoId;
    private String mascotaId;
    private ListenerRegistration mascotaListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_mascota);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get IDs from intent
        duenoId = getIntent().getStringExtra("dueno_id");
        mascotaId = getIntent().getStringExtra("mascota_id");

        if (duenoId == null && mAuth.getCurrentUser() != null) {
            duenoId = mAuth.getCurrentUser().getUid();
        }

        if (mascotaId == null) {
            Toast.makeText(this, "Error: No se pudo cargar el perfil de la mascota", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivEditMascota = findViewById(R.id.iv_edit_mascota);
        ivAvatarMascota = findViewById(R.id.iv_avatar_mascota);
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        tvDescripcionMascota = findViewById(R.id.tv_descripcion_mascota);
        tvRaza = findViewById(R.id.tv_raza);
        tvSexo = findViewById(R.id.tv_sexo);
        tvEdad = findViewById(R.id.tv_edad);
        tvTamano = findViewById(R.id.tv_tamano);
        tvPeso = findViewById(R.id.tv_peso);
        tvEsterilizado = findViewById(R.id.tv_esterilizado);

        rvGaleria = findViewById(R.id.rv_galeria);
        rvGaleria.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        galeriaUrls = new ArrayList<>();
        galeriaAdapter = new GaleriaAdapter(this, galeriaUrls);
        rvGaleria.setAdapter(galeriaAdapter);

        btnSalud = findViewById(R.id.btn_salud);
        btnComportamiento = findViewById(R.id.btn_comportamiento);
        btnInstrucciones = findViewById(R.id.btn_instrucciones);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                showToast("Próximamente: Inicio");
                return true;
            } else if (itemId == R.id.menu_search) {
                showToast("Próximamente: Buscar");
                return true;
            } else if (itemId == R.id.menu_walks) {
                showToast("Próximamente: Paseos");
                return true;
            } else if (itemId == R.id.menu_messages) {
                showToast("Próximamente: Mensajes");
                return true;
            } else if (itemId == R.id.menu_perfil) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        ivEditMascota.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, EditarPerfilMascotaActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        });

        btnSalud.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaSaludActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        });

        btnComportamiento.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaComportamientoActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        });

        btnInstrucciones.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaInstruccionesActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        });
    }

    @SuppressWarnings("unchecked")
    private void cargarDatosMascota() {
        if (mascotaListener != null) mascotaListener.remove();
        mascotaListener = db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId)
                .addSnapshotListener((document, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (document != null && document.exists()) {
                        // Nombre
                        String nombre = document.getString("nombre");
                        tvNombreMascota.setText(nombre);

                        // Raza
                        String raza = document.getString("raza");
                        tvRaza.setText(raza != null ? raza : "No especificado");

                        // Sexo
                        String sexo = document.getString("sexo");
                        tvSexo.setText(sexo != null ? sexo : "No especificado");

                        // Edad calculada desde fecha_nacimiento
                        Timestamp fechaNacimiento = document.getTimestamp("fecha_nacimiento");
                        int edad = 0;
                        if (fechaNacimiento != null) {
                            Date birthDate = fechaNacimiento.toDate();
                            LocalDate localBirthDate = birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            LocalDate currentDate = LocalDate.now();
                            edad = Period.between(localBirthDate, currentDate).getYears();
                            tvEdad.setText(edad + " años");
                        } else {
                            tvEdad.setText("No especificada");
                        }

                        // Descripción (raza / sexo, edad)
                        StringBuilder descripcion = new StringBuilder();
                        if (raza != null) {
                            descripcion.append(raza);
                        }
                        if (sexo != null) {
                            if (descripcion.length() > 0) descripcion.append(" / ");
                            descripcion.append(sexo);
                        }
                        if (edad > 0) {
                            if (descripcion.length() > 0) descripcion.append(", ");
                            descripcion.append(edad).append(" años");
                        }
                        tvDescripcionMascota.setText(descripcion.toString());

                        // Tamaño
                        String tamano = document.getString("tamano");
                        tvTamano.setText(tamano != null ? tamano : "No especificado");

                        // Peso
                        Double peso = document.getDouble("peso");
                        tvPeso.setText(peso != null ? peso + " kg" : "No especificado");

                        // Esterilizado
                        Boolean esterilizado = document.getBoolean("esterilizado");
                        tvEsterilizado.setText(esterilizado != null && esterilizado ? "Sí" : "No");

                        // Foto principal
                        String fotoUrl = document.getString("foto_principal_url");
                        if (fotoUrl != null && !fotoUrl.isEmpty()) {
                            Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatarMascota);
                        }

                        // Galería
                        galeriaUrls.clear();
                        List<String> galeria = (List<String>) document.get("galeria_fotos");
                        if (galeria != null && !galeria.isEmpty()) {
                            galeriaUrls.addAll(galeria);
                        } else if (fotoUrl != null) {
                            // Si no hay galería, usar la foto principal
                            galeriaUrls.add(fotoUrl);
                        }
                        galeriaAdapter.notifyDataSetChanged();

                    } else {
                        Log.d(TAG, "Current data: null");
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        cargarDatosMascota();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mascotaListener != null) {
            mascotaListener.remove();
        }
    }
}
