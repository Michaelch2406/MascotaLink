package com.mjc.mascotalink;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.ImageCompressor;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.network.SocketManager;
import com.mjc.mascotalink.network.NetworkMonitorHelper;

import org.json.JSONException;
import org.json.JSONObject;

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
    private static final int REQUEST_LOCATION_PERMISSION = 100;

    // Feature flag: activar/desactivar WebSocket
    private static final boolean USE_WEBSOCKET = true;

    public static String currentChatId = null;

    private FirebaseFirestore db;
    private SocketManager socketManager;
    private NetworkMonitorHelper networkMonitor;
    private String currentUserId;
    private String chatId;
    private String otroUsuarioId;

    private RecyclerView rvMensajes;
    private RecyclerView rvQuickReplies;
    private ChatAdapter adapter;
    private QuickReplyAdapter quickReplyAdapter;
    private EditText etMensaje;
    private FloatingActionButton btnEnviar;
    private FloatingActionButton fabScrollDown;
    private ImageView btnBack;
    private ImageView btnAdjuntos;
    private TextView tvNombreChat, tvEstadoChat;
    private CircleImageView ivAvatarChat;
    private ProgressBar progressLoadMore;

    private Timer typingTimer;
    private LinearLayoutManager layoutManager;
    private ListenerRegistration newMessagesListener;
    private ListenerRegistration statusUpdatesListener;
    private DocumentSnapshot oldestSnapshot;
    private Date latestTimestampLoaded;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private boolean isSending = false;
    private long lastSendAtMs = 0L;
    private long lastTypingUpdateMs = 0L;
    private final HashSet<String> messageIds = new HashSet<>();
    
    // Para manejo de imágenes
    private Uri currentPhotoUri;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FusedLocationProviderClient fusedLocationClient;
    
    // Activity Result Launchers
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Inicializar launchers ANTES de setContentView
        initializeLaunchers();

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Inicializar SocketManager
        socketManager = SocketManager.getInstance(this);

        // Inicializar NetworkMonitorHelper para monitoreo robusto de red
        networkMonitor = new NetworkMonitorHelper(this, socketManager, new NetworkMonitorHelper.NetworkCallback() {
            private com.google.android.material.snackbar.Snackbar reconnectSnackbar = null;

            @Override
            public void onNetworkLost() {
                runOnUiThread(() -> {
                    tvEstadoChat.setText("⚠️ Sin conexión");
                    tvEstadoChat.setTextColor(getColor(R.color.red_error));

                    // Mostrar Snackbar persistente
                    if (reconnectSnackbar == null || !reconnectSnackbar.isShown()) {
                        reconnectSnackbar = com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            "Conexión perdida. Los mensajes se enviarán cuando vuelva la conexión.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
                        );
                        reconnectSnackbar.setAction("Reintentar", v -> {
                            if (networkMonitor != null) {
                                networkMonitor.forceReconnect();
                            }
                        });
                        reconnectSnackbar.show();
                    }

                    // Deshabilitar envío de mensajes hasta que reconecte
                    btnEnviar.setEnabled(false);
                    btnEnviar.setAlpha(0.5f);
                });
            }

            @Override
            public void onNetworkAvailable() {
                Log.d(TAG, "Red disponible nuevamente");
                runOnUiThread(() -> {
                    tvEstadoChat.setText("Conectando...");
                    tvEstadoChat.setTextColor(getColor(R.color.secondary));
                });
            }

            @Override
            public void onReconnected() {
                runOnUiThread(() -> {
                    Log.d(TAG, "✅ Reconectado al chat, re-configurando listeners");

                    // Restaurar estado visual
                    tvEstadoChat.setTextColor(getColor(R.color.gray_dark));

                    // Dismiss Snackbar de reconexión
                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.dismiss();
                    }

                    // Mostrar confirmación breve
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        "✅ Conexión restaurada",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show();

                    // Re-habilitar envío de mensajes
                    if (!isSending) {
                        btnEnviar.setEnabled(true);
                        btnEnviar.setAlpha(1.0f);
                    }

                    // Resetear contador de no leídos tras reconexión
                    if (chatId != null) {
                        socketManager.resetUnreadCount(chatId);
                    }

                    // Re-configurar listeners de WebSocket si está habilitado
                    if (USE_WEBSOCKET && socketManager.isConnected()) {
                        setupWebSocketListeners();
                    }

                    // Cargar mensajes nuevos que pudieron llegar durante desconexión
                    loadInitialMessages();
                });
            }

            @Override
            public void onRetrying(int attempt, long delayMs) {
                runOnUiThread(() -> {
                    String msg = String.format(java.util.Locale.US,
                        "Reintentando conexión (%d/5)...", attempt);
                    tvEstadoChat.setText(msg);

                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.setText("Reintento " + attempt + "/5 en " + (delayMs/1000) + "s...");
                    }
                });
            }

            @Override
            public void onReconnectionFailed(int attempts) {
                runOnUiThread(() -> {
                    tvEstadoChat.setText("❌ Sin conexión");
                    tvEstadoChat.setTextColor(getColor(R.color.red_error));

                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.dismiss();
                    }

                    // Snackbar con opción de reintento manual
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        "No se pudo reconectar. Los mensajes se enviarán más tarde.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Reintentar", v -> {
                        if (networkMonitor != null) {
                            networkMonitor.forceReconnect();
                        }
                    }).show();
                });
            }

            @Override
            public void onNetworkTypeChanged(NetworkMonitorHelper.NetworkType type) {
                Log.d(TAG, "Tipo de red cambió a: " + type);
            }

            @Override
            public void onNetworkQualityChanged(NetworkMonitorHelper.NetworkQuality quality) {
                Log.d(TAG, "Calidad de red: " + quality);
                // Opcional: Ajustar comportamiento según calidad (comprimir imágenes más en red lenta, etc.)
            }
        });

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

        // Cancelar notificación si se abrió desde una
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(chatId.hashCode());
        }

        initViews();
        setupRecyclerView();
        setupListeners();

        // Configurar WebSocket si está habilitado
        if (USE_WEBSOCKET && socketManager.isConnected()) {
            setupWebSocketListeners();
        } else {
            // Fallback a Firestore
            loadInitialMessages();
        }

        // Configurar el room actual para reconexión automática
        networkMonitor.setCurrentRoom(chatId, NetworkMonitorHelper.RoomType.CHAT);
        networkMonitor.register();

        cargarDatosOtroUsuario();
        escucharEstadoChat();
        handleRemoteInput(getIntent());
    }

    private void handleRemoteInput(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence("key_text_reply");
            if (replyText != null) {
                String message = replyText.toString().trim();
                if (!message.isEmpty()) {
                    // Asegurar que los IDs estén listos (ya deberían estarlo por onCreate)
                    if (chatId != null && currentUserId != null && otroUsuarioId != null) {
                        enviarMensaje(message);
                        Toast.makeText(this, "Respuesta enviada", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "Datos incompletos para enviar respuesta remota");
                    }
                }
            }
        }
    }

    private void initViews() {
        rvMensajes = findViewById(R.id.rv_mensajes);
        rvQuickReplies = findViewById(R.id.rv_quick_replies);
        etMensaje = findViewById(R.id.et_mensaje);
        btnEnviar = findViewById(R.id.btn_enviar);
        fabScrollDown = findViewById(R.id.fab_scroll_down);
        btnBack = findViewById(R.id.btn_back);
        btnAdjuntos = findViewById(R.id.btn_adjuntos);
        tvNombreChat = findViewById(R.id.tv_nombre_chat);
        tvEstadoChat = findViewById(R.id.tv_estado_chat);
        ivAvatarChat = findViewById(R.id.iv_avatar_chat);
        progressLoadMore = findViewById(R.id.progress_load_more);
        
        // Configurar botón de scroll rápido
        setupScrollButton();
        
        // Configurar quick replies solo para paseadores
        setupQuickReplies();
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
                
                // Cargar más mensajes al llegar arriba
                if (!recyclerView.canScrollVertically(-1) && !isLoadingMore && hasMoreMessages) {
                    loadMoreMessages();
                }
                
                // Mostrar/ocultar botón de scroll según posición
                int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
                int totalItems = adapter.getItemCount();
                
                if (fabScrollDown != null) {
                    if (lastVisiblePosition < totalItems - 3) {
                        // No está al final, mostrar botón
                        fabScrollDown.show();
                    } else {
                        // Está al final, ocultar botón
                        fabScrollDown.hide();
                    }
                }
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnAdjuntos.setOnClickListener(v -> mostrarOpcionesAdjuntos());

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
                    // Enviar typing por WebSocket o Firestore
                    if (USE_WEBSOCKET && socketManager.isConnected()) {
                        socketManager.sendTyping(chatId);
                    } else {
                        actualizarEstadoEscribiendo(true);
                    }
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
                        // Enviar stop typing por WebSocket o Firestore
                        if (USE_WEBSOCKET && socketManager.isConnected()) {
                            socketManager.sendStopTyping(chatId);
                        } else {
                            actualizarEstadoEscribiendo(false);
                        }
                    }
                }, 2000);
            }
        });
    }

    private void enviarMensaje(String texto) {
        // Prevención de doble envío
        if (isSending) {
            Log.w(TAG, "Intento de envío mientras ya se está enviando un mensaje");
            return;
        }

        isSending = true;
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.5f); // Indicador visual
        lastSendAtMs = SystemClock.elapsedRealtime();

        // Guardar el texto por si falla el envío
        final String textoOriginal = texto;

        // Enviar por WebSocket si está disponible
        if (USE_WEBSOCKET && socketManager.isConnected()) {
            socketManager.sendMessage(chatId, otroUsuarioId, texto);
            etMensaje.setText("");
            resetSendButton();
            vibrarSutil();
            Log.d(TAG, "Mensaje enviado vía WebSocket");
            return;
        }

        // Fallback: Enviar por Firestore
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("id_remitente", currentUserId);
        mensaje.put("id_destinatario", otroUsuarioId);
        mensaje.put("texto", texto);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("leido", false);
        mensaje.put("entregado", true); // Marcar como entregado desde el inicio
        mensaje.put("tipo", "texto");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put("fecha_eliminacion", new Timestamp(cal.getTime()));

        db.collection("chats").document(chatId)
                .collection("mensajes")
                .add(mensaje)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Mensaje enviado exitosamente: " + docRef.getId());
                    
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("ultimo_mensaje", texto);
                    chatUpdate.put("ultimo_timestamp", FieldValue.serverTimestamp());
                    chatUpdate.put("mensajes_no_leidos." + otroUsuarioId, FieldValue.increment(1));

                    db.collection("chats").document(chatId).update(chatUpdate)
                            .addOnSuccessListener(aVoid -> {
                                // Solo limpiar el input si todo fue exitoso
                                etMensaje.setText("");
                                resetSendButton();
                                
                                // Feedback háptico sutil
                                vibrarSutil();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error actualizando chat", e);
                                // Aún así, el mensaje se envió, así que limpiamos
                                etMensaje.setText("");
                                resetSendButton();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error enviando mensaje", e);
                    
                    // Determinar el tipo de error y mostrar mensaje apropiado
                    String errorMsg = "Error al enviar mensaje";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("PERMISSION_DENIED")) {
                            errorMsg = "No tienes permiso para enviar mensajes";
                        } else if (e.getMessage().contains("UNAVAILABLE")) {
                            errorMsg = "Sin conexión. Verifica tu internet";
                        } else if (e.getMessage().contains("DEADLINE_EXCEEDED")) {
                            errorMsg = "Tiempo de espera agotado. Inténtalo de nuevo";
                        }
                    }
                    
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    
                    // NO limpiar el input para que el usuario pueda reintentar
                    // Restaurar el texto original si se borró
                    if (etMensaje.getText().toString().trim().isEmpty()) {
                        etMensaje.setText(textoOriginal);
                        etMensaje.setSelection(textoOriginal.length());
                    }
                    
                    resetSendButton();
                });
    }
    
    /**
     * Restaura el estado del botón de envío.
     */
    private void resetSendButton() {
        isSending = false;
        btnEnviar.setEnabled(true);
        btnEnviar.setAlpha(1.0f);
    }
    
    /**
     * Actualiza un mensaje específico en el adapter de manera eficiente.
     * Solo actualiza el item que cambió, no toda la lista.
     */
    private void actualizarMensajeEnAdapter(Mensaje mensajeActualizado) {
        // Buscar el mensaje en la lista del adapter y actualizarlo
        // Esto es más eficiente que notifyDataSetChanged()
        adapter.notifyDataSetChanged(); // Por ahora, usar esto. Idealmente implementar con DiffUtil
    }
    
    /**
     * Configura las respuestas rápidas solo para paseadores.
     */
    /**
     * Inicializa los launchers para cámara y galería.
     * DEBE llamarse ANTES de setContentView.
     */
    private void initializeLaunchers() {
        // Launcher para cámara
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    if (currentPhotoUri != null) {
                        procesarYEnviarImagen(currentPhotoUri);
                    }
                }
            }
        );
        
        // Launcher para galería
        galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        procesarYEnviarImagen(imageUri);
                    }
                }
            }
        );
    }
    
    /**
     * Muestra el bottom sheet con opciones de adjuntos.
     */
    private void mostrarOpcionesAdjuntos() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_adjuntos, null);
        
        view.findViewById(R.id.option_camera).setOnClickListener(v -> {
            bottomSheet.dismiss();
            abrirCamara();
        });
        
        view.findViewById(R.id.option_gallery).setOnClickListener(v -> {
            bottomSheet.dismiss();
            abrirGaleria();
        });
        
        view.findViewById(R.id.option_location).setOnClickListener(v -> {
            bottomSheet.dismiss();
            compartirUbicacion();
        });
        
        bottomSheet.setContentView(view);
        bottomSheet.show();
    }
    
    /**
     * Abre la cámara para tomar una foto.
     */
    private void abrirCamara() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                // Crear archivo temporal para la foto
                java.io.File photoFile = new java.io.File(
                    getCacheDir(),
                    "photo_" + System.currentTimeMillis() + ".jpg"
                );
                
                currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    photoFile
                );
                
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(intent);
                
            } catch (Exception e) {
                Log.e(TAG, "Error abriendo cámara", e);
                Toast.makeText(this, "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No hay cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Abre la galería para seleccionar una imagen.
     */
    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }
    
    /**
     * Procesa y envía una imagen.
     */
    private void procesarYEnviarImagen(Uri imageUri) {
        // Mostrar indicador de carga
        Toast.makeText(this, "Procesando imagen...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            // Comprimir imagen en background
            java.io.File compressedFile = ImageCompressor.compressImage(this, imageUri);
            
            if (compressedFile != null) {
                runOnUiThread(() -> subirImagenAStorage(compressedFile));
            } else {
                runOnUiThread(() -> 
                    Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
    
    /**
     * Sube la imagen a Firebase Storage y envía el mensaje.
     */
    private void subirImagenAStorage(java.io.File imageFile) {
        String fileName = "chat_" + System.currentTimeMillis() + ".jpg";
        StorageReference imageRef = storageRef.child("chats/" + chatId + "/images/" + fileName);
        
        Uri fileUri = Uri.fromFile(imageFile);
        
        imageRef.putFile(fileUri)
            .addOnProgressListener(taskSnapshot -> {
                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                Log.d(TAG, "Subiendo imagen: " + (int) progress + "%");
            })
            .addOnSuccessListener(taskSnapshot -> {
                // Obtener URL de descarga
                imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    enviarMensajeImagen(downloadUri.toString());
                    imageFile.delete(); // Limpiar archivo temporal
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error subiendo imagen", e);
                Toast.makeText(this, "Error al subir la imagen", Toast.LENGTH_SHORT).show();
                imageFile.delete();
            });
    }
    
    /**
     * Envía un mensaje de tipo imagen.
     */
    private void enviarMensajeImagen(String imageUrl) {
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("id_remitente", currentUserId);
        mensaje.put("id_destinatario", otroUsuarioId);
        mensaje.put("texto", "Imagen");
        mensaje.put("tipo", "imagen");
        mensaje.put("imagen_url", imageUrl);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("leido", false);
        mensaje.put("entregado", true);
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put("fecha_eliminacion", new Timestamp(cal.getTime()));
        
        db.collection("chats").document(chatId)
            .collection("mensajes")
            .add(mensaje)
            .addOnSuccessListener(docRef -> {
                Log.d(TAG, "Mensaje de imagen enviado");
                
                Map<String, Object> chatUpdate = new HashMap<>();
                chatUpdate.put("ultimo_mensaje", "📷 Imagen");
                chatUpdate.put("ultimo_timestamp", FieldValue.serverTimestamp());
                chatUpdate.put("mensajes_no_leidos." + otroUsuarioId, FieldValue.increment(1));
                
                db.collection("chats").document(chatId).update(chatUpdate);
                
                vibrarSutil();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error enviando mensaje de imagen", e);
                Toast.makeText(this, "Error al enviar la imagen", Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Comparte la ubicación actual del usuario.
     */
    private void compartirUbicacion() {
        // Verificar permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
            return;
        }
        
        Toast.makeText(this, "Obteniendo ubicación...", Toast.LENGTH_SHORT).show();
        
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (location != null) {
                    enviarMensajeUbicacion(location.getLatitude(), location.getLongitude());
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error obteniendo ubicación", e);
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Envía un mensaje de tipo ubicación.
     */
    private void enviarMensajeUbicacion(double latitud, double longitud) {
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("id_remitente", currentUserId);
        mensaje.put("id_destinatario", otroUsuarioId);
        mensaje.put("texto", "Ubicación compartida");
        mensaje.put("tipo", "ubicacion");
        mensaje.put("latitud", latitud);
        mensaje.put("longitud", longitud);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("leido", false);
        mensaje.put("entregado", true);
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put("fecha_eliminacion", new Timestamp(cal.getTime()));
        
        db.collection("chats").document(chatId)
            .collection("mensajes")
            .add(mensaje)
            .addOnSuccessListener(docRef -> {
                Log.d(TAG, "Mensaje de ubicación enviado");
                
                Map<String, Object> chatUpdate = new HashMap<>();
                chatUpdate.put("ultimo_mensaje", "📍 Ubicación");
                chatUpdate.put("ultimo_timestamp", FieldValue.serverTimestamp());
                chatUpdate.put("mensajes_no_leidos." + otroUsuarioId, FieldValue.increment(1));
                
                db.collection("chats").document(chatId).update(chatUpdate);
                
                vibrarSutil();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error enviando mensaje de ubicación", e);
                Toast.makeText(this, "Error al enviar ubicación", Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Configura el botón de scroll rápido al final.
     */
    private void setupScrollButton() {
        if (fabScrollDown != null) {
            fabScrollDown.setOnClickListener(v -> {
                if (adapter.getItemCount() > 0) {
                    rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);
                    fabScrollDown.hide();
                }
            });
            
            // Inicialmente oculto
            fabScrollDown.hide();
        }
    }
    
    /**
     * Proporciona feedback háptico sutil al enviar mensaje.
     */
    private void vibrarSutil() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al vibrar", e);
        }
    }
    
    private void setupQuickReplies() {
        String userRole = BottomNavManager.getUserRole(this);
        
        // Mostrar quick replies tanto para paseadores como para dueños
        if ("PASEADOR".equals(userRole) || "DUENO".equals(userRole) || "DUEÑO".equals(userRole)) {
            rvQuickReplies.setVisibility(View.VISIBLE);
            
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            rvQuickReplies.setLayoutManager(layoutManager);
            
            quickReplyAdapter = new QuickReplyAdapter(this, message -> {
                // Al hacer click en una respuesta rápida, insertarla en el campo de texto
                etMensaje.setText(message);
                etMensaje.setSelection(message.length());
                // Opcionalmente, enviar automáticamente después de un pequeño delay
                // para dar tiempo al usuario de editar si lo desea
                // Handler handler = new Handler(Looper.getMainLooper());
                // handler.postDelayed(() -> {
                //     if (etMensaje.getText().toString().equals(message)) {
                //         enviarMensaje(message);
                //     }
                // }, 1000);
            }, userRole);
            
            rvQuickReplies.setAdapter(quickReplyAdapter);
        } else {
            rvQuickReplies.setVisibility(View.GONE);
        }
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
                        
                        // Marcar como leído si soy el destinatario y aún no está leído
                        if (m.getId_destinatario() != null && 
                            m.getId_destinatario().equals(currentUserId) && 
                            !m.isLeido()) {
                            marcarLeido(doc.getId());
                        }
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
                    
                    // Mostrar error con opción de reintentar
                    String errorMsg = "Error al cargar mensajes";
                    if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                        errorMsg = "No tienes permiso para ver estos mensajes";
                    }
                    
                    Toast.makeText(this, errorMsg + ". Toca para reintentar", Toast.LENGTH_LONG).show();
                    
                    // Agregar listener para reintentar al tocar la pantalla
                    rvMensajes.setOnClickListener(v -> {
                        rvMensajes.setOnClickListener(null); // Remover listener
                        loadInitialMessages();
                    });
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
                                    // También resetear el contador global del chat por si se incrementó
                                    marcarTodosLeidos();
                                }
                            } else if (change.getType() == DocumentChange.Type.MODIFIED) {
                                // Actualizar estado de mensaje existente (leido/entregado)
                                String messageId = change.getDocument().getId();
                                Log.d(TAG, "Mensaje modificado: " + messageId);
                                
                                // Actualizar solo el mensaje específico en lugar de toda la lista
                                Mensaje updatedMessage = change.getDocument().toObject(Mensaje.class);
                                if (updatedMessage != null) {
                                    updatedMessage.setId(messageId);
                                    actualizarMensajeEnAdapter(updatedMessage);
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
            Toast.makeText(this, "Error al cargar mensajes antiguos", Toast.LENGTH_SHORT).show();
        });
    }

    private void showLoadingOlder(boolean show) {
        if (progressLoadMore != null) {
            progressLoadMore.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void marcarLeido(String mensajeId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("leido", true);
        updates.put("entregado", true); // También marcar como entregado
        
        db.collection("chats").document(chatId).collection("mensajes").document(mensajeId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Mensaje marcado como leído: " + mensajeId))
                .addOnFailureListener(e -> Log.e(TAG, "Error marcando mensaje como leído", e));
    }
    
    /**
     * Marca todos los mensajes del chat como leídos y resetea el contador.
     */
    private void marcarTodosLeidos() {
        if (chatId == null || currentUserId == null) {
            Log.w(TAG, "No se puede resetear contador: chatId o currentUserId es null");
            return;
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("mensajes_no_leidos." + currentUserId, 0);
        
        db.collection("chats").document(chatId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Contador de no leídos reseteado exitosamente para " + currentUserId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error reseteando contador de no leídos", e);
                    // Intentar con set merge si el update falla
                    db.collection("chats").document(chatId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Contador reseteado con merge"))
                            .addOnFailureListener(e2 -> Log.e(TAG, "Error en merge también", e2));
                });
    }

    private void cargarDatosOtroUsuario() {
        if (otroUsuarioId == null) return;
        db.collection("usuarios").document(otroUsuarioId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String nombreDisplay = doc.getString("nombre_display");
                        tvNombreChat.setText(nombreDisplay != null ? nombreDisplay : "Usuario");
                        String foto = doc.getString("foto_perfil");
                        if (foto != null) {
                            Glide.with(this).load(MyApplication.getFixedUrl(foto)).placeholder(R.drawable.ic_user_placeholder).into(ivAvatarChat);
                        }
                    }
                });
    }

    private void escucharEstadoChat() {
        if (statusUpdatesListener != null) statusUpdatesListener.remove();
        statusUpdatesListener = db.collection("chats").document(chatId).addSnapshotListener((doc, e) -> {
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

    // ========================================
    // WEBSOCKET INTEGRATION
    // ========================================

    /**
     * Configura los listeners de WebSocket para mensajes en tiempo real
     */
    private void setupWebSocketListeners() {
        if (!socketManager.isConnected()) {
            Log.w(TAG, "Socket no conectado, usando Firestore fallback");
            loadInitialMessages();
            return;
        }

        // Unirse al chat
        socketManager.joinChat(chatId);

        // Cargar mensajes iniciales desde Firestore
        loadInitialMessages();

        // Listener para nuevos mensajes
        socketManager.on("new_message", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];

                // Crear objeto Mensaje desde JSON
                Mensaje mensaje = new Mensaje();
                mensaje.setId(data.optString("id", ""));
                mensaje.setId_remitente(data.optString("id_remitente", ""));
                mensaje.setId_destinatario(data.optString("id_destinatario", ""));
                mensaje.setTexto(data.optString("texto", ""));
                mensaje.setTipo(data.optString("tipo", "texto"));
                mensaje.setLeido(data.optBoolean("leido", false));
                mensaje.setEntregado(data.optBoolean("entregado", true));

                // Parsear timestamp
                String timestampStr = data.optString("timestamp", "");
                if (!timestampStr.isEmpty()) {
                    try {
                        Date date = new java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            java.util.Locale.US
                        ).parse(timestampStr);
                        if (date != null) {
                            mensaje.setTimestamp(date);
                        }
                    } catch (Exception e) {
                        mensaje.setTimestamp(new Date());
                    }
                } else {
                    mensaje.setTimestamp(new Date());
                }

                // Imagen o ubicación
                if (data.has("imagen_url")) {
                    mensaje.setImagen_url(data.optString("imagen_url"));
                }
                if (data.has("latitud") && data.has("longitud")) {
                    mensaje.setLatitud(data.optDouble("latitud"));
                    mensaje.setLongitud(data.optDouble("longitud"));
                }

                runOnUiThread(() -> {
                    // Evitar duplicados
                    if (!messageIds.contains(mensaje.getId())) {
                        messageIds.add(mensaje.getId());
                        adapter.agregarMensaje(mensaje);
                        rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);

                        // Marcar como leído si no es mensaje propio
                        if (!mensaje.getId_remitente().equals(currentUserId)) {
                            socketManager.markMessageRead(chatId, mensaje.getId());
                            vibrarSutil();
                        }
                    }
                });

                Log.d(TAG, "Mensaje recibido vía WebSocket");
            } catch (Exception e) {
                Log.e(TAG, "Error parseando mensaje WebSocket", e);
            }
        });

        // Listener para read receipts
        socketManager.on("message_read", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                String messageId = data.getString("messageId");

                runOnUiThread(() -> {
                    // Actualizar el adapter para mostrar doble check
                    // TODO: Implementar método marcarComoLeido() en ChatAdapter si es necesario
                    adapter.notifyDataSetChanged();
                });

                Log.d(TAG, "Read receipt recibido para mensaje: " + messageId);
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando read receipt", e);
            }
        });

        // Listener para typing indicator
        socketManager.on("user_typing", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                // Solo mostrar si NO es el usuario actual
                if (!userId.equals(currentUserId)) {
                    runOnUiThread(() -> {
                        if (tvEstadoChat != null) {
                            tvEstadoChat.setText("Escribiendo...");
                            tvEstadoChat.setVisibility(View.VISIBLE);
                        }
                    });
                }

                Log.d(TAG, "Usuario escribiendo: " + userId);
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando user_typing", e);
            }
        });

        // Listener para stop typing
        socketManager.on("user_stop_typing", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (!userId.equals(currentUserId)) {
                    runOnUiThread(() -> {
                        if (tvEstadoChat != null) {
                            tvEstadoChat.setText(""); // O mostrar "Online" si está online
                            tvEstadoChat.setVisibility(View.GONE);
                        }
                    });
                }

                Log.d(TAG, "Usuario dejó de escribir: " + userId);
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando user_stop_typing", e);
            }
        });

        // ========================================
        // LISTENERS DE PRESENCIA
        // ========================================

        // Listener para cuando el otro usuario se conecta
        socketManager.on("user_connected", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(otroUsuarioId)) {
                    runOnUiThread(() -> {
                        if (tvEstadoChat != null) {
                            tvEstadoChat.setText("En línea");
                            tvEstadoChat.setTextColor(getColor(R.color.green_success));
                            tvEstadoChat.setVisibility(View.VISIBLE);
                        }
                    });
                    Log.d(TAG, "👁️ Otro usuario conectado: " + userId);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando user_connected", e);
            }
        });

        // Listener para cuando el otro usuario se desconecta
        socketManager.on("user_disconnected", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(otroUsuarioId)) {
                    runOnUiThread(() -> {
                        if (tvEstadoChat != null) {
                            tvEstadoChat.setText("Desconectado");
                            tvEstadoChat.setTextColor(getColor(R.color.gray_text));
                            tvEstadoChat.setVisibility(View.VISIBLE);
                        }
                    });
                    Log.d(TAG, "👁️ Otro usuario desconectado: " + userId);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando user_disconnected", e);
            }
        });

        // Listener para respuesta de usuarios online
        socketManager.on("online_users_response", args -> {
            if (args.length == 0) return;

            try {
                JSONObject data = (JSONObject) args[0];
                org.json.JSONArray onlineUsers = data.getJSONArray("online");

                // Verificar si el otro usuario está online
                boolean isOnline = false;
                for (int i = 0; i < onlineUsers.length(); i++) {
                    if (onlineUsers.getString(i).equals(otroUsuarioId)) {
                        isOnline = true;
                        break;
                    }
                }

                final boolean finalIsOnline = isOnline;
                runOnUiThread(() -> {
                    if (tvEstadoChat != null) {
                        if (finalIsOnline) {
                            tvEstadoChat.setText("En línea");
                            tvEstadoChat.setTextColor(getColor(R.color.green_success));
                        } else {
                            tvEstadoChat.setText("Desconectado");
                            tvEstadoChat.setTextColor(getColor(R.color.gray_text));
                        }
                        tvEstadoChat.setVisibility(View.VISIBLE);
                    }
                });

                Log.d(TAG, "📊 Estado de presencia: " + (finalIsOnline ? "online" : "offline"));
            } catch (JSONException e) {
                Log.e(TAG, "Error parseando online_users_response", e);
            }
        });

        // Consultar estado inicial del otro usuario
        if (otroUsuarioId != null) {
            socketManager.getOnlineUsers(new String[]{otroUsuarioId});
            socketManager.subscribePresence(new String[]{otroUsuarioId});
        }

        // Resetear contador de no leídos
        socketManager.resetUnreadCount(chatId);

        Log.d(TAG, "WebSocket listeners configurados para chat: " + chatId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentChatId = chatId;

        // Cancelar notificación de este chat específico al entrar
        if (chatId != null) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(chatId.hashCode());

                // Verificar si quedan otras notificaciones del grupo MESSAGES
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    boolean otherChatsExist = false;
                    for (android.service.notification.StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
                        // ID 0 es el resumen, buscamos otros hijos
                        if (sbn.getId() != 0 && "com.mjc.mascotalink.MESSAGES".equals(sbn.getNotification().getGroup())) {
                            otherChatsExist = true;
                            break;
                        }
                    }
                    if (!otherChatsExist) {
                        notificationManager.cancel(0); // Cancelar resumen
                    }
                }
            }
        }

        actualizarEstadoEscribiendo(false);

        // WebSocket: Re-unirse al chat si estaba conectado
        if (USE_WEBSOCKET && socketManager.isConnected()) {
            socketManager.joinChat(chatId);
            socketManager.resetUnreadCount(chatId);
        } else {
            attachNewMessagesListener();
        }

        // Marcar todos los mensajes como leídos y resetear contador
        // Usar un pequeño delay para asegurar que todo esté inicializado
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            marcarTodosLeidos();
        }, 300);

        db.collection("chats").document(chatId)
                .update("chat_abierto." + currentUserId, chatId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatId = null;

        // WebSocket: Salir del chat
        if (USE_WEBSOCKET && socketManager.isConnected()) {
            socketManager.leaveChat(chatId);
            socketManager.sendStopTyping(chatId);
        }

        db.collection("chats").document(chatId)
                .update("estado_usuarios." + currentUserId, "offline",
                        "ultima_actividad." + currentUserId, FieldValue.serverTimestamp(),
                        "chat_abierto." + currentUserId, null);
        if (newMessagesListener != null) {
            newMessagesListener.remove();
            newMessagesListener = null;
        }
        if (statusUpdatesListener != null) {
            statusUpdatesListener.remove();
            statusUpdatesListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Desregistrar monitor de red
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }

        // Desuscribirse de presencia
        if (USE_WEBSOCKET && otroUsuarioId != null) {
            socketManager.unsubscribePresence(new String[]{otroUsuarioId});
        }

        // Limpiar listeners de WebSocket
        if (USE_WEBSOCKET) {
            socketManager.off("new_message");
            socketManager.off("user_typing");
            socketManager.off("user_stop_typing");
            socketManager.off("message_read");
            socketManager.off("user_connected");
            socketManager.off("user_disconnected");
            socketManager.off("online_users_response");
        }

        Log.d(TAG, "ChatActivity destroyed, listeners limpiados");
    }
}
