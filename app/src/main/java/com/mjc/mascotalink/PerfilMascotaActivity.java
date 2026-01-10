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
import com.mjc.mascotalink.util.ImageViewerUtil;
import com.mjc.mascotalink.MyApplication;

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
    private TextView tvNombreMascota, tvDescripcionMascota, tvInfoAdicional;
    private TextView tvRaza, tvSexo, tvEdad, tvTamano, tvPeso, tvEsterilizado;
    private RecyclerView rvGaleria;
    private View btnSalud, btnComportamiento, btnInstrucciones;
    private View btnAddPhoto, emptyGaleriaContainer, btnAddPhotoEmpty;
    private GaleriaAdapter galeriaAdapter;
    private List<String> galeriaUrls;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "DUEÑO";
    private int bottomNavSelectedItem = R.id.menu_perfil;
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

        

        // Setup Bottom Navigation immediately if visible

        if (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE) {

            setupBottomNavigation();

        }

        

        setupListeners();

    }



    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivEditMascota = findViewById(R.id.iv_edit_mascota);
        ivAvatarMascota = findViewById(R.id.iv_avatar_mascota);
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        tvDescripcionMascota = findViewById(R.id.tv_descripcion_mascota);
        tvInfoAdicional = findViewById(R.id.tv_info_adicional);
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

        // NUEVO: Configurar click listener para abrir imágenes de galería en fullscreen
        galeriaAdapter.setOnImageClickListener((position, imageUrl) -> {
            // Si hay varias fotos, usar GaleriaActivity con ViewPager2
            if (galeriaUrls.size() > 1) {
                Intent intent = new Intent(PerfilMascotaActivity.this, GaleriaActivity.class);
                intent.putStringArrayListExtra("imageUrls", new ArrayList<>(galeriaUrls));
                // Nota: GaleriaActivity podría mejorarse para soportar startPosition
                startActivity(intent);
            } else {
                // Si es solo una foto, usar zoom dialog
                ImageViewerUtil.showFullscreenImage(PerfilMascotaActivity.this, imageUrl);
            }
        });

        // Galería - botones de añadir
        btnAddPhoto = findViewById(R.id.btn_add_photo);
        emptyGaleriaContainer = findViewById(R.id.empty_galeria_container);
        btnAddPhotoEmpty = findViewById(R.id.btn_add_photo_empty);

        btnSalud = findViewById(R.id.btn_salud);
        btnComportamiento = findViewById(R.id.btn_comportamiento);
        btnInstrucciones = findViewById(R.id.btn_instrucciones);
        bottomNav = findViewById(R.id.bottom_nav);

        contentContainer = findViewById(R.id.content_container);
        errorContainer = findViewById(R.id.error_container);
        tvErrorMessage = findViewById(R.id.tv_error_message);
    }

    private void setupRoleBasedUI() {
        boolean isOwner = duenoId != null && duenoId.equals(currentUserId);

        // The main edit icon is only visible to the owner
        ivEditMascota.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        // Gallery add buttons only visible to owner
        if (btnAddPhoto != null) {
            btnAddPhoto.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        }

        // The bottom navigation is only visible to the owner
        if (isOwner) {
            bottomNav.setVisibility(View.VISIBLE);
            bottomNavRole = "DUEÑO";
            setupBottomNavigation();
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

        // Gallery add photo buttons - open gallery management
        View.OnClickListener addPhotoListener = v -> {
            Intent intent = new Intent(PerfilMascotaActivity.this, GestionarGaleriaMascotaActivity.class);
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", mascotaId);
            startActivity(intent);
        };

        if (btnAddPhoto != null) {
            btnAddPhoto.setOnClickListener(addPhotoListener);
        }
        if (btnAddPhotoEmpty != null) {
            btnAddPhotoEmpty.setOnClickListener(addPhotoListener);
        }
        if (emptyGaleriaContainer != null) {
            emptyGaleriaContainer.setOnClickListener(addPhotoListener);
        }

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

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        if (bottomNav == null || bottomNav.getVisibility() != View.VISIBLE) {
            return;
        }
        BottomNavManager.setupBottomNav(this, bottomNav, bottomNavRole, bottomNavSelectedItem);
    }

    @SuppressWarnings("unchecked")
    private void cargarDatosMascota() {
        if (mascotaListener != null) mascotaListener.remove();
        
        Log.d(TAG, "Cargando mascota con duenoId: " + duenoId + " y mascotaId: " + mascotaId);

        mascotaListener = db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId)
                .addSnapshotListener((document, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        showErrorState("Error al cargar el perfil de la mascota.");
                        return;
                    }

                    if (document != null && document.exists()) {
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
                            Glide.with(this).load(MyApplication.getFixedUrl(fotoUrl)).circleCrop().into(ivAvatarMascota);

                            // NUEVO: Click listener para ver foto en fullscreen con zoom
                            final String finalFotoUrl = fotoUrl;
                            ivAvatarMascota.setOnClickListener(v ->
                                ImageViewerUtil.showFullscreenImage(PerfilMascotaActivity.this, finalFotoUrl));
                        } else {
                            ivAvatarMascota.setImageResource(R.drawable.ic_pet_placeholder);
                            ivAvatarMascota.setOnClickListener(null);
                        }

                        // Galería - usar galeria_mascotas
                        galeriaUrls.clear();
                        List<String> galeria = (List<String>) document.get("galeria_mascotas");
                        if (galeria != null && !galeria.isEmpty()) {
                            for (String url : galeria) {
                                galeriaUrls.add(MyApplication.getFixedUrl(url));
                            }
                        } else if (fotoUrl != null) {
                            // Si no hay galería, usar la foto principal
                            galeriaUrls.add(MyApplication.getFixedUrl(fotoUrl));
                        }
                        galeriaAdapter.notifyDataSetChanged();

                        // Mostrar/ocultar estado vacío de galería
                        boolean isOwner = duenoId != null && duenoId.equals(currentUserId);
                        if (galeriaUrls.isEmpty() && isOwner) {
                            rvGaleria.setVisibility(View.GONE);
                            if (emptyGaleriaContainer != null) {
                                emptyGaleriaContainer.setVisibility(View.VISIBLE);
                            }
                        } else {
                            rvGaleria.setVisibility(View.VISIBLE);
                            if (emptyGaleriaContainer != null) {
                                emptyGaleriaContainer.setVisibility(View.GONE);
                            }
                        }

                        // Info adicional (sexo · edad)
                        if (tvInfoAdicional != null) {
                            StringBuilder infoAdicional = new StringBuilder();
                            if (sexo != null) {
                                infoAdicional.append(sexo);
                            }
                            if (edad > 0) {
                                if (infoAdicional.length() > 0) infoAdicional.append(" · ");
                                infoAdicional.append(edad).append(" años");
                            }
                            tvInfoAdicional.setText(infoAdicional.toString());
                        }

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
