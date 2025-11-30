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
import com.mjc.mascota.modelo.Resena;
import com.mjc.mascota.ui.perfil.ResenaAdapter;
import com.mjc.mascotalink.security.CredentialManager;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.util.BottomNavManager;

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

    // Views
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageView ivBack, ivEditPerfil;
    private de.hdodenhof.circleimageview.CircleImageView ivAvatar;
    private ImageButton btnMensaje;
    private ImageView ivVerificadoBadge;
    private TextView tvNombre, tvRol, tvVerificado;
    private TextView tvMascotasRegistradas, tvPaseosSolicitados, tvMiembroDesdeStat;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout llAcercaDe, llResenas, ajustes_section, soporte_section;
    private TabLayout tabLayout;
    private Button btnCerrarSesion;
    private TextView btnFavoritos, btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos, btnMisPaseos;
    private TextView tvEmailDueno, tvTelefonoDueno;
    private ImageView btnCopyEmail, btnCopyTelefono;
    private View skeletonLayout;
    private NestedScrollView scrollViewContent;
    private BottomNavigationView bottomNav;

    // Mascotas
    private RecyclerView rvMascotas;
    private MascotaPerfilAdapter mascotaAdapter;
    private List<Pet> petList = new ArrayList<>();
    private Button btnVerTodasMascotas;

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

    // Listeners
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ListenerRegistration duenoListener;
    private ListenerRegistration mascotasListener;
    private ListenerRegistration metodoPagoListener;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 123;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_dueno);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();

        // Manual Toolbar Setup
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        ivBack.setOnClickListener(v -> finish());

        setupListeners();
        setupTabs();
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
        rvMascotas = findViewById(R.id.rv_mascotas);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mascotaAdapter = new MascotaPerfilAdapter(this, petList);
        rvMascotas.setAdapter(mascotaAdapter);
        btnVerTodasMascotas = findViewById(R.id.btn_ver_todas_mascotas);
        
        tvEmailDueno = findViewById(R.id.tv_email_dueno);
        tvTelefonoDueno = findViewById(R.id.tv_telefono_dueno);
        btnCopyEmail = findViewById(R.id.btn_copy_email);
        btnCopyTelefono = findViewById(R.id.btn_copy_telefono);
        
        ajustes_section = findViewById(R.id.ajustes_section);
        btnMisPaseos = findViewById(R.id.btn_mis_paseos);
        btnFavoritos = findViewById(R.id.btn_favoritos);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
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
        bottomNav = findViewById(R.id.bottom_nav);
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
        
        btnNotificaciones.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Notificaciones", Toast.LENGTH_SHORT).show());
        btnPrivacidad.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Privacidad", Toast.LENGTH_SHORT).show());
        btnCentroAyuda.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Centro de Ayuda", Toast.LENGTH_SHORT).show());
        btnTerminos.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Términos y Condiciones", Toast.LENGTH_SHORT).show());

        btnCerrarSesion.setOnClickListener(v -> {
            detachDataListeners();
            new CredentialManager(PerfilDuenoActivity.this).clearCredentials();
            try {
                EncryptedPreferencesHelper.getInstance(PerfilDuenoActivity.this).clear();
            } catch (Exception e) {
                Log.e(TAG, "btnCerrarSesion: error limpiando prefs cifradas", e);
            }
            mAuth.signOut();
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
        
        btnVerTodasMascotas.setOnClickListener(v -> Toast.makeText(this, "Mostrar lista completa de mascotas", Toast.LENGTH_SHORT).show());

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
        tabLayout.addTab(tabLayout.newTab().setText("Acerca de"), true);
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
                fetchCurrentUserRoleAndSetupUI();
                attachDataListeners();
                if (duenoId.equals(currentUserId)) {
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
        boolean isOwnProfile = duenoId != null && duenoId.equals(currentUserId);

                if (isOwnProfile) {
            toolbarTitle.setText("Perfil");
            ivEditPerfil.setVisibility(View.VISIBLE);
            btnMensaje.setVisibility(View.GONE);
            ajustes_section.setVisibility(View.VISIBLE);
            soporte_section.setVisibility(View.VISIBLE);
            btnCerrarSesion.setVisibility(View.VISIBLE);
            bottomNavRole = "Dueño";
            bottomNavSelectedItem = R.id.menu_perfil;
        } else {
            toolbarTitle.setText("Dueño");
            ivEditPerfil.setVisibility(View.GONE);
            btnMensaje.setVisibility(View.VISIBLE);
            ajustes_section.setVisibility(View.GONE);
            soporte_section.setVisibility(View.GONE);
            btnCerrarSesion.setVisibility(View.GONE);
            
            // Assuming viewing as Walker
            bottomNavRole = currentUserRole != null ? currentUserRole : "PASEADOR";
            bottomNavSelectedItem = R.id.menu_search;
        }
        com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = bottomNavRole != null ? bottomNavRole : "Dueño";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, bottomNavSelectedItem);
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    private void attachDataListeners() {
        detachDataListeners();
        
        // 1. Load User Basic Info & Stats
        DocumentReference userDocRef = db.collection("usuarios").document(duenoId);
        duenoListener = userDocRef.addSnapshotListener((usuarioDoc, e) -> {
            if (e != null) return;
            if (usuarioDoc != null && usuarioDoc.exists()) {
                tvNombre.setText(usuarioDoc.getString("nombre_display"));
                if (!isDestroyed() && !isFinishing()) {
                    Glide.with(this).load(usuarioDoc.getString("foto_perfil")).placeholder(R.drawable.ic_user_placeholder).into(ivAvatar);
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
        db.collection("duenos").document(duenoId).addSnapshotListener((duenoDoc, e) -> {
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
        
        cargarMascotas();
        if (duenoId.equals(currentUserId)) {
            cargarMetodoPagoPredeterminado(duenoId);
        }
    }
    
    private void cargarMascotas() {
        if (mascotasListener != null) mascotasListener.remove();
        mascotasListener = db.collection("duenos").document(duenoId).collection("mascotas")
                .addSnapshotListener((value, e) -> {
                    if (e != null) return;
                    
                    petList.clear();
                    if (value != null && !value.isEmpty()) {
                        tvMascotasRegistradas.setText(value.size() + " Mascotas");
                        for (QueryDocumentSnapshot doc : value) {
                            Pet pet = new Pet();
                            pet.setId(doc.getId());
                            pet.setName(doc.getString("nombre"));
                            pet.setBreed(doc.getString("raza"));
                            pet.setAvatarUrl(doc.getString("foto_principal_url"));
                            pet.setOwnerId(duenoId);
                            petList.add(pet);
                        }
                        btnVerTodasMascotas.setVisibility(value.size() > 3 ? View.VISIBLE : View.GONE);
                    } else {
                        tvMascotasRegistradas.setText("0 Mascotas");
                        btnVerTodasMascotas.setVisibility(View.GONE);
                    }
                    mascotaAdapter.notifyDataSetChanged();
                });
    }

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (metodoPagoListener != null) metodoPagoListener.remove();
        db.collection("usuarios").document(uid).collection("metodos_pago")
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
            List<Task<DocumentSnapshot>> userTasks = new ArrayList<>();
            
            int fetchCount = queryDocumentSnapshots.size();
            boolean hasMore = false;
            
            // Check logic for pagination button (simplified)
            if (limit == 4 && fetchCount == 4) {
                hasMore = true; 
                // Hack: if we request 4 and get 4, assume there might be more. 
                // Real implementation might request limit+1 to know for sure.
            }

            for (int i = 0; i < fetchCount; i++) {
                DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(i);
                Resena resena = new Resena();
                resena.setId(doc.getId());
                resena.setComentario(doc.getString("comentario"));
                Double calif = doc.getDouble("calificacion");
                resena.setCalificacion(calif != null ? calif.floatValue() : 0f);
                resena.setFecha(doc.getTimestamp("timestamp"));
                
                nuevasResenas.add(resena);
                
                // Author is a Paseador
                String autorId = doc.getString("paseadorId");
                if (autorId == null) autorId = doc.getString("autorId");
                
                if (autorId != null) {
                    userTasks.add(db.collection("usuarios").document(autorId).get());
                } else {
                    userTasks.add(Tasks.forResult(null));
                }
            }
            
            // Just keep the button visible if we got results, simple UX
            final boolean showButton = fetchCount >= limit;

            if (!userTasks.isEmpty()) {
                Tasks.whenAllSuccess(userTasks).addOnSuccessListener(userDocs -> {
                    for (int i = 0; i < userDocs.size(); i++) {
                        if (userDocs.get(i) instanceof DocumentSnapshot) {
                            DocumentSnapshot userDoc = (DocumentSnapshot) userDocs.get(i);
                            if (userDoc != null && userDoc.exists()) {
                                nuevasResenas.get(i).setAutorNombre(userDoc.getString("nombre_display"));
                                nuevasResenas.get(i).setAutorFotoUrl(userDoc.getString("foto_perfil"));
                            } else {
                                nuevasResenas.get(i).setAutorNombre("Paseador");
                            }
                        }
                    }
                    resenaAdapter.addResenas(nuevasResenas);
                    isLoadingResenas = false;
                    
                    if (showButton) {
                        btnVerMasResenas.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                resenaAdapter.addResenas(nuevasResenas);
                isLoadingResenas = false;
                if (showButton) {
                    btnVerMasResenas.setVisibility(View.VISIBLE);
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error cargando reseñas", e);
            isLoadingResenas = false;
        });
    }

    private void detachDataListeners() {
        if (duenoListener != null) duenoListener.remove();
        if (mascotasListener != null) mascotasListener.remove();
        if (metodoPagoListener != null) metodoPagoListener.remove();
    }

    private void showContent() {
        if (!isContentVisible) {
            isContentVisible = true;
            skeletonLayout.setVisibility(View.GONE);
            scrollViewContent.setVisibility(View.VISIBLE);
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
        mAuth.addAuthStateListener(mAuthListener);
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
        if (ivAvatar != null && !isDestroyed() && !isFinishing()) {
            Glide.with(this).clear(ivAvatar);
        }
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        detachDataListeners();
    }
}



