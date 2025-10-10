package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.util.Log;

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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class PerfilDuenoActivity extends AppCompatActivity {

    private static final String TAG = "PerfilDuenoActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ImageView ivAvatar, ivVerificado, ivBack;
    private TextView tvNombreCompleto, tvRol;
    private EditText etEmailDueno, etTelefonoDueno;
    private ImageView ivEditEmail, ivSaveEmail, ivEditTelefono, ivSaveTelefono, ivEditMascotas;
    private String originalEmail, originalTelefono;
    private RecyclerView rvMascotas;
    private MascotaPerfilAdapter mascotaAdapter;
    private List<Pet> petList;
    private Button btnCerrarSesion;
    private View btnEditarPerfil, btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;
    private String metodoPagoId; // Variable to store the payment method ID
    private View skeletonLayout;
    private androidx.core.widget.NestedScrollView scrollViewContent;
    private boolean isContentVisible = false;

    // Listeners for real-time updates
    private ListenerRegistration duenoListener;
    private ListenerRegistration mascotasListener;
    private ListenerRegistration metodoPagoListener;


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
            String uid = currentUser.getUid();
            cargarDatosDueno(uid);
            cargarMascotas(uid);
            cargarMetodoPagoPredeterminado(uid);
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

        etEmailDueno = findViewById(R.id.et_email_dueno);
        ivEditEmail = findViewById(R.id.iv_edit_email);
        ivSaveEmail = findViewById(R.id.iv_save_email);

        etTelefonoDueno = findViewById(R.id.et_telefono_dueno);
        ivEditTelefono = findViewById(R.id.iv_edit_telefono);
        ivSaveTelefono = findViewById(R.id.iv_save_telefono);
        ivEditMascotas = findViewById(R.id.iv_edit_mascotas);

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
        skeletonLayout = findViewById(R.id.skeleton_layout);
        scrollViewContent = findViewById(R.id.scroll_view_content);


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
                // Ya estamos aquí
                return true;
            }
            return false;
        });
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        // Email editing
        ivEditEmail.setOnClickListener(v -> {
            etEmailDueno.setEnabled(true);
            ivEditEmail.setVisibility(View.GONE);
            ivSaveEmail.setVisibility(View.VISIBLE);
            etEmailDueno.requestFocus();
        });

        ivSaveEmail.setOnClickListener(v -> {
            String newEmail = etEmailDueno.getText().toString();
            if (!newEmail.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar cambio de correo")
                        .setMessage("¿Estás seguro de que quieres cambiar tu correo electrónico a " + newEmail + "?")
                        .setPositiveButton("Sí", (dialog, which) -> {
                            updateContactInfo("correo", newEmail);
                            etEmailDueno.setEnabled(false);
                            ivSaveEmail.setVisibility(View.GONE);
                            ivEditEmail.setVisibility(View.VISIBLE);
                        })
                        .setNegativeButton("No", (dialog, which) -> {
                            // Revertir cambios o simplemente cerrar el diálogo
                            etEmailDueno.setText(originalEmail); // Revertir al valor original
                            etEmailDueno.setEnabled(false);
                            ivSaveEmail.setVisibility(View.GONE);
                            ivEditEmail.setVisibility(View.VISIBLE);
                        })
                        .show();
            } else {
                showToast("El correo electrónico no puede estar vacío.");
            }
        });

        // Phone editing
        ivEditTelefono.setOnClickListener(v -> {
            etTelefonoDueno.setEnabled(true);
            ivEditTelefono.setVisibility(View.GONE);
            ivSaveTelefono.setVisibility(View.VISIBLE);
            etTelefonoDueno.requestFocus();
        });

        ivSaveTelefono.setOnClickListener(v -> {
            String newTelefono = etTelefonoDueno.getText().toString();
            if (!newTelefono.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar cambio de teléfono")
                        .setMessage("¿Estás seguro de que quieres cambiar tu número de teléfono a " + newTelefono + "?")
                        .setPositiveButton("Sí", (dialog, which) -> {
                            updateContactInfo("telefono", newTelefono);
                            etTelefonoDueno.setEnabled(false);
                            ivSaveTelefono.setVisibility(View.GONE);
                            ivEditTelefono.setVisibility(View.VISIBLE);
                        })
                        .setNegativeButton("No", (dialog, which) -> {
                            // Revertir cambios o simplemente cerrar el diálogo
                            etTelefonoDueno.setText(originalTelefono); // Revertir al valor original
                            etTelefonoDueno.setEnabled(false);
                            ivSaveTelefono.setVisibility(View.GONE);
                            ivEditTelefono.setVisibility(View.VISIBLE);
                        })
                        .show();
            } else {
                showToast("El número de teléfono no puede estar vacío.");
            }
        });

        ivEditMascotas.setOnClickListener(v -> showToast("Próximamente: Gestionar Mascotas"));

        btnEditarPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, EditarPerfilDuenoActivity.class);
            startActivity(intent);
        });
        btnNotificaciones.setOnClickListener(v -> showToast("Próximamente: Notificaciones"));
        btnMetodosPago.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, MetodoPagoActivity.class);
            // Pass the payment method ID to the activity
            if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
                intent.putExtra("metodo_pago_id", metodoPagoId);
            }
            startActivity(intent);
        });
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
        if (duenoListener != null) duenoListener.remove();
        duenoListener = db.collection("usuarios").document(uid).addSnapshotListener((document, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (document != null && document.exists()) {
                tvNombreCompleto.setText(document.getString("nombre_display"));
                tvRol.setText("Dueña de mascotas");

                etEmailDueno.setText(document.getString("correo"));
                etTelefonoDueno.setText(document.getString("telefono"));
                originalEmail = document.getString("correo");
                originalTelefono = document.getString("telefono");

                String fotoUrl = document.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatar);
                }

                String verificacion = document.getString("verificacion_estado");
                if ("APROBADO".equals(verificacion)) {
                    ivVerificado.setVisibility(View.VISIBLE);
                } else {
                    ivVerificado.setVisibility(View.GONE);
                }
                showContent();
            } else {
                Log.d(TAG, "Current data: null");
            }
        });
    }

    private void cargarMascotas(String uid) {
        if (mascotasListener != null) mascotasListener.remove();
        mascotasListener = db.collection("duenos").document(uid).collection("mascotas")
                .addSnapshotListener((value, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        Toast.makeText(this, "Error al cargar las mascotas.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    petList.clear();
                    if (value == null || value.isEmpty()) {
                        // Opcional: mostrar un mensaje de que no hay mascotas
                    } else {
                        for (QueryDocumentSnapshot doc : value) {
                            Pet pet = new Pet();
                            pet.setId(doc.getId());
                            pet.setName(doc.getString("nombre"));
                            pet.setBreed(doc.getString("raza"));
                            pet.setAvatarUrl(doc.getString("foto_principal_url"));
                            petList.add(pet);
                        }
                    }
                    mascotaAdapter.notifyDataSetChanged();
                    showContent();
                });
    }

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (metodoPagoListener != null) metodoPagoListener.remove();
        Query query = db.collection("usuarios").document(uid).collection("metodos_pago")
                .whereEqualTo("predeterminado", true).limit(1);

        metodoPagoListener = query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                metodoPagoId = queryDocumentSnapshots.getDocuments().get(0).getId();
            } else {
                // Si no hay predeterminado, busca el más reciente en tiempo real también
                db.collection("usuarios").document(uid).collection("metodos_pago")
                        .orderBy("fecha_registro", Query.Direction.DESCENDING).limit(1)
                        .addSnapshotListener((snapshots, error) -> {
                            if (error != null) {
                                Log.w(TAG, "Listen failed for recent payment method.", error);
                                return;
                            }
                            if (snapshots != null && !snapshots.isEmpty()) {
                                metodoPagoId = snapshots.getDocuments().get(0).getId();
                            }
                        });
            }
        });
    }

    private void showContent() {
        if (!isContentVisible) {
            isContentVisible = true;
            skeletonLayout.setVisibility(View.GONE);
            scrollViewContent.setVisibility(View.VISIBLE);
        }
    }

    private void updateContactInfo(String field, String value) {
        if (currentUser != null) {
            db.collection("usuarios").document(currentUser.getUid())
                    .update(field, value)
                    .addOnSuccessListener(aVoid -> showToast(field + " actualizado correctamente."))
                    .addOnFailureListener(e -> showToast("Error al actualizar " + field + ": " + e.getMessage()));
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (duenoListener != null) {
            duenoListener.remove();
        }
        if (mascotasListener != null) {
            mascotasListener.remove();
        }
        if (metodoPagoListener != null) {
            metodoPagoListener.remove();
        }
    }
}
