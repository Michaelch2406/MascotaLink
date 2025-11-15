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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascotalink.util.BottomNavManager;

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

    // Views
    private ImageView ivBack, ivAvatarMascota, ivEditMascota;
    private TextView tvNombreMascota, tvDescripcionMascota;
    private TextView tvRaza, tvSexo, tvEdad, tvTamano, tvPeso, tvEsterilizado;
    private RecyclerView rvGaleria;
    private View btnSalud, btnComportamiento, btnInstrucciones;
    private GaleriaAdapter galeriaAdapter;
    private List<String> galeriaUrls;
    private BottomNavigationView bottomNav;
    private View contentContainer, errorContainer; // Containers for content and error message
    private TextView tvErrorMessage; // TextView inside errorContainer

    // Data
    private String duenoId;
    private String mascotaId;
    private String currentUserId;
    private ListenerRegistration mascotaListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_mascota);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get current user
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // Get IDs from intent
        duenoId = getIntent().getStringExtra("dueno_id");
        mascotaId = getIntent().getStringExtra("mascota_id");

        if (duenoId == null || duenoId.isEmpty() || mascotaId == null || mascotaId.isEmpty()) {
            // Show error state immediately if IDs are missing
            initViews(); // Need to init views to show error
            showErrorState("No se pudo cargar el perfil de la mascota. Faltan datos.");
            // Hide back button if we can't even load the profile
            if(ivBack != null) ivBack.setVisibility(View.GONE);
            return;
        }

        initViews();
        setupRoleBasedUI();
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
        bottomNav = findViewById(R.id.bottom_nav);

        contentContainer = findViewById(R.id.content_container);
        errorContainer = findViewById(R.id.error_container);
        tvErrorMessage = findViewById(R.id.tv_error_message);
    }

    private void setupRoleBasedUI() {
        boolean isOwner = duenoId.equals(currentUserId);

        // The main edit icon is only visible to the owner
        ivEditMascota.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        // The bottom navigation is only visible to the owner
        if (isOwner) {
            bottomNav.setVisibility(View.VISIBLE);
            // Assuming the owner is always "DUEÑO"
            BottomNavManager.setupBottomNav(this, bottomNav, "DUEÑO", R.id.menu_perfil);
        } else {
            bottomNav.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        // This listener is only visible to the owner, so no role check is needed here.
        ivEditMascota.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, EditarPerfilMascotaActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        });

        // These buttons are visible to everyone (owner and walker)
        btnSalud.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaSaludActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            // Pass a flag to indicate read-only mode if not owner
            intent.putExtra("read_only", !duenoId.equals(currentUserId));
            startActivity(intent);
        });

        btnComportamiento.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaComportamientoActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            // Pass a flag to indicate read-only mode if not owner
            intent.putExtra("read_only", !duenoId.equals(currentUserId));
            startActivity(intent);
        });

        btnInstrucciones.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, MascotaInstruccionesActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            // Pass a flag to indicate read-only mode if not owner
            intent.putExtra("read_only", !duenoId.equals(currentUserId));
            startActivity(intent);
        });
    }

    @SuppressWarnings("unchecked")
    private void cargarDatosMascota() {
        if (mascotaListener != null) mascotaListener.remove();
        
        Log.d(TAG, "Cargando mascota con duenoId: " + duenoId + " y mascotaId: " + mascotaId);

        // Workaround: Use a query instead of a direct get, in case of an emulator bug with security rules on subcollection document gets.
        mascotaListener = db.collection("duenos").document(duenoId)
                .collection("mascotas").whereEqualTo(com.google.firebase.firestore.FieldPath.documentId(), mascotaId)
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        showErrorState("Error al cargar el perfil de la mascota.");
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        
                        showContentState();
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
                        } else {
                            ivAvatarMascota.setImageResource(R.drawable.ic_pet_placeholder);
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
                        Log.d(TAG, "No such document for duenoId: " + duenoId + " y mascotaId: " + mascotaId);
                        showErrorState("El perfil de esta mascota no fue encontrado o fue eliminado.");
                    }
                });
    }

    private void showErrorState(String message) {
        contentContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
    }

    private void showContentState() {
        contentContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only load data if IDs are valid
        if (duenoId != null && !duenoId.isEmpty() && mascotaId != null && !mascotaId.isEmpty()) {
            cargarDatosMascota();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mascotaListener != null) {
            mascotaListener.remove();
        }
    }
}
