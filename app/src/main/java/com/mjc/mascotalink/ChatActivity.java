package com.mjc.mascotalink;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mjc.mascotalink.modelo.Mensaje;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

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

    private Timer typingTimer;

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
            // Si venimos de un botón "Contactar" y no tenemos chatId, lo generamos
            chatId = generarChatId(currentUserId, otroUsuarioId);
            crearChatSiNoExiste(chatId, otroUsuarioId);
        } else if (chatId == null) {
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupListeners();
        cargarDatosOtroUsuario();
        escucharMensajes();
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
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(this, currentUserId);
        rvMensajes.setLayoutManager(new LinearLayoutManager(this));
        rvMensajes.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEnviar.setOnClickListener(v -> {
            String texto = etMensaje.getText().toString().trim();
            if (!texto.isEmpty()) {
                enviarMensaje(texto);
            }
        });

        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                actualizarEstadoEscribiendo(true);
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
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error enviando mensaje", Toast.LENGTH_SHORT).show());
    }

    private void escucharMensajes() {
        db.collection("chats").document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null) {
                        for (DocumentChange change : snapshot.getDocumentChanges()) {
                            if (change.getType() == DocumentChange.Type.ADDED) {
                                Mensaje m = change.getDocument().toObject(Mensaje.class);
                                adapter.agregarMensaje(m);
                                rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);
                                if (m.getId_destinatario().equals(currentUserId) && !m.isLeido()) {
                                    marcarLeido(change.getDocument().getId());
                                }
                            }
                        }
                    }
                });
    }
    
    private void marcarLeido(String mensajeId) {
        db.collection("chats").document(chatId).collection("mensajes").document(mensajeId)
                .update("leido", true);
        // Also reset unread count logic if needed
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
                    tvEstadoChat.setText("Desconectado"); // Or last seen logic
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
        if (u1.compareTo(u2) < 0) return u1 + "_" + u2;
        else return u2 + "_" + u1;
    }

    private void crearChatSiNoExiste(String chatId, String otroUsuarioId) {
        db.collection("chats").document(chatId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                Map<String, Object> chat = new HashMap<>();
                // Initialize structure as per requirements
                // Simplified for brevity
                chat.put("participantes", java.util.Arrays.asList(currentUserId, otroUsuarioId));
                chat.put("fecha_creacion", FieldValue.serverTimestamp());
                db.collection("chats").document(chatId).set(chat);
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        actualizarEstadoEscribiendo(false); // Set online
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        db.collection("chats").document(chatId)
                .update("estado_usuarios." + currentUserId, "offline");
    }
}
