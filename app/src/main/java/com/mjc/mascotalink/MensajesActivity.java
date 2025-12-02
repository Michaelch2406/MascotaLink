package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
import java.util.List;

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
    
    // Handler para actualizar timestamps periódicamente
    private Handler updateHandler;
    private Runnable updateRunnable;

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
        
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
        }
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
        
        messagesListener = db.collection("chats")
                .whereArrayContains("participantes", currentUserId)
                .orderBy("ultimo_timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

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
                        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Chat chat = doc.toObject(Chat.class);
                            chat.setChatId(doc.getId());
                            
                            // Capturar conteo de mensajes no leídos para el usuario actual
                            Object noLeidosObj = doc.get("mensajes_no_leidos");
                            if (noLeidosObj instanceof java.util.Map) {
                                java.util.Map<String, Long> noLeidosMap = (java.util.Map<String, Long>) noLeidosObj;
                                if (noLeidosMap != null && noLeidosMap.containsKey(currentUserId)) {
                                    Object val = noLeidosMap.get(currentUserId);
                                    // Firestore puede devolver Long o Integer
                                    if (val instanceof Long) {
                                        chat.setMensajesNoLeidos(((Long) val).intValue());
                                    } else if (val instanceof Integer) {
                                        chat.setMensajesNoLeidos((Integer) val);
                                    }
                                }
                            }

                            conversaciones.add(chat);

                            // Find other user ID to fetch details
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
                                tasks.add(db.collection("usuarios").document(otherId).get());
                            } else {
                                tasks.add(Tasks.forResult(null));
                            }
                        }
                        
                        // Load user details
                        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                            for (int i = 0; i < results.size(); i++) {
                                DocumentSnapshot userDoc = (DocumentSnapshot) results.get(i);
                                if (userDoc != null && userDoc.exists()) {
                                    conversaciones.get(i).setNombreOtroUsuario(userDoc.getString("nombre_display"));
                                    conversaciones.get(i).setFotoOtroUsuario(userDoc.getString("foto_perfil"));
                                    // status could be synced here if needed from 'estado_usuarios' map in chat doc
                                    // chat object already has 'estado_usuarios' map, use it:
                                    if (conversaciones.get(i).getEstado_usuarios() != null) {
                                        String otherId = userDoc.getId();
                                        conversaciones.get(i).setEstadoOtroUsuario(conversaciones.get(i).getEstado_usuarios().get(otherId));
                                    }
                                }
                            }
                            adaptador.actualizarConversaciones(conversaciones);
                            emptyView.setVisibility(View.GONE);
                            rvConversaciones.setVisibility(View.VISIBLE);
                        });

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
}
