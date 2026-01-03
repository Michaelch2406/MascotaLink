package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mjc.mascotalink.modelo.Chat;
import com.mjc.mascotalink.util.BottomNavManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MensajesActivity extends AppCompatActivity {

    private RecyclerView rvConversaciones;
    private MensajesAdapter adaptador;
    private LinearLayout emptyView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private BottomNavigationView bottomNav;

    private FirebaseFirestore db;
    private String currentUserId;
    private String userRole;
    private com.google.firebase.firestore.ListenerRegistration messagesListener;

    private Handler updateHandler;
    private Runnable updateRunnable;

    private Map<String, CachedUser> userCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    // Skeleton Loading
    private View skeletonLayout;
    private RecyclerView skeletonRecyclerConversaciones;
    private ConversacionSkeletonAdapter skeletonAdapter;
    private boolean isInitialLoadComplete = false;
    private long skeletonShowTime = 0;
    private static final long MIN_SKELETON_DISPLAY_TIME_MS = 800;

    private static class CachedUser {
        String nombre;
        String foto;
        long timestamp;

        CachedUser(String nombre, String foto) {
            this.nombre = nombre;
            this.foto = foto;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mensajes);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        } else {
            finish();
            return;
        }
        
        userRole = BottomNavManager.getUserRole(this);

        initViews();
        setupBottomNavigation();
        setupRecyclerView();
        cargarConversaciones();
        setupPeriodicUpdate();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
        startPeriodicUpdate();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicUpdate();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        stopPeriodicUpdate();
    }

    private void initViews() {
        // Custom Header back button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        rvConversaciones = findViewById(R.id.rv_conversaciones);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh); // Optional if wrapped
        bottomNav = findViewById(R.id.bottom_nav);

        // Skeleton views
        skeletonLayout = findViewById(R.id.skeleton_layout);
        skeletonRecyclerConversaciones = skeletonLayout.findViewById(R.id.skeleton_recycler_conversaciones);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
        }

        setupSkeletonLoader();
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = userRole != null ? userRole : "DUEÑO";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_messages);
    }

    private void setupRecyclerView() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adaptador = new MensajesAdapter(this, chat -> {
            Intent intent = new Intent(MensajesActivity.this, ChatActivity.class);
            intent.putExtra("chat_id", chat.getChatId());
            // We need to pass the OTHER user ID for ChatActivity to load header info
            // We'll find it from participants list logic in ChatActivity or pass it here if known
            // Ideally Chat object should have it separated.
            // For now, let ChatActivity figure it out or pass 'otroUsuarioId'
            // We computed it in the adapter/loading logic? 
            // Actually, let's re-find it here for safety or pass via intent if stored in Chat object
            // (We added fields to Chat model but they are transient for UI)
            
            // Simple logic: find ID that is not mine
            String otherId = null;
            if (chat.getParticipantes() != null) {
                for (String id : chat.getParticipantes()) {
                    if (!id.equals(currentUserId)) {
                        otherId = id;
                        break;
                    }
                }
            }
            intent.putExtra("id_otro_usuario", otherId);
            startActivity(intent);
        });
        rvConversaciones.setAdapter(adaptador);
    }

    private void cargarConversaciones() {
        if (messagesListener != null) {
            messagesListener.remove();
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        
        Log.d("Mensajes", "Cargando conversaciones para userId: " + currentUserId + ", role: " + userRole);

        // Paginación: Limitar a 30 conversaciones más recientes
        messagesListener = db.collection("chats")
                .whereArrayContains("participantes", currentUserId)
                .orderBy("ultimo_timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snapshot, error) -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                    // Ocultar skeleton cuando los datos lleguen
                    hideSkeleton();

                    if (error != null) {
                        // Si el usuario ya cerró sesión, ignorar el error de permisos
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

                        Log.e("Mensajes", "Error al cargar chats: " + error.getMessage());
                        Log.e("Mensajes", "Error code: " + error.getCode());
                        
                        // Mostrar mensaje de error amigable
                        String errorMsg = "No se pudieron cargar las conversaciones";
                        if (error.getMessage() != null) {
                            if (error.getMessage().contains("PERMISSION_DENIED")) {
                                errorMsg = "No tienes permiso para ver las conversaciones";
                            } else if (error.getMessage().contains("UNAVAILABLE")) {
                                errorMsg = "Sin conexión. Verifica tu internet";
                            }
                        }
                        
                        Toast.makeText(MensajesActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        
                        // Mostrar vista vacía con opción de reintentar
                        emptyView.setVisibility(View.VISIBLE);
                        rvConversaciones.setVisibility(View.GONE);
                        
                        // Agregar botón de reintentar (si existe en el layout)
                        // O permitir pull-to-refresh
                        return;
                    }

                    Log.d("Mensajes", "Query exitosa. Documentos encontrados: " + (snapshot != null ? snapshot.size() : 0));

                    if (snapshot != null && !snapshot.isEmpty()) {
                        List<Chat> conversaciones = new ArrayList<>();
                        Map<String, Integer> userIndexMap = new HashMap<>();
                        List<String> usersToFetch = new ArrayList<>();

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Chat chat = doc.toObject(Chat.class);
                            chat.setChatId(doc.getId());

                            Object noLeidosObj = doc.get("mensajes_no_leidos");
                            if (noLeidosObj instanceof java.util.Map) {
                                java.util.Map<String, Long> noLeidosMap = (java.util.Map<String, Long>) noLeidosObj;
                                if (noLeidosMap != null && noLeidosMap.containsKey(currentUserId)) {
                                    Object val = noLeidosMap.get(currentUserId);
                                    if (val instanceof Long) {
                                        chat.setMensajesNoLeidos(((Long) val).intValue());
                                    } else if (val instanceof Integer) {
                                        chat.setMensajesNoLeidos((Integer) val);
                                    }
                                }
                            }

                            conversaciones.add(chat);

                            String otherId = null;
                            if (chat.getParticipantes() != null) {
                                for (String id : chat.getParticipantes()) {
                                    if (!id.equals(currentUserId)) {
                                        otherId = id;
                                        break;
                                    }
                                }
                            }

                            if (otherId != null) {
                                CachedUser cached = userCache.get(otherId);
                                if (cached != null && !cached.isExpired()) {
                                    chat.setNombreOtroUsuario(cached.nombre);
                                    chat.setFotoOtroUsuario(cached.foto);
                                    if (chat.getEstado_usuarios() != null) {
                                        chat.setEstadoOtroUsuario(chat.getEstado_usuarios().get(otherId));
                                    }
                                } else {
                                    if (!usersToFetch.contains(otherId)) {
                                        usersToFetch.add(otherId);
                                    }
                                    userIndexMap.put(otherId, conversaciones.size() - 1);
                                }
                            }
                        }

                        if (usersToFetch.isEmpty()) {
                            adaptador.actualizarConversaciones(conversaciones);
                            emptyView.setVisibility(View.GONE);
                            rvConversaciones.setVisibility(View.VISIBLE);
                        } else {
                            List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();
                            for (String userId : usersToFetch) {
                                tasks.add(db.collection("usuarios").document(userId).get());
                            }

                            Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                                for (int i = 0; i < results.size(); i++) {
                                    DocumentSnapshot userDoc = (DocumentSnapshot) results.get(i);
                                    if (userDoc != null && userDoc.exists()) {
                                        String userId = userDoc.getId();
                                        String nombre = userDoc.getString("nombre_display");
                                        String foto = userDoc.getString("foto_perfil");

                                        userCache.put(userId, new CachedUser(nombre, foto));

                                        Integer chatIndex = userIndexMap.get(userId);
                                        if (chatIndex != null && chatIndex < conversaciones.size()) {
                                            conversaciones.get(chatIndex).setNombreOtroUsuario(nombre);
                                            conversaciones.get(chatIndex).setFotoOtroUsuario(foto);
                                            if (conversaciones.get(chatIndex).getEstado_usuarios() != null) {
                                                conversaciones.get(chatIndex).setEstadoOtroUsuario(
                                                    conversaciones.get(chatIndex).getEstado_usuarios().get(userId)
                                                );
                                            }
                                        }
                                    }
                                }
                                adaptador.actualizarConversaciones(conversaciones);
                                emptyView.setVisibility(View.GONE);
                                rvConversaciones.setVisibility(View.VISIBLE);
                            });
                        }

                    } else {
                        emptyView.setVisibility(View.VISIBLE);
                        rvConversaciones.setVisibility(View.GONE);
                    }
                });
    }
    
    /**
     * Configura la actualización periódica de timestamps.
     */
    private void setupPeriodicUpdate() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Actualizar el adaptador para refrescar los timestamps
                if (adaptador != null) {
                    adaptador.notifyDataSetChanged();
                }
                // Programar la próxima actualización en 60 segundos
                updateHandler.postDelayed(this, 60000);
            }
        };
    }
    
    /**
     * Inicia la actualización periódica.
     */
    private void startPeriodicUpdate() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.postDelayed(updateRunnable, 60000);
        }
    }
    
    /**
     * Detiene la actualización periódica.
     */
    private void stopPeriodicUpdate() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private void setupSkeletonLoader() {
        // Configurar el RecyclerView del skeleton con 6 items
        if (skeletonRecyclerConversaciones != null) {
            skeletonRecyclerConversaciones.setLayoutManager(new LinearLayoutManager(this));
            skeletonAdapter = new ConversacionSkeletonAdapter(6);
            skeletonRecyclerConversaciones.setAdapter(skeletonAdapter);
        }

        // Aplicar animación shimmer
        if (skeletonLayout != null) {
            applyShimmerAnimation(skeletonLayout);
        }

        // Registrar tiempo de inicio
        skeletonShowTime = System.currentTimeMillis();
        Log.d("Mensajes", "setupSkeletonLoader: Skeleton inicializado y visible");
    }

    private void applyShimmerAnimation(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyShimmerAnimation(viewGroup.getChildAt(i));
            }
        } else {
            if (view.getId() != View.NO_ID) {
                try {
                    String resourceName = getResources().getResourceEntryName(view.getId());
                    if (resourceName != null && resourceName.startsWith("skeleton_")) {
                        android.view.animation.Animation shimmer = android.view.animation.AnimationUtils.loadAnimation(
                            this, R.anim.shimmer_animation);
                        view.startAnimation(shimmer);
                    }
                } catch (Exception e) {
                    // Ignore if resource name cannot be retrieved
                }
            }
        }
    }

    private void hideSkeleton() {
        if (!isInitialLoadComplete && skeletonLayout != null) {
            long elapsedTime = System.currentTimeMillis() - skeletonShowTime;
            long remainingTime = MIN_SKELETON_DISPLAY_TIME_MS - elapsedTime;

            if (remainingTime > 0) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isInitialLoadComplete = true;
                    if (skeletonLayout != null) {
                        skeletonLayout.setVisibility(View.GONE);
                    }
                    Log.d("Mensajes", "hideSkeleton: Skeleton ocultado (con delay)");
                }, remainingTime);
            } else {
                isInitialLoadComplete = true;
                skeletonLayout.setVisibility(View.GONE);
                Log.d("Mensajes", "hideSkeleton: Skeleton ocultado (inmediato)");
            }
        }
    }
}
