package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascota.modelo.Resena;
import com.mjc.mascota.ui.perfil.ResenaAdapter;
import com.mjc.mascotalink.security.CredentialManager;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.network.SocketManager;
import org.json.JSONObject;
import android.text.format.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerfilDuenoActivity extends AppCompatActivity {

    private static final String TAG = "PerfilDuenoActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SocketManager socketManager;

    // Views
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageView ivBack, ivEditPerfil;
    private de.hdodenhof.circleimageview.CircleImageView ivAvatar;
    private ImageButton btnMensaje;
    private ImageView ivVerificadoBadge;
    private TextView tvNombre, tvRol, tvVerificado;
    private TextView badgePerfilEnLinea;
    private TextView tvMascotasRegistradas, tvPaseosSolicitados, tvMiembroDesdeStat;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout llAcercaDe, llResenas, ajustes_section, soporte_section;
    private TabLayout tabLayout;
    private Button btnCerrarSesion;
    private TextView btnFavoritos, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos, btnMisPaseos;
    private View btnNotificaciones;
    private androidx.appcompat.widget.SwitchCompat switchNotificaciones;
    private TextView tvEmailDueno, tvTelefonoDueno;
    private ImageView btnCopyEmail, btnCopyTelefono;
    private View skeletonLayout;
    private NestedScrollView scrollViewContent;
    private SwipeRefreshLayout swipeRefresh;
    private BottomNavigationView bottomNav;

    // Mascotas
    private LinearLayout btnMisMascotas;
    private com.mjc.mascotalink.views.OverlappingAvatarsView overlappingMascotas;
    private ListenerRegistration mascotasListener;

    // Reseñas
    private RecyclerView recyclerViewResenas;
    private LinearLayout llEmptyReviews;
    private ResenaAdapter resenaAdapter;
    private List<Resena> resenasList = new ArrayList<>();
    private DocumentSnapshot lastVisibleResena = null;
    private boolean isLoadingResenas = false;
    private Button btnVerMasResenas;

    // State
    private boolean isContentVisible = false;
    private String duenoId;
    private String currentUserId;
    private String currentUserRole;
    private String metodoPagoId;
    private String bottomNavRole = "Dueño";
    private int bottomNavSelectedItem = R.id.menu_perfil;
    private boolean isOwnProfile = false; // Declared as field

    // Listeners
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ListenerRegistration duenoListener;
    private ListenerRegistration duenoStatsListener;
    private ListenerRegistration metodoPagoListener;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 123;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_dueno);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        socketManager = SocketManager.getInstance(this);

        initViews();

        // Manual Toolbar Setup
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        ivBack.setOnClickListener(v -> finish());

        setupListeners();
        // setupTabs() will be called after role is determined in setupRoleBasedUI
        setupResenasRecyclerView();
        setupAuthListener();

        // Determine duenoId
        String idFromIntent = getIntent().getStringExtra("id_dueno");
        if (idFromIntent == null) {
             idFromIntent = getIntent().getStringExtra("id_Dueno"); // Fallback
        }
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            duenoId = idFromIntent != null ? idFromIntent : currentUserId;
            String cachedRole = BottomNavManager.getUserRole(this);
            if (cachedRole != null) {
                currentUserRole = cachedRole;
            }
        } else {
            duenoId = idFromIntent;
        }

        com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);

        if (bottomNav != null) {
            setupBottomNavigation();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        ivBack = findViewById(R.id.iv_back);
        ivEditPerfil = findViewById(R.id.iv_edit_perfil);
        ivAvatar = findViewById(R.id.iv_avatar);
        btnMensaje = findViewById(R.id.btn_mensaje);
        tvNombre = findViewById(R.id.tv_nombre);
        tvRol = findViewById(R.id.tv_rol);
        ivVerificadoBadge = findViewById(R.id.iv_verificado_badge);
        tvVerificado = findViewById(R.id.tv_verificado);
        badgePerfilEnLinea = findViewById(R.id.badge_perfil_en_linea);

        tvMascotasRegistradas = findViewById(R.id.tv_mascotas_registradas);
        tvPaseosSolicitados = findViewById(R.id.tv_paseos_solicitados);
        tvMiembroDesdeStat = findViewById(R.id.tv_miembro_desde_stat);

        tvRatingValor = findViewById(R.id.tv_rating_valor);
        ratingBar = findViewById(R.id.rating_bar);
        tvResenasTotal = findViewById(R.id.tv_resenas_total);

        tabLayout = findViewById(R.id.tab_layout);
        llAcercaDe = findViewById(R.id.ll_acerca_de);
        llResenas = findViewById(R.id.ll_resenas);

        // Acerca de content
        btnMisMascotas = findViewById(R.id.btn_mis_mascotas);
        overlappingMascotas = findViewById(R.id.overlapping_mascotas);

        tvEmailDueno = findViewById(R.id.tv_email_dueno);
        tvTelefonoDueno = findViewById(R.id.tv_telefono_dueno);
        btnCopyEmail = findViewById(R.id.btn_copy_email);
        btnCopyTelefono = findViewById(R.id.btn_copy_telefono);

        ajustes_section = findViewById(R.id.ajustes_section);
        btnMisPaseos = findViewById(R.id.btn_mis_paseos);
        btnFavoritos = findViewById(R.id.btn_favoritos);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
        switchNotificaciones = findViewById(R.id.switch_notificaciones);
        btnMetodosPago = findViewById(R.id.btn_metodos_pago);

        soporte_section = findViewById(R.id.soporte_section);
        btnPrivacidad = findViewById(R.id.btn_privacidad);
        btnCentroAyuda = findViewById(R.id.btn_centro_ayuda);
        btnTerminos = findViewById(R.id.btn_terminos);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);

        // Reseñas content
        recyclerViewResenas = findViewById(R.id.recycler_view_resenas);
        llEmptyReviews = findViewById(R.id.ll_empty_reviews);
        btnVerMasResenas = findViewById(R.id.btn_ver_mas_resenas);

        skeletonLayout = findViewById(R.id.skeleton_layout);
        scrollViewContent = findViewById(R.id.scroll_view_content);
        swipeRefresh = findViewById(R.id.swipe_refresh_perfil);
        bottomNav = findViewById(R.id.bottom_nav);

        // Inicialmente ocultar TODO el contenido y mostrar skeleton
        scrollViewContent.setVisibility(View.GONE);
        ivEditPerfil.setVisibility(View.GONE);
        btnMensaje.setVisibility(View.GONE);
        showSkeleton();
    }

    private void setupListeners() {
        ivEditPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, EditarPerfilDuenoActivity.class);
            startActivity(intent);
        });

        if (btnMisPaseos != null) {
            btnMisPaseos.setOnClickListener(v -> {
                Intent intent = new Intent(PerfilDuenoActivity.this, HistorialPaseosActivity.class);
                intent.putExtra("rol_usuario", "Dueño");
                startActivity(intent);
            });
        }

        btnFavoritos.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, FavoritosActivity.class);
            startActivity(intent);
        });

        btnMetodosPago.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, MetodoPagoActivity.class);
            if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
                intent.putExtra("metodo_pago_id", metodoPagoId);
            }
            startActivity(intent);
        });

        btnNotificaciones.setOnClickListener(v -> startActivity(new Intent(PerfilDuenoActivity.this, NotificacionesActivity.class)));

        // Configurar switch de notificaciones
        com.mjc.mascotalink.utils.NotificacionesPreferences notifPrefs = new com.mjc.mascotalink.utils.NotificacionesPreferences(this);
        notifPrefs.loadFromFirestore(() -> {
            switchNotificaciones.setChecked(notifPrefs.isNotificacionesEnabled());
        });

        switchNotificaciones.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Solo si el cambio fue por el usuario
                notifPrefs.setNotificacionesEnabled(isChecked);
                String mensaje = isChecked ? "Notificaciones activadas" : "Notificaciones desactivadas";
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
            }
        });

        btnPrivacidad.setOnClickListener(v -> startActivity(new Intent(PerfilDuenoActivity.this, PoliticaPrivacidadActivity.class)));
        btnCentroAyuda.setOnClickListener(v -> startActivity(new Intent(PerfilDuenoActivity.this, CentroAyudaActivity.class)));
        btnTerminos.setOnClickListener(v -> startActivity(new Intent(PerfilDuenoActivity.this, TerminosCondicionesActivity.class)));

        // Setup SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener(this::refreshProfileData);
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);

        btnCerrarSesion.setOnClickListener(v -> {
            // Crear diálogo moderno con layout personalizado
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout_progress, null);
            AlertDialog progressDialog = new AlertDialog.Builder(PerfilDuenoActivity.this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            // Hacer el fondo del diálogo transparente para mostrar las esquinas redondeadas
            if (progressDialog.getWindow() != null) {
                progressDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            progressDialog.show();

            // Deshabilitar botón para evitar clicks múltiples
            btnCerrarSesion.setEnabled(false);

            // Ejecutar limpieza en segundo plano
            new Thread(() -> {
                try {
                    // Detener listeners y servicios
                    runOnUiThread(() -> detachDataListeners());
                    com.mjc.mascotalink.util.UnreadBadgeManager.stop();

                    // Limpiar credenciales
                    new CredentialManager(PerfilDuenoActivity.this).clearCredentials();

                    // Limpiar preferencias cifradas
                    try {
                        EncryptedPreferencesHelper.getInstance(PerfilDuenoActivity.this).clear();
                    } catch (Exception e) {
                        Log.e(TAG, "btnCerrarSesion: error limpiando prefs cifradas", e);
                    }

                    // Limpiar el rol guardado
                    com.mjc.mascotalink.util.BottomNavManager.clearUserRole(PerfilDuenoActivity.this);

                    // Desconectar WebSocket con timeout
                    try {
                        com.mjc.mascotalink.network.SocketManager.getInstance(PerfilDuenoActivity.this).disconnect();
                        // Dar tiempo para que se desconecte
                        Thread.sleep(500);
                    } catch (Exception e) {
                        Log.e(TAG, "Error desconectando WebSocket", e);
                    }

                    // Cerrar sesión en el hilo principal
                    runOnUiThread(() -> {
                        mAuth.signOut();

                        // Cerrar diálogo
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

                        // Navegar a LoginActivity inmediatamente
                        Intent intent = new Intent(PerfilDuenoActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error durante cierre de sesión", e);
                    runOnUiThread(() -> {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(PerfilDuenoActivity.this, "Error al cerrar sesión. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();
                        btnCerrarSesion.setEnabled(true);
                    });
                }
            }).start();
        });

        scrollViewContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (btnVerMasResenas.getVisibility() == View.GONE && v.getChildAt(v.getChildCount() - 1) != null) {
                if ((scrollY >= (v.getChildAt(v.getChildCount() - 1).getMeasuredHeight() - v.getMeasuredHeight())) &&
                        scrollY > oldScrollY && llResenas.getVisibility() == View.VISIBLE) {
                    cargarMasResenas(10); 
                }
            }
        });

        btnVerMasResenas.setOnClickListener(v -> {
            btnVerMasResenas.setVisibility(View.GONE);
            cargarMasResenas(10);
        });
        
        btnMisMascotas.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilDuenoActivity.this, MisMascotasActivity.class);
            intent.putExtra("dueno_id", duenoId);
            startActivity(intent);
        });

        btnCopyEmail.setOnClickListener(v -> copyToClipboard("Correo", tvEmailDueno.getText().toString()));
        btnCopyTelefono.setOnClickListener(v -> copyToClipboard("Teléfono", tvTelefonoDueno.getText().toString()));
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " copiado al portapapeles", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupTabs() {
        llAcercaDe.setVisibility(View.VISIBLE);
        llResenas.setVisibility(View.GONE);
        tabLayout.removeAllTabs(); // Clear existing tabs

        String aboutTabText = isOwnProfile ? "Mi perfil" : "Información";
        tabLayout.addTab(tabLayout.newTab().setText(aboutTabText), true);
        tabLayout.addTab(tabLayout.newTab().setText("Reseñas"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    llAcercaDe.setVisibility(View.VISIBLE);
                    llResenas.setVisibility(View.GONE);
                } else {
                    llAcercaDe.setVisibility(View.GONE);
                    llResenas.setVisibility(View.VISIBLE);
                    if (resenasList.isEmpty()) {
                        cargarMasResenas(4);
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupResenasRecyclerView() {
        recyclerViewResenas.setLayoutManager(new LinearLayoutManager(this));
        resenaAdapter = new ResenaAdapter(this, resenasList);
        resenaAdapter.setOnResenaAutorClickListener((autorId, autorRol) -> {
            Log.d(TAG, "Callback reseña - autorId: " + autorId + ", autorRol: " + autorRol);

            if (autorId == null || autorId.isEmpty()) {
                Toast.makeText(this, "No se puede abrir el perfil del usuario", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent;
            if ("paseador".equals(autorRol)) {
                intent = new Intent(PerfilDuenoActivity.this, PerfilPaseadorActivity.class);
                intent.putExtra("paseadorId", autorId);
            } else if ("dueno".equals(autorRol)) {
                intent = new Intent(PerfilDuenoActivity.this, PerfilDuenoActivity.class);
                intent.putExtra("id_dueno", autorId);
            } else {
                Log.e(TAG, "AutorRol no reconocido: '" + autorRol + "'");
                Toast.makeText(this, "Tipo de usuario desconocido", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(intent);
        });
        recyclerViewResenas.setAdapter(resenaAdapter);
    }

    private void setupAuthListener() {
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                currentUserId = user.getUid();
                if (duenoId == null) {
                    duenoId = currentUserId;
                }
                
                // Determine ownership immediately to avoid race conditions with showContent()
                isOwnProfile = duenoId != null && duenoId.equals(currentUserId);
                
                fetchCurrentUserRoleAndSetupUI();
                attachDataListeners();
                if (isOwnProfile) {
                    updateFcmToken();
                }
            } else {
                Intent intent = new Intent(PerfilDuenoActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        };
        btnMensaje.setOnClickListener(v -> {
            if (duenoId == null || currentUserId == null || duenoId.equals(currentUserId)) {
                Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
                return;
            }
            com.mjc.mascotalink.util.ChatHelper.openOrCreateChat(this, db, currentUserId, duenoId);
        });

    }

    private void refreshProfileData() {
        // Reload profile data
        attachDataListeners();
        cargarMascotasAvatares();
        cargarMasResenas(4);
        // Refresh indicator will stop automatically when showContent() is called
    }

    private void fetchCurrentUserRoleAndSetupUI() {
        if (currentUserId == null) {
            setupRoleBasedUI(); 
            return;
        }
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserRole = documentSnapshot.getString("rol");
                    }
                    setupRoleBasedUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user role", e);
                    setupRoleBasedUI();
                });
    }

        private void setupRoleBasedUI() {
            this.isOwnProfile = duenoId != null && duenoId.equals(currentUserId);

            if (this.isOwnProfile) {
                toolbarTitle.setText("Perfil");
                // No mostrar botones aún, se mostrarán cuando se carguen los datos
                ajustes_section.setVisibility(View.VISIBLE);
                soporte_section.setVisibility(View.VISIBLE);
                btnCerrarSesion.setVisibility(View.VISIBLE);
                bottomNavRole = "Dueño";
                bottomNavSelectedItem = R.id.menu_perfil;
                bottomNav.setVisibility(View.VISIBLE);
            } else {
                toolbarTitle.setText("Dueño");
                // No mostrar botones aún, se mostrarán cuando se carguen los datos
                ajustes_section.setVisibility(View.GONE);
                soporte_section.setVisibility(View.GONE);
                btnCerrarSesion.setVisibility(View.GONE);

                // Ocultar barra de navegación cuando ves el perfil de otro usuario
                bottomNav.setVisibility(View.GONE);
                bottomNavRole = null;
                bottomNavSelectedItem = 0;
            }
            com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);
            setupBottomNavigation();
            setupTabs(); // Call setupTabs here after isOwnProfile is determined
        }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = bottomNavRole != null ? bottomNavRole : "Dueño";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, bottomNavSelectedItem);
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    private void attachDataListeners() {
        detachDataListeners();
        showSkeleton(); // Mostrar skeleton antes de cargar datos

        // 1. Load User Basic Info & Stats
        DocumentReference userDocRef = db.collection("usuarios").document(duenoId);
        duenoListener = userDocRef.addSnapshotListener((usuarioDoc, e) -> {
            if (e != null) return;
            if (usuarioDoc != null && usuarioDoc.exists()) {
                tvNombre.setText(usuarioDoc.getString("nombre_display"));
                if (!isDestroyed() && !isFinishing()) {
                    Glide.with(this).load(MyApplication.getFixedUrl(usuarioDoc.getString("foto_perfil"))).placeholder(R.drawable.ic_user_placeholder).into(ivAvatar);
                }
                
                tvEmailDueno.setText(usuarioDoc.getString("correo"));
                tvTelefonoDueno.setText(usuarioDoc.getString("telefono"));
                
                Timestamp fechaRegistro = usuarioDoc.getTimestamp("fecha_registro");
                if (fechaRegistro != null) {
                    tvMiembroDesdeStat.setText("Desde " + new SimpleDateFormat("yyyy", Locale.getDefault()).format(fechaRegistro.toDate()));
                } else {
                    tvMiembroDesdeStat.setText("Reciente");
                }
                
                showContent();
            }
        });

        // 2. Load Dueno specific Stats
        duenoStatsListener = db.collection("duenos").document(duenoId).addSnapshotListener((duenoDoc, e) -> {
             if (e != null) return;
             if (duenoDoc != null && duenoDoc.exists()) {
                 String verificacion = duenoDoc.getString("verificacion_estado");
                 if ("APROBADO".equalsIgnoreCase(verificacion)) {
                     ivVerificadoBadge.setVisibility(View.VISIBLE);
                     tvVerificado.setVisibility(View.VISIBLE);
                 } else {
                     ivVerificadoBadge.setVisibility(View.GONE);
                     tvVerificado.setVisibility(View.GONE);
                 }
                 
                 Object paseosObj = duenoDoc.get("num_paseos_solicitados");
                 long paseosSolicitados = 0;
                 if (paseosObj instanceof Number) {
                     paseosSolicitados = ((Number) paseosObj).longValue();
                 } else if (paseosObj instanceof String) {
                     try {
                         paseosSolicitados = Long.parseLong((String) paseosObj);
                     } catch (NumberFormatException ex) {
                         paseosSolicitados = 0;
                     }
                 }
                 tvPaseosSolicitados.setText(paseosSolicitados + " Paseos");
                 
                 Double promedio = duenoDoc.getDouble("calificacion_promedio");
                 Long totalResenas = duenoDoc.getLong("total_resenas");
                 
                 if (promedio != null) {
                     tvRatingValor.setText(String.format(Locale.getDefault(), "%.1f", promedio));
                     ratingBar.setRating(promedio.floatValue());
                 } else {
                     tvRatingValor.setText("0.0");
                     ratingBar.setRating(0f);
                 }
                 
                 tvResenasTotal.setText((totalResenas != null ? totalResenas : 0) + " reviews");
             }
        });

        cargarMascotasAvatares();

        if (duenoId.equals(currentUserId)) {
            cargarMetodoPagoPredeterminado(duenoId);
        }

        // Setup presence listeners if viewing someone else's profile
        if (!duenoId.equals(currentUserId)) {
            setupPresenceListeners();
        }
    }
    
    private void cargarMascotasAvatares() {
        if (mascotasListener != null) mascotasListener.remove();

        mascotasListener = db.collection("duenos").document(duenoId).collection("mascotas")
                .whereEqualTo("activo", true)
                .addSnapshotListener((value, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error cargando avatares de mascotas", e);
                        overlappingMascotas.setPlaceholders(1);
                        return;
                    }

                    if (value != null && !value.isEmpty()) {
                        tvMascotasRegistradas.setText(value.size() + " Mascotas");

                        // Recolectar URLs de fotos para el OverlappingAvatarsView
                        java.util.List<String> fotoUrls = new java.util.ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            String fotoUrl = doc.getString("foto_principal_url");
                            if (fotoUrl != null && !fotoUrl.isEmpty()) {
                                fotoUrls.add(fotoUrl);
                            }
                        }

                        if (!fotoUrls.isEmpty()) {
                            overlappingMascotas.setImageUrls(fotoUrls);
                        } else {
                            overlappingMascotas.setPlaceholders(value.size());
                        }
                    } else {
                        tvMascotasRegistradas.setText("0 Mascotas");
                        overlappingMascotas.setPlaceholders(1);
                    }
                });
    }

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (metodoPagoListener != null) metodoPagoListener.remove();
        metodoPagoListener = db.collection("usuarios").document(uid).collection("metodos_pago")
                .whereEqualTo("predeterminado", true).limit(1)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e == null && queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        metodoPagoId = queryDocumentSnapshots.getDocuments().get(0).getId();
                    }
                });
    }
    
    private void cargarMasResenas(int limit) {
        if (isLoadingResenas) return;
        isLoadingResenas = true;

        Query query = db.collection("resenas_duenos")
                .whereEqualTo("duenoId", duenoId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit);

        if (lastVisibleResena != null) {
            query = query.startAfter(lastVisibleResena);
        }

        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                isLoadingResenas = false;
                if (resenasList.isEmpty()) {
                    llEmptyReviews.setVisibility(View.VISIBLE);
                    recyclerViewResenas.setVisibility(View.GONE);
                }
                return;
            }

            llEmptyReviews.setVisibility(View.GONE);
            recyclerViewResenas.setVisibility(View.VISIBLE);

            lastVisibleResena = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);

            List<Resena> nuevasResenas = new ArrayList<>();

            int fetchCount = queryDocumentSnapshots.size();
            boolean hasMore = false;

            if (limit == 4 && fetchCount == 4) {
                hasMore = true;
            }

            for (int i = 0; i < fetchCount; i++) {
                DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(i);
                Resena resena = new Resena();
                resena.setId(doc.getId());
                resena.setComentario(doc.getString("comentario"));
                Double calif = doc.getDouble("calificacion");
                resena.setCalificacion(calif != null ? calif.floatValue() : 0f);
                resena.setFecha(doc.getTimestamp("timestamp"));

                String autorId = doc.getString("autorId");
                String autorNombre = doc.getString("autorNombre");
                String autorFotoUrl = doc.getString("autorFotoUrl");
                String autorRol = doc.getString("autorRol");

                if (autorNombre == null || autorNombre.isEmpty()) {
                    autorNombre = "Paseador";
                }

                resena.setAutorId(autorId);
                resena.setAutorNombre(autorNombre);
                resena.setAutorFotoUrl(autorFotoUrl);
                resena.setAutorRol(autorRol);

                nuevasResenas.add(resena);
            }

            final boolean showButton = fetchCount >= limit;

            resenaAdapter.addResenas(nuevasResenas);
            isLoadingResenas = false;

            if (showButton) {
                btnVerMasResenas.setVisibility(View.VISIBLE);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error cargando reseñas", e);
            isLoadingResenas = false;
            // Asegurar que el SwipeRefresh se detenga en caso de error
            if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void detachDataListeners() {
        if (duenoListener != null) {
            duenoListener.remove();
            duenoListener = null;
        }
        if (duenoStatsListener != null) {
            duenoStatsListener.remove();
            duenoStatsListener = null;
        }
        if (mascotasListener != null) {
            mascotasListener.remove();
            mascotasListener = null;
        }
        if (metodoPagoListener != null) {
            metodoPagoListener.remove();
            metodoPagoListener = null;
        }
        cleanupPresenceListeners();
    }

    private void setupPresenceListeners() {
        if (socketManager == null || !socketManager.isConnected()) {
            Log.w(TAG, "SocketManager no conectado, no se puede configurar presencia");
            return;
        }

        // Listen for when dueño connects
        socketManager.on("user_connected", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(duenoId)) {
                    runOnUiThread(() -> {
                        badgePerfilEnLinea.setVisibility(View.VISIBLE);
                    });
                    Log.d(TAG, " Dueño conectado: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando user_connected", e);
            }
        });

        // Listen for when dueño disconnects
        socketManager.on("user_disconnected", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(duenoId)) {
                    runOnUiThread(() -> {
                        badgePerfilEnLinea.setVisibility(View.GONE);
                    });
                    Log.d(TAG, " Dueño desconectado: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando user_disconnected", e);
            }
        });

        // Listen for online users query response
        socketManager.on("online_users_response", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                org.json.JSONArray onlineUsers = data.getJSONArray("online");

                boolean isOnline = false;
                for (int i = 0; i < onlineUsers.length(); i++) {
                    JSONObject user = onlineUsers.getJSONObject(i);
                    if (user.getString("userId").equals(duenoId)) {
                        isOnline = true;
                        break;
                    }
                }

                final boolean finalIsOnline = isOnline;
                runOnUiThread(() -> {
                    if (finalIsOnline) {
                        badgePerfilEnLinea.setVisibility(View.VISIBLE);
                    } else {
                        badgePerfilEnLinea.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error procesando online_users_response", e);
            }
        });

        // Query initial status and subscribe to presence updates
        socketManager.getOnlineUsers(new String[]{duenoId});
        socketManager.subscribePresence(new String[]{duenoId});
        Log.d(TAG, " Presencia configurada para dueño: " + duenoId);
    }

    private void cleanupPresenceListeners() {
        if (socketManager != null && duenoId != null && !duenoId.equals(currentUserId)) {
            socketManager.off("user_connected");
            socketManager.off("user_disconnected");
            socketManager.off("online_users_response");
            socketManager.unsubscribePresence(new String[]{duenoId});
            Log.d(TAG, " Limpieza de presencia para dueño: " + duenoId);
        }
    }

    private void showSkeleton() {
        if (skeletonLayout != null && scrollViewContent != null) {
            skeletonLayout.setVisibility(View.VISIBLE);
            scrollViewContent.setVisibility(View.GONE);
            isContentVisible = false;
            startShimmerAnimation();
        }
    }

    private void startShimmerAnimation() {
        if (skeletonLayout == null) return;
        android.view.animation.Animation shimmer = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shimmer_animation);
        applyShimmerToChildren(skeletonLayout, shimmer);
    }

    private void applyShimmerToChildren(View view, android.view.animation.Animation animation) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof android.view.ViewGroup) {
                    applyShimmerToChildren(child, animation);
                } else if (child.getBackground() != null) {
                    child.startAnimation(animation);
                }
            }
        }
    }

    private void showContent() {
        if (!isContentVisible) {
            isContentVisible = true;
            if (skeletonLayout != null) {
                skeletonLayout.clearAnimation();
                skeletonLayout.setVisibility(View.GONE);
            }
            if (scrollViewContent != null) {
                scrollViewContent.setVisibility(View.VISIBLE);
            }
        }

        // Actualizar botones de acción según el tipo de perfil (siempre)
        if (isOwnProfile) {
            if (ivEditPerfil != null) ivEditPerfil.setVisibility(View.VISIBLE);
            if (btnMensaje != null) btnMensaje.setVisibility(View.GONE);
        } else {
            if (ivEditPerfil != null) ivEditPerfil.setVisibility(View.GONE);
            if (btnMensaje != null) btnMensaje.setVisibility(View.VISIBLE);
        }

        // Stop refresh indicator when content is loaded
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }
    }
    
    private void updateFcmToken() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Map<String, Object> tokenMap = new HashMap<>();
                    tokenMap.put("fcmToken", token);
                    db.collection("usuarios").document(user.getUid())
                            .update(tokenMap)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated in Firestore"));
                });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuthListener != null) {
            mAuth.addAuthStateListener(mAuthListener);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permiso de Notificaciones")
                            .setMessage("Habilita notificaciones para saber cuándo inicia tu paseo.")
                            .setPositiveButton("Aceptar", (dialog, which) -> {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                            })
                            .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                }
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
            mAuthListener = null;
        }
        detachDataListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachDataListeners();
        if (mAuthListener != null && mAuth != null) {
            mAuth.removeAuthStateListener(mAuthListener);
            mAuthListener = null;
        }
    }
}



