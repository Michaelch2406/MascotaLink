package com.mjc.mascotalink;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.mjc.mascotalink.modelo.Mensaje;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int PAGE_SIZE = 50;
    private static final int MESSAGE_MAX_LENGTH = 500;
    private static final long SEND_COOLDOWN_MS = 2000;
    private static final long TYPING_DEBOUNCE_MS = 500;

    public static String currentChatId = null;

    private FirebaseFirestore db;
    private String currentUserId;
    private String chatId;
    private String otroUsuarioId;

    private RecyclerView rvMensajes;
    private ChatAdapter adapter;
    private EditText etMensaje;
    private FloatingActionButton btnEnviar;
    private ImageView btnBack;
    private TextView tvNombreChat, tvEstadoChat;
    private CircleImageView ivAvatarChat;
    private ProgressBar progressLoadMore;

    private Timer typingTimer;
    private LinearLayoutManager layoutManager;
    private ListenerRegistration newMessagesListener;
    private DocumentSnapshot oldestSnapshot;
    private Date latestTimestampLoaded;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private boolean isSending = false;
    private long lastSendAtMs = 0L;
    private long lastTypingUpdateMs = 0L;
    private final HashSet<String> messageIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        } else {
            finish();
            return;
        }

        chatId = getIntent().getStringExtra("chat_id");
        otroUsuarioId = getIntent().getStringExtra("id_otro_usuario");

        if (chatId == null && otroUsuarioId != null) {
            chatId = generarChatId(currentUserId, otroUsuarioId);
            crearChatSiNoExiste(chatId, otroUsuarioId);
        } else if (chatId == null) {
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupListeners();
        loadInitialMessages();
        cargarDatosOtroUsuario();
        escucharEstadoChat();
    }

    private void initViews() {
        rvMensajes = findViewById(R.id.rv_mensajes);
        etMensaje = findViewById(R.id.et_mensaje);
        btnEnviar = findViewById(R.id.btn_enviar);
        btnBack = findViewById(R.id.btn_back);
        tvNombreChat = findViewById(R.id.tv_nombre_chat);
        tvEstadoChat = findViewById(R.id.tv_estado_chat);
        ivAvatarChat = findViewById(R.id.iv_avatar_chat);
        progressLoadMore = findViewById(R.id.progress_load_more);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(this, currentUserId);
        layoutManager = new LinearLayoutManager(this);
        rvMensajes.setLayoutManager(layoutManager);
        rvMensajes.setAdapter(adapter);

        rvMensajes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!recyclerView.canScrollVertically(-1) && !isLoadingMore && hasMoreMessages) {
                    loadMoreMessages();
                }
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEnviar.setOnClickListener(v -> {
            String texto = etMensaje.getText().toString().trim();
            if (texto.isEmpty()) {
                Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show();
                return;
            }
            if (texto.length() > MESSAGE_MAX_LENGTH) {
                Toast.makeText(this, "Máximo " + MESSAGE_MAX_LENGTH + " caracteres", Toast.LENGTH_SHORT).show();
                return;
            }
            if (otroUsuarioId == null || chatId == null) {
                Toast.makeText(this, "No se pudo enviar el mensaje", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastSendAtMs < SEND_COOLDOWN_MS) {
                Toast.makeText(this, "Espera un momento antes de enviar otro mensaje", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSending) return;
            enviarMensaje(texto);
        });

        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastTypingUpdateMs > TYPING_DEBOUNCE_MS) {
                    actualizarEstadoEscribiendo(true);
                    lastTypingUpdateMs = now;
                }
                if (typingTimer != null) {
                    typingTimer.cancel();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                typingTimer = new Timer();
                typingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        actualizarEstadoEscribiendo(false);
                    }
                }, 2000);
            }
        });
    }

    private void enviarMensaje(String texto) {
        isSending = true;
        btnEnviar.setEnabled(false);
        lastSendAtMs = SystemClock.elapsedRealtime();

        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("id_remitente", currentUserId);
        mensaje.put("id_destinatario", otroUsuarioId);
        mensaje.put("texto", texto);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("leido", false);
        mensaje.put("entregado", false);
        mensaje.put("tipo", "texto");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put("fecha_eliminacion", new Timestamp(cal.getTime()));

        db.collection("chats").document(chatId)
                .collection("mensajes")
                .add(mensaje)
                .addOnSuccessListener(docRef -> {
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("ultimo_mensaje", texto);
                    chatUpdate.put("ultimo_timestamp", FieldValue.serverTimestamp());
                    chatUpdate.put("mensajes_no_leidos." + otroUsuarioId, FieldValue.increment(1));

                    db.collection("chats").document(chatId).update(chatUpdate);
                    etMensaje.setText("");
                    isSending = false;
                    btnEnviar.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error enviando mensaje", Toast.LENGTH_SHORT).show();
                    isSending = false;
                    btnEnviar.setEnabled(true);
                });
    }

    private void loadInitialMessages() {
        isLoadingMore = true;
        showLoadingOlder(true);
        messageIds.clear();
        db.collection("chats").document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    isLoadingMore = false;
                    showLoadingOlder(false);
                    if (snapshot == null || snapshot.isEmpty()) {
                        hasMoreMessages = false;
                        return;
                    }

                    List<Mensaje> page = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Mensaje m = doc.toObject(Mensaje.class);
                        if (m == null) continue;
                        m.setId(doc.getId());
                        messageIds.add(doc.getId());
                        page.add(m);
                    }
                    Collections.reverse(page);
                    adapter.setMensajes(page);
                    if (!page.isEmpty()) {
                        latestTimestampLoaded = page.get(page.size() - 1).getTimestamp();
                    }
                    oldestSnapshot = snapshot.getDocuments().get(snapshot.size() - 1);
                    if (snapshot.size() < PAGE_SIZE) {
                        hasMoreMessages = false;
                    }
                    rvMensajes.scrollToPosition(adapter.getItemCount() - 1);
                    attachNewMessagesListener();
                })
                .addOnFailureListener(e -> {
                    isLoadingMore = false;
                    showLoadingOlder(false);
                    Log.e(TAG, "Error cargando mensajes iniciales", e);
                });
    }

    private void attachNewMessagesListener() {
        if (latestTimestampLoaded == null) return;
        if (newMessagesListener != null) newMessagesListener.remove();

        newMessagesListener = db.collection("chats").document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .startAfter(latestTimestampLoaded)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null) {
                        for (DocumentChange change : snapshot.getDocumentChanges()) {
                            if (change.getType() == DocumentChange.Type.ADDED) {
                                Mensaje m = change.getDocument().toObject(Mensaje.class);
                                if (m == null) continue;
                                m.setId(change.getDocument().getId());
                                if (messageIds.contains(m.getId())) continue;
                                messageIds.add(m.getId());
                                adapter.agregarMensaje(m);
                                latestTimestampLoaded = m.getTimestamp();
                                maybeScrollToBottom();
                                if (m.getId_destinatario() != null && m.getId_destinatario().equals(currentUserId) && !m.isLeido()) {
                                    marcarLeido(change.getDocument().getId());
                                }
                            }
                        }
                    }
                });
    }

    private void maybeScrollToBottom() {
        int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        if (lastVisible >= adapter.getItemCount() - 3) {
            rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) return;
        isLoadingMore = true;
        showLoadingOlder(true);

        Query query = db.collection("chats").document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);
        if (oldestSnapshot != null) {
            query = query.startAfter(oldestSnapshot);
        }

        query.get().addOnSuccessListener(snapshot -> {
            isLoadingMore = false;
            showLoadingOlder(false);
            if (snapshot == null || snapshot.isEmpty()) {
                hasMoreMessages = false;
                return;
            }

            List<Mensaje> older = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Mensaje m = doc.toObject(Mensaje.class);
                if (m == null) continue;
                m.setId(doc.getId());
                if (messageIds.contains(m.getId())) continue;
                messageIds.add(m.getId());
                older.add(m);
            }

            if (older.isEmpty()) {
                hasMoreMessages = false;
                return;
            }

            Collections.reverse(older);

            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            View firstView = layoutManager.findViewByPosition(firstVisible);
            int offset = firstView != null ? firstView.getTop() : 0;

            adapter.agregarMensajesAlInicio(older);
            layoutManager.scrollToPositionWithOffset(firstVisible + older.size(), offset);

            oldestSnapshot = snapshot.getDocuments().get(snapshot.size() - 1);
            if (snapshot.size() < PAGE_SIZE) {
                hasMoreMessages = false;
            }
        }).addOnFailureListener(e -> {
            isLoadingMore = false;
            showLoadingOlder(false);
            Log.e(TAG, "Error cargando más mensajes", e);
        });
    }

    private void showLoadingOlder(boolean show) {
        if (progressLoadMore != null) {
            progressLoadMore.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void marcarLeido(String mensajeId) {
        db.collection("chats").document(chatId).collection("mensajes").document(mensajeId)
                .update("leido", true);
        db.collection("chats").document(chatId).update("mensajes_no_leidos." + currentUserId, 0);
    }

    private void cargarDatosOtroUsuario() {
        if (otroUsuarioId == null) return;
        db.collection("usuarios").document(otroUsuarioId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        tvNombreChat.setText(doc.getString("nombre_display"));
                        String foto = doc.getString("foto_perfil");
                        if (foto != null) {
                            Glide.with(this).load(foto).placeholder(R.drawable.ic_user_placeholder).into(ivAvatarChat);
                        }
                    }
                });
    }

    private void escucharEstadoChat() {
        db.collection("chats").document(chatId).addSnapshotListener((doc, e) -> {
            if (doc != null && doc.exists()) {
                String estado = doc.getString("estado_usuarios." + otroUsuarioId);
                if ("escribiendo".equals(estado)) {
                    tvEstadoChat.setText("Escribiendo...");
                    tvEstadoChat.setTextColor(getColor(R.color.green_success));
                } else if ("online".equals(estado)) {
                    tvEstadoChat.setText("En línea");
                    tvEstadoChat.setTextColor(getColor(R.color.green_success));
                } else {
                    tvEstadoChat.setText("Desconectado");
                    tvEstadoChat.setTextColor(getColor(R.color.gray_text));
                }
            }
        });
    }

    private void actualizarEstadoEscribiendo(boolean escribiendo) {
        db.collection("chats").document(chatId)
                .update("estado_usuarios." + currentUserId, escribiendo ? "escribiendo" : "online");
    }

    private String generarChatId(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "_" + u2 : u2 + "_" + u1;
    }

    private void crearChatSiNoExiste(String chatId, String otroUsuarioId) {
        db.collection("chats").document(chatId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                Map<String, Object> chat = new HashMap<>();
                chat.put("participantes", java.util.Arrays.asList(currentUserId, otroUsuarioId));
                chat.put("fecha_creacion", FieldValue.serverTimestamp());
                Map<String, Object> mensajesNoLeidos = new HashMap<>();
                mensajesNoLeidos.put(currentUserId, 0);
                mensajesNoLeidos.put(otroUsuarioId, 0);
                chat.put("mensajes_no_leidos", mensajesNoLeidos);

                Map<String, Object> estadoUsuarios = new HashMap<>();
                estadoUsuarios.put(currentUserId, "offline");
                estadoUsuarios.put(otroUsuarioId, "offline");
                chat.put("estado_usuarios", estadoUsuarios);
                chat.put("ultimo_mensaje", "");
                chat.put("ultimo_timestamp", FieldValue.serverTimestamp());
                db.collection("chats").document(chatId).set(chat);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentChatId = chatId;
        actualizarEstadoEscribiendo(false);
        attachNewMessagesListener();
        db.collection("chats").document(chatId)
                .update("chat_abierto." + currentUserId, chatId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatId = null;
        db.collection("chats").document(chatId)
                .update("estado_usuarios." + currentUserId, "offline",
                        "ultima_actividad." + currentUserId, FieldValue.serverTimestamp(),
                        "chat_abierto." + currentUserId, null);
        if (newMessagesListener != null) {
            newMessagesListener.remove();
            newMessagesListener = null;
        }
    }
}
