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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;

import java.util.ArrayList;
import java.util.List;

public class PerfilDuenoActivity extends AppCompatActivity {

    private static final String TAG = "PerfilDuenoActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Se añade el AuthStateListener
    private FirebaseAuth.AuthStateListener mAuthListener;

    private ImageView ivAvatar, ivVerificado, ivBack;
    private TextView tvNombreCompleto, tvRol;
    private EditText etEmailDueno, etTelefonoDueno;
    private ImageView ivEditEmail, ivSaveEmail, ivEditTelefono, ivSaveTelefono, ivEditMascotas;
    private String originalEmail, originalTelefono;
    private RecyclerView rvMascotas;
    private MascotaPerfilAdapter mascotaAdapter;
    private List<Pet> petList;
    private Button btnCerrarSesion;
    private ImageView ivEditPerfil;
    private View btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;
    private String metodoPagoId; // Variable to store the payment method ID
    private View skeletonLayout;
    private androidx.core.widget.NestedScrollView scrollViewContent;
    private boolean isContentVisible = false;
    private View btnFavoritos; // Botón para favoritos

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

        initViews();
        setupListeners();
        setupAuthListener(); // Se configura el nuevo listener

        // Si recibimos un id_dueno por Intent, cargar directamente ese perfil
        String duenoIdFromIntent = getIntent().getStringExtra("id_dueno");
        if (duenoIdFromIntent != null && !duenoIdFromIntent.isEmpty()) {
            setupRoleBasedUI(duenoIdFromIntent); // Configurar UI basado en el rol
            cargarDatosDueno(duenoIdFromIntent);
            cargarMascotas(duenoIdFromIntent);
            cargarMetodoPagoPredeterminado(duenoIdFromIntent);
        }
    }

    private void setupRoleBasedUI(String profileOwnerId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Es mi perfil si el usuario actual no es nulo y su UID coincide con el del perfil que se está viendo.
        boolean isMyProfile = currentUser != null && currentUser.getUid().equals(profileOwnerId);

        // Controles de edición de campos (siempre empiezan deshabilitados)
        ivEditEmail.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        ivEditTelefono.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        etEmailDueno.setEnabled(false);
        etTelefonoDueno.setEnabled(false);
        ivSaveEmail.setVisibility(View.GONE);
        ivSaveTelefono.setVisibility(View.GONE);

        // Icono de edición principal y de mascotas
        ivEditPerfil.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        ivEditMascotas.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);

        // Secciones completas (Ajustes, Soporte) y botón de cerrar sesión
        View seccionAjustes = findViewById(R.id.seccion_ajustes_cuenta);
        if (seccionAjustes != null) {
            seccionAjustes.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        }
        View seccionSoporte = findViewById(R.id.seccion_soporte_legal);
        if (seccionSoporte != null) {
            seccionSoporte.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        }
        if (btnCerrarSesion != null) {
            btnCerrarSesion.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
        }


        // La barra de navegación inferior solo debe ser visible para el dueño del perfil
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setVisibility(isMyProfile ? View.VISIBLE : View.GONE);
    }

    private void setupAuthListener() {
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                // El usuario está logueado.
                Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                String uid = user.getUid();

                // Si no se pasó un ID por intent, significa que el usuario está viendo su propio perfil.
                if (getIntent().getStringExtra("id_dueno") == null) {
                    setupRoleBasedUI(uid); // Configurar UI para el propio perfil
                    cargarDatosDueno(uid);
                    cargarMascotas(uid);
                    cargarMetodoPagoPredeterminado(uid);
                }
            } else {
                // El usuario no está logueado, redirigir a LoginActivity.
                Log.d(TAG, "onAuthStateChanged:signed_out");
                Intent intent = new Intent(PerfilDuenoActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        };
    }


    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivEditPerfil = findViewById(R.id.iv_edit_perfil);
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
        ivEditPerfil = findViewById(R.id.iv_edit_perfil);
        btnFavoritos = findViewById(R.id.btn_favoritos); // Botón de favoritos
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
            if (itemId == R.id.menu_perfil) {
                // Ya estamos aquí
                return true;
            } else if (itemId == R.id.menu_home) {
                showToast("Próximamente: Inicio");
                return false; // No cambiar la selección
            } else if (itemId == R.id.menu_search) {
                Intent intent = new Intent(this, BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_walks) {
                Intent intent = new Intent(this, PaseosActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_messages) {
                showToast("Próximamente: Mensajes");
                return false; // No cambiar la selección
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

        ivEditPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, EditarPerfilDuenoActivity.class);
            startActivity(intent);
        });

        btnFavoritos.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, FavoritosActivity.class);
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
            // Detach listeners FIRST to prevent PERMISSION_DENIED noise on logout
            if (duenoListener != null) duenoListener.remove();
            if (mascotasListener != null) mascotasListener.remove();
            if (metodoPagoListener != null) metodoPagoListener.remove();

            // Limpiar preferencias de "recordar sesión"
            getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();

            // Sign out AFTER detaching listeners
            mAuth.signOut();
            // The AuthStateListener will automatically handle the navigation to LoginActivity
        });
    }

    private void cargarDatosDueno(String uid) {
        if (duenoListener != null) duenoListener.remove();
        duenoListener = db.collection("usuarios").document(uid).addSnapshotListener((document, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                // Don't show toast on initial load fail, AuthListener handles redirection
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
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("usuarios").document(user.getUid())
                    .update(field, value)
                    .addOnSuccessListener(aVoid -> showToast(field + " actualizado correctamente."))
                    .addOnFailureListener(e -> showToast("Error al actualizar " + field + ": " + e.getMessage()));
        } else {
            showToast("Error: No se pudo verificar el usuario para la actualización.");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Clear Glide load to prevent crash on destroy
        if (ivAvatar != null) {
            Glide.with(this).clear(ivAvatar);
        }

        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
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
