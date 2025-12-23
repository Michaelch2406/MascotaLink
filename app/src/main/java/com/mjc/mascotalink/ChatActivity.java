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
import com.mjc.mascota.utils.FirestoreConstants;
import com.mjc.mascotalink.modelo.Mensaje;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.ImageCompressor;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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

    private Uri currentPhotoUri;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FusedLocationProviderClient fusedLocationClient;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initializeLaunchers();
        initializeFirebase();
        initializeNetworkMonitor();

        if (!validateUser()) return;
        if (!validateChatData()) return;

        cancelNotificationIfExists();

        initViews();
        setupRecyclerView();
        setupListeners();

        setupChatConnection();
        cargarDatosOtroUsuario();
        escucharEstadoChat();
        handleRemoteInput(getIntent());
    }

    private void initializeLaunchers() {
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && currentPhotoUri != null) {
                    procesarYEnviarImagen(currentPhotoUri);
                }
            }
        );

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

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        socketManager = SocketManager.getInstance(this);
    }

    private void initializeNetworkMonitor() {
        networkMonitor = new NetworkMonitorHelper(this, socketManager, new ChatNetworkCallback());
    }

    private boolean validateUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            return true;
        }
        finish();
        return false;
    }

    private boolean validateChatData() {
        chatId = getIntent().getStringExtra("chat_id");
        otroUsuarioId = getIntent().getStringExtra("id_otro_usuario");

        if (chatId == null && otroUsuarioId != null) {
            chatId = generarChatId(currentUserId, otroUsuarioId);
            crearChatSiNoExiste(chatId, otroUsuarioId);
        } else if (chatId == null) {
            finish();
            return false;
        }
        return true;
    }

    private void cancelNotificationIfExists() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(chatId.hashCode());
        }
    }

    private void setupChatConnection() {
        if (USE_WEBSOCKET && socketManager.isConnected()) {
            setupWebSocketListeners();
        } else {
            loadInitialMessages();
        }

        networkMonitor.setCurrentRoom(chatId, NetworkMonitorHelper.RoomType.CHAT);
        networkMonitor.register();
    }

    // ==================== NETWORK CALLBACK ====================

    private class ChatNetworkCallback implements NetworkMonitorHelper.NetworkCallback {
        private com.google.android.material.snackbar.Snackbar reconnectSnackbar = null;

        @Override
        public void onNetworkLost() {
            runOnUiThread(() -> {
                updateConnectionStatus("⚠️ Sin conexión", R.color.red_error);
                showReconnectSnackbar();
                disableSendButton();
            });
        }

        @Override
        public void onNetworkAvailable() {
            runOnUiThread(() -> updateConnectionStatus("Conectando...", R.color.secondary));
        }

        @Override
        public void onReconnected() {
            runOnUiThread(() -> handleReconnection());
        }

        @Override
        public void onRetrying(int attempt, long delayMs) {
            runOnUiThread(() -> {
                String msg = String.format(java.util.Locale.US, "Reintentando conexión (%d/5)...", attempt);
                tvEstadoChat.setText(msg);

                if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                    reconnectSnackbar.setText("Reintento " + attempt + "/5 en " + (delayMs/1000) + "s...");
                }
            });
        }

        @Override
        public void onReconnectionFailed(int attempts) {
            runOnUiThread(() -> {
                updateConnectionStatus("❌ Sin conexión", R.color.red_error);
                dismissReconnectSnackbar();
                showReconnectionFailedSnackbar();
            });
        }

        @Override
        public void onNetworkTypeChanged(NetworkMonitorHelper.NetworkType type) {
            Log.d(TAG, "Tipo de red cambió a: " + type);
        }

        @Override
        public void onNetworkQualityChanged(NetworkMonitorHelper.NetworkQuality quality) {
            Log.d(TAG, "Calidad de red: " + quality);
        }

        private void updateConnectionStatus(String status, int colorRes) {
            tvEstadoChat.setText(status);
            tvEstadoChat.setTextColor(getColor(colorRes));
        }

        private void showReconnectSnackbar() {
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
        }

        private void dismissReconnectSnackbar() {
            if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                reconnectSnackbar.dismiss();
            }
        }

        private void showReconnectionFailedSnackbar() {
            dismissReconnectSnackbar();
            com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content),
                "No se pudo reconectar. Los mensajes se enviarán más tarde.",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).setAction("Reintentar", v -> {
                if (networkMonitor != null) {
                    networkMonitor.forceReconnect();
                }
            }).show();
        }

        private void handleReconnection() {
            Log.d(TAG, "✅ Reconectado al chat, re-configurando listeners");
            tvEstadoChat.setTextColor(getColor(R.color.gray_dark));
            dismissReconnectSnackbar();

            com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content),
                "✅ Conexión restaurada",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show();

            enableSendButton();

            if (chatId != null) {
                socketManager.resetUnreadCount(chatId);
            }

            if (USE_WEBSOCKET && socketManager.isConnected()) {
                setupWebSocketListeners();
            }

            loadInitialMessages();
        }

        private void disableSendButton() {
            btnEnviar.setEnabled(false);
            btnEnviar.setAlpha(0.5f);
        }

        private void enableSendButton() {
            if (!isSending) {
                btnEnviar.setEnabled(true);
                btnEnviar.setAlpha(1.0f);
            }
        }
    }

    // ==================== VIEW INITIALIZATION ====================

    private void handleRemoteInput(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence("key_text_reply");
            if (replyText != null) {
                String message = replyText.toString().trim();
                if (!message.isEmpty() && chatId != null && currentUserId != null && otroUsuarioId != null) {
                    enviarMensaje(message);
                    Toast.makeText(this, "Respuesta enviada", Toast.LENGTH_SHORT).show();
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

        setupScrollButton();
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

                if (!recyclerView.canScrollVertically(-1) && !isLoadingMore && hasMoreMessages) {
                    loadMoreMessages();
                }

                updateScrollButtonVisibility();
            }
        });
    }

    private void updateScrollButtonVisibility() {
        int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
        int totalItems = adapter.getItemCount();

        if (fabScrollDown != null) {
            if (lastVisiblePosition < totalItems - 3) {
                fabScrollDown.show();
            } else {
                fabScrollDown.hide();
            }
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnAdjuntos.setOnClickListener(v -> mostrarOpcionesAdjuntos());
        btnEnviar.setOnClickListener(v -> handleSendButtonClick());
        etMensaje.addTextChangedListener(createTypingWatcher());
    }

    private void handleSendButtonClick() {
        String texto = etMensaje.getText().toString().trim();

        if (!validateMessageBeforeSending(texto)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastSendAtMs < SEND_COOLDOWN_MS) {
            Toast.makeText(this, "Espera un momento antes de enviar otro mensaje", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isSending) {
            enviarMensaje(texto);
        }
    }

    private boolean validateMessageBeforeSending(String texto) {
        if (texto.isEmpty()) {
            Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (texto.length() > MESSAGE_MAX_LENGTH) {
            Toast.makeText(this, "Máximo " + MESSAGE_MAX_LENGTH + " caracteres", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (otroUsuarioId == null || chatId == null) {
            Toast.makeText(this, "No se pudo enviar el mensaje", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private TextWatcher createTypingWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleTypingIndicator();
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleStopTyping();
            }
        };
    }

    private void handleTypingIndicator() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTypingUpdateMs > TYPING_DEBOUNCE_MS) {
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

    private void scheduleStopTyping() {
        typingTimer = new Timer();
        typingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (USE_WEBSOCKET && socketManager.isConnected()) {
                    socketManager.sendStopTyping(chatId);
                } else {
                    actualizarEstadoEscribiendo(false);
                }
            }
        }, 2000);
    }

    // ==================== MESSAGE SENDING ====================

    private void enviarMensaje(String texto) {
        if (isSending) {
            Log.w(TAG, "Intento de envío mientras ya se está enviando un mensaje");
            return;
        }

        prepareSendingState(texto);

        if (USE_WEBSOCKET && socketManager.isConnected()) {
            sendMessageViaWebSocket(texto);
            return;
        }

        sendMessageViaFirestore(texto);
    }

    private void prepareSendingState(String texto) {
        isSending = true;
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.5f);
        lastSendAtMs = SystemClock.elapsedRealtime();
    }

    private void sendMessageViaWebSocket(String texto) {
        socketManager.sendMessage(chatId, otroUsuarioId, texto);
        etMensaje.setText("");
        resetSendButton();
        vibrarSutil();
        Log.d(TAG, "Mensaje enviado vía WebSocket");
    }

    private void sendMessageViaFirestore(String texto) {
        final String textoOriginal = texto;
        Map<String, Object> mensaje = buildMessageData(texto, FirestoreConstants.MESSAGE_TYPE_TEXTO);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .collection(FirestoreConstants.COLLECTION_MENSAJES)
                .add(mensaje)
                .addOnSuccessListener(docRef -> handleMessageSentSuccess(texto))
                .addOnFailureListener(e -> handleMessageSentFailure(e, textoOriginal));
    }

    private Map<String, Object> buildMessageData(String texto, String tipo) {
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put(FirestoreConstants.FIELD_ID_REMITENTE, currentUserId);
        mensaje.put(FirestoreConstants.FIELD_ID_DESTINATARIO, otroUsuarioId);
        mensaje.put(FirestoreConstants.FIELD_TEXTO, texto);
        mensaje.put(FirestoreConstants.FIELD_TIMESTAMP, FieldValue.serverTimestamp());
        mensaje.put(FirestoreConstants.FIELD_LEIDO, false);
        mensaje.put(FirestoreConstants.FIELD_ENTREGADO, true);
        mensaje.put(FirestoreConstants.FIELD_TIPO, tipo);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put(FirestoreConstants.FIELD_FECHA_ELIMINACION, new Timestamp(cal.getTime()));

        return mensaje;
    }

    private void handleMessageSentSuccess(String texto) {
        Log.d(TAG, "Mensaje enviado exitosamente");

        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put(FirestoreConstants.FIELD_ULTIMO_MENSAJE, texto);
        chatUpdate.put(FirestoreConstants.FIELD_ULTIMO_TIMESTAMP, FieldValue.serverTimestamp());
        chatUpdate.put(FirestoreConstants.FIELD_MENSAJES_NO_LEIDOS + "." + otroUsuarioId, FieldValue.increment(1));

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId).update(chatUpdate)
                .addOnSuccessListener(aVoid -> {
                    etMensaje.setText("");
                    resetSendButton();
                    vibrarSutil();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error actualizando chat", e);
                    etMensaje.setText("");
                    resetSendButton();
                });
    }

    private void handleMessageSentFailure(Exception e, String textoOriginal) {
        Log.e(TAG, "Error enviando mensaje", e);

        String errorMsg = determineErrorMessage(e);
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();

        if (etMensaje.getText().toString().trim().isEmpty()) {
            etMensaje.setText(textoOriginal);
            etMensaje.setSelection(textoOriginal.length());
        }

        resetSendButton();
    }

    private String determineErrorMessage(Exception e) {
        if (e.getMessage() == null) return "Error al enviar mensaje";

        if (e.getMessage().contains("PERMISSION_DENIED")) {
            return "No tienes permiso para enviar mensajes";
        } else if (e.getMessage().contains("UNAVAILABLE")) {
            return "Sin conexión. Verifica tu internet";
        } else if (e.getMessage().contains("DEADLINE_EXCEEDED")) {
            return "Tiempo de espera agotado. Inténtalo de nuevo";
        }

        return "Error al enviar mensaje";
    }

    private void resetSendButton() {
        isSending = false;
        btnEnviar.setEnabled(true);
        btnEnviar.setAlpha(1.0f);
    }

    private void actualizarMensajeEnAdapter(Mensaje mensajeActualizado) {
        adapter.updateMessage(mensajeActualizado);
    }

    // ==================== IMAGE & LOCATION HANDLING ====================

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

    private void abrirCamara() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                java.io.File photoFile = new java.io.File(
                    getCacheDir(),
                    "photo_" + System.currentTimeMillis() + ".jpg"
                );

                currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    photoFile
                );

                intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(intent);

            } catch (Exception e) {
                Log.e(TAG, "Error abriendo cámara", e);
                Toast.makeText(this, "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No hay cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void procesarYEnviarImagen(Uri imageUri) {
        Toast.makeText(this, "Procesando imagen...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
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
                imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    enviarMensajeImagen(downloadUri.toString());
                    imageFile.delete();
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error subiendo imagen", e);
                Toast.makeText(this, "Error al subir la imagen", Toast.LENGTH_SHORT).show();
                imageFile.delete();
            });
    }

    private void enviarMensajeImagen(String imageUrl) {
        Map<String, Object> mensaje = buildMessageData("Imagen", FirestoreConstants.MESSAGE_TYPE_IMAGEN);
        mensaje.put(FirestoreConstants.FIELD_IMAGEN_URL, imageUrl);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
            .collection(FirestoreConstants.COLLECTION_MENSAJES)
            .add(mensaje)
            .addOnSuccessListener(docRef -> {
                Log.d(TAG, "Mensaje de imagen enviado");
                updateChatLastMessage("📷 Imagen");
                vibrarSutil();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error enviando mensaje de imagen", e);
                Toast.makeText(this, "Error al enviar la imagen", Toast.LENGTH_SHORT).show();
            });
    }

    private void compartirUbicacion() {
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

    private void enviarMensajeUbicacion(double latitud, double longitud) {
        Map<String, Object> mensaje = buildMessageData("Ubicación compartida", FirestoreConstants.MESSAGE_TYPE_UBICACION);
        mensaje.put(FirestoreConstants.FIELD_LATITUD, latitud);
        mensaje.put(FirestoreConstants.FIELD_LONGITUD, longitud);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
            .collection(FirestoreConstants.COLLECTION_MENSAJES)
            .add(mensaje)
            .addOnSuccessListener(docRef -> {
                Log.d(TAG, "Mensaje de ubicación enviado");
                updateChatLastMessage("📍 Ubicación");
                vibrarSutil();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error enviando mensaje de ubicación", e);
                Toast.makeText(this, "Error al enviar ubicación", Toast.LENGTH_SHORT).show();
            });
    }

    private void updateChatLastMessage(String lastMessage) {
        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put(FirestoreConstants.FIELD_ULTIMO_MENSAJE, lastMessage);
        chatUpdate.put(FirestoreConstants.FIELD_ULTIMO_TIMESTAMP, FieldValue.serverTimestamp());
        chatUpdate.put(FirestoreConstants.FIELD_MENSAJES_NO_LEIDOS + "." + otroUsuarioId, FieldValue.increment(1));

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId).update(chatUpdate);
    }

    // ==================== UI HELPERS ====================

    private void setupScrollButton() {
        if (fabScrollDown != null) {
            fabScrollDown.setOnClickListener(v -> {
                if (adapter.getItemCount() > 0) {
                    rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);
                    fabScrollDown.hide();
                }
            });
            fabScrollDown.hide();
        }
    }

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

        if (FirestoreConstants.ROLE_PASEADOR.equals(userRole) ||
            FirestoreConstants.ROLE_DUENO.equals(userRole) ||
            "DUEÑO".equals(userRole)) {
            rvQuickReplies.setVisibility(View.VISIBLE);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            rvQuickReplies.setLayoutManager(layoutManager);

            quickReplyAdapter = new QuickReplyAdapter(this, message -> {
                etMensaje.setText(message);
                etMensaje.setSelection(message.length());
            }, userRole);

            rvQuickReplies.setAdapter(quickReplyAdapter);
        } else {
            rvQuickReplies.setVisibility(View.GONE);
        }
    }

    // ==================== MESSAGE LOADING ====================

    private void loadInitialMessages() {
        isLoadingMore = true;
        showLoadingOlder(true);
        messageIds.clear();

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .collection(FirestoreConstants.COLLECTION_MENSAJES)
                .orderBy(FirestoreConstants.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                .get()
                .addOnSuccessListener(snapshot -> handleInitialMessagesLoaded(snapshot))
                .addOnFailureListener(e -> handleMessagesLoadError(e));
    }

    private void handleInitialMessagesLoaded(com.google.firebase.firestore.QuerySnapshot snapshot) {
        isLoadingMore = false;
        showLoadingOlder(false);

        if (snapshot == null || snapshot.isEmpty()) {
            hasMoreMessages = false;
            return;
        }

        List<Mensaje> page = parseMessages(snapshot);
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
    }

    private List<Mensaje> parseMessages(com.google.firebase.firestore.QuerySnapshot snapshot) {
        List<Mensaje> messages = new ArrayList<>();

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Mensaje m = doc.toObject(Mensaje.class);
            if (m == null) continue;

            m.setId(doc.getId());
            messageIds.add(doc.getId());
            messages.add(m);

            if (shouldMarkAsRead(m)) {
                marcarLeido(doc.getId());
            }
        }

        return messages;
    }

    private boolean shouldMarkAsRead(Mensaje mensaje) {
        return mensaje.getIdDestinatario() != null &&
               mensaje.getIdDestinatario().equals(currentUserId) &&
               !mensaje.isLeido();
    }

    private void handleMessagesLoadError(Exception e) {
        isLoadingMore = false;
        showLoadingOlder(false);
        Log.e(TAG, "Error cargando mensajes iniciales", e);

        String errorMsg = "Error al cargar mensajes";
        if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
            errorMsg = "No tienes permiso para ver estos mensajes";
        }

        Toast.makeText(this, errorMsg + ". Toca para reintentar", Toast.LENGTH_LONG).show();

        rvMensajes.setOnClickListener(v -> {
            rvMensajes.setOnClickListener(null);
            loadInitialMessages();
        });
    }

    private void attachNewMessagesListener() {
        if (latestTimestampLoaded == null) return;
        if (newMessagesListener != null) newMessagesListener.remove();

        newMessagesListener = db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .collection(FirestoreConstants.COLLECTION_MENSAJES)
                .orderBy(FirestoreConstants.FIELD_TIMESTAMP, Query.Direction.ASCENDING)
                .startAfter(latestTimestampLoaded)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null) {
                        handleNewMessages(snapshot);
                    }
                });
    }

    private void handleNewMessages(com.google.firebase.firestore.QuerySnapshot snapshot) {
        for (DocumentChange change : snapshot.getDocumentChanges()) {
            if (change.getType() == DocumentChange.Type.ADDED) {
                handleMessageAdded(change);
            } else if (change.getType() == DocumentChange.Type.MODIFIED) {
                handleMessageModified(change);
            }
        }
    }

    private void handleMessageAdded(DocumentChange change) {
        Mensaje m = change.getDocument().toObject(Mensaje.class);
        if (m == null) return;

        m.setId(change.getDocument().getId());
        if (messageIds.contains(m.getId())) return;

        messageIds.add(m.getId());
        adapter.agregarMensaje(m);
        latestTimestampLoaded = m.getTimestamp();
        maybeScrollToBottom();

        if (shouldMarkAsRead(m)) {
            marcarLeido(change.getDocument().getId());
            marcarTodosLeidos();
        }
    }

    private void handleMessageModified(DocumentChange change) {
        String messageId = change.getDocument().getId();
        Log.d(TAG, "Mensaje modificado: " + messageId);

        Mensaje updatedMessage = change.getDocument().toObject(Mensaje.class);
        if (updatedMessage != null) {
            updatedMessage.setId(messageId);
            actualizarMensajeEnAdapter(updatedMessage);
        }
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

        Query query = db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .collection(FirestoreConstants.COLLECTION_MENSAJES)
                .orderBy(FirestoreConstants.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (oldestSnapshot != null) {
            query = query.startAfter(oldestSnapshot);
        }

        query.get()
            .addOnSuccessListener(snapshot -> handleMoreMessagesLoaded(snapshot))
            .addOnFailureListener(e -> {
                isLoadingMore = false;
                showLoadingOlder(false);
                Log.e(TAG, "Error cargando más mensajes", e);
                Toast.makeText(this, "Error al cargar mensajes antiguos", Toast.LENGTH_SHORT).show();
            });
    }

    private void handleMoreMessagesLoaded(com.google.firebase.firestore.QuerySnapshot snapshot) {
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
    }

    private void showLoadingOlder(boolean show) {
        if (progressLoadMore != null) {
            progressLoadMore.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void marcarLeido(String mensajeId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(FirestoreConstants.FIELD_LEIDO, true);
        updates.put(FirestoreConstants.FIELD_ENTREGADO, true);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .collection(FirestoreConstants.COLLECTION_MENSAJES).document(mensajeId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Mensaje marcado como leído: " + mensajeId))
                .addOnFailureListener(e -> Log.e(TAG, "Error marcando mensaje como leído", e));
    }

    private void marcarTodosLeidos() {
        if (chatId == null || currentUserId == null) {
            Log.w(TAG, "No se puede resetear contador: chatId o currentUserId es null");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(FirestoreConstants.FIELD_MENSAJES_NO_LEIDOS + "." + currentUserId, 0);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Contador de no leídos reseteado"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error reseteando contador de no leídos", e);
                    db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Contador reseteado con merge"))
                            .addOnFailureListener(e2 -> Log.e(TAG, "Error en merge también", e2));
                });
    }

    private void cargarDatosOtroUsuario() {
        if (otroUsuarioId == null) return;

        db.collection(FirestoreConstants.COLLECTION_USUARIOS).document(otroUsuarioId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String nombreDisplay = doc.getString(FirestoreConstants.FIELD_NOMBRE_DISPLAY);
                        tvNombreChat.setText(nombreDisplay != null ? nombreDisplay : "Usuario");

                        String foto = doc.getString(FirestoreConstants.FIELD_FOTO_PERFIL);
                        if (foto != null) {
                            Glide.with(this).load(MyApplication.getFixedUrl(foto))
                                .placeholder(R.drawable.ic_user_placeholder)
                                .into(ivAvatarChat);
                        }
                    }
                });
    }

    private void escucharEstadoChat() {
        if (statusUpdatesListener != null) statusUpdatesListener.remove();

        statusUpdatesListener = db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
            .addSnapshotListener((doc, e) -> {
                if (doc != null && doc.exists()) {
                    handleChatStatusUpdate(doc);
                }
            });
    }

    private void handleChatStatusUpdate(DocumentSnapshot doc) {
        String estado = doc.getString(FirestoreConstants.FIELD_ESTADO_USUARIOS + "." + otroUsuarioId);

        if (FirestoreConstants.STATUS_ESCRIBIENDO.equals(estado)) {
            tvEstadoChat.setText(FirestoreConstants.TYPING_STATUS_TEXT);
            tvEstadoChat.setTextColor(getColor(R.color.green_success));
        } else if (FirestoreConstants.STATUS_ONLINE.equals(estado)) {
            tvEstadoChat.setText(FirestoreConstants.ONLINE_STATUS_TEXT);
            tvEstadoChat.setTextColor(getColor(R.color.green_success));
        } else {
            tvEstadoChat.setText(FirestoreConstants.DEFAULT_STATUS_TEXT);
            tvEstadoChat.setTextColor(getColor(R.color.gray_text));
        }
    }

    private void actualizarEstadoEscribiendo(boolean escribiendo) {
        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .update(FirestoreConstants.FIELD_ESTADO_USUARIOS + "." + currentUserId,
                    escribiendo ? FirestoreConstants.STATUS_ESCRIBIENDO : FirestoreConstants.STATUS_ONLINE);
    }

    private String generarChatId(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "_" + u2 : u2 + "_" + u1;
    }

    private void crearChatSiNoExiste(String chatId, String otroUsuarioId) {
        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    Map<String, Object> chat = buildNewChatData(otroUsuarioId);
                    db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId).set(chat);
                }
            });
    }

    private Map<String, Object> buildNewChatData(String otroUsuarioId) {
        Map<String, Object> chat = new HashMap<>();
        chat.put(FirestoreConstants.FIELD_PARTICIPANTES, java.util.Arrays.asList(currentUserId, otroUsuarioId));
        chat.put(FirestoreConstants.FIELD_FECHA_CREACION, FieldValue.serverTimestamp());

        Map<String, Object> mensajesNoLeidos = new HashMap<>();
        mensajesNoLeidos.put(currentUserId, 0);
        mensajesNoLeidos.put(otroUsuarioId, 0);
        chat.put(FirestoreConstants.FIELD_MENSAJES_NO_LEIDOS, mensajesNoLeidos);

        Map<String, Object> estadoUsuarios = new HashMap<>();
        estadoUsuarios.put(currentUserId, FirestoreConstants.STATUS_OFFLINE);
        estadoUsuarios.put(otroUsuarioId, FirestoreConstants.STATUS_OFFLINE);
        chat.put(FirestoreConstants.FIELD_ESTADO_USUARIOS, estadoUsuarios);

        chat.put(FirestoreConstants.FIELD_ULTIMO_MENSAJE, "");
        chat.put(FirestoreConstants.FIELD_ULTIMO_TIMESTAMP, FieldValue.serverTimestamp());

        return chat;
    }

    // ==================== WEBSOCKET INTEGRATION ====================

    private void setupWebSocketListeners() {
        if (!socketManager.isConnected()) {
            Log.w(TAG, "Socket no conectado, usando Firestore fallback");
            loadInitialMessages();
            return;
        }

        socketManager.joinChat(chatId);
        loadInitialMessages();

        setupMessageListeners();
        setupPresenceListeners();
        queryInitialPresence();

        socketManager.resetUnreadCount(chatId);
        Log.d(TAG, "WebSocket listeners configurados para chat: " + chatId);
    }

    private void setupMessageListeners() {
        socketManager.on("new_message", this::handleWebSocketNewMessage);
        socketManager.on("message_read", this::handleWebSocketMessageRead);
        socketManager.on("user_typing", args -> handleWebSocketTyping(args, true));
        socketManager.on("user_stop_typing", args -> handleWebSocketTyping(args, false));
    }

    private void setupPresenceListeners() {
        socketManager.on("user_connected", this::handleWebSocketUserConnected);
        socketManager.on("user_disconnected", this::handleWebSocketUserDisconnected);
        socketManager.on("online_users_response", this::handleWebSocketOnlineUsersResponse);
    }

    private void queryInitialPresence() {
        if (otroUsuarioId != null) {
            socketManager.getOnlineUsers(new String[]{otroUsuarioId});
            socketManager.subscribePresence(new String[]{otroUsuarioId});
        }
    }

    private void handleWebSocketNewMessage(Object[] args) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            Mensaje mensaje = parseWebSocketMessage(data);

            runOnUiThread(() -> {
                if (!messageIds.contains(mensaje.getId())) {
                    messageIds.add(mensaje.getId());
                    adapter.agregarMensaje(mensaje);
                    rvMensajes.smoothScrollToPosition(adapter.getItemCount() - 1);

                    if (!mensaje.getIdRemitente().equals(currentUserId)) {
                        socketManager.markMessageRead(chatId, mensaje.getId());
                        vibrarSutil();
                    }
                }
            });

            Log.d(TAG, "Mensaje recibido vía WebSocket");
        } catch (Exception e) {
            Log.e(TAG, "Error parseando mensaje WebSocket", e);
        }
    }

    private Mensaje parseWebSocketMessage(JSONObject data) throws Exception {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(data.optString(FirestoreConstants.FIELD_ID, ""));
        mensaje.setIdRemitente(data.optString(FirestoreConstants.FIELD_ID_REMITENTE, ""));
        mensaje.setIdDestinatario(data.optString(FirestoreConstants.FIELD_ID_DESTINATARIO, ""));
        mensaje.setTexto(data.optString(FirestoreConstants.FIELD_TEXTO, ""));
        mensaje.setTipo(data.optString(FirestoreConstants.FIELD_TIPO, FirestoreConstants.MESSAGE_TYPE_TEXTO));
        mensaje.setLeido(data.optBoolean(FirestoreConstants.FIELD_LEIDO, false));
        mensaje.setEntregado(data.optBoolean(FirestoreConstants.FIELD_ENTREGADO, true));

        String timestampStr = data.optString(FirestoreConstants.FIELD_TIMESTAMP, "");
        if (!timestampStr.isEmpty()) {
            try {
                // Parse standard ISO 8601 date from JavaScript's toISOString() (always UTC)
                // Format: yyyy-MM-dd'T'HH:mm:ss.SSSZ
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(timestampStr);
                mensaje.setTimestamp(date != null ? date : new Date());
            } catch (Exception e) {
                // Fallback for formats without milliseconds or different precision
                try {
                     java.text.SimpleDateFormat sdfFallback = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
                     sdfFallback.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                     Date date = sdfFallback.parse(timestampStr);
                     mensaje.setTimestamp(date != null ? date : new Date());
                } catch (Exception ex) {
                    Log.e(TAG, "Error parsing timestamp: " + timestampStr, ex);
                    mensaje.setTimestamp(new Date());
                }
            }
        } else {
            mensaje.setTimestamp(new Date());
        }

        if (data.has(FirestoreConstants.FIELD_IMAGEN_URL)) {
            mensaje.setImagenUrl(data.optString(FirestoreConstants.FIELD_IMAGEN_URL));
        }
        if (data.has(FirestoreConstants.FIELD_LATITUD) && data.has(FirestoreConstants.FIELD_LONGITUD)) {
            mensaje.setLatitud(data.optDouble(FirestoreConstants.FIELD_LATITUD));
            mensaje.setLongitud(data.optDouble(FirestoreConstants.FIELD_LONGITUD));
        }

        return mensaje;
    }

    private void handleWebSocketMessageRead(Object[] args) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            String messageId = data.getString("messageId");

            runOnUiThread(() -> adapter.notifyDataSetChanged());
            Log.d(TAG, "Read receipt recibido para mensaje: " + messageId);
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando read receipt", e);
        }
    }

    private void handleWebSocketTyping(Object[] args, boolean isTyping) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            String userId = data.getString("userId");

            if (!userId.equals(currentUserId)) {
                runOnUiThread(() -> {
                    if (tvEstadoChat != null) {
                        if (isTyping) {
                            tvEstadoChat.setText(FirestoreConstants.TYPING_STATUS_TEXT);
                            tvEstadoChat.setVisibility(View.VISIBLE);
                        } else {
                            tvEstadoChat.setText("");
                            tvEstadoChat.setVisibility(View.GONE);
                        }
                    }
                });
            }

            Log.d(TAG, "Usuario " + (isTyping ? "escribiendo" : "dejó de escribir") + ": " + userId);
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando typing event", e);
        }
    }

    private void handleWebSocketUserConnected(Object[] args) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            String userId = data.getString("userId");

            if (userId.equals(otroUsuarioId)) {
                runOnUiThread(() -> {
                    if (tvEstadoChat != null) {
                        tvEstadoChat.setText(FirestoreConstants.ONLINE_STATUS_TEXT);
                        tvEstadoChat.setTextColor(getColor(R.color.green_success));
                        tvEstadoChat.setVisibility(View.VISIBLE);
                    }
                });
                Log.d(TAG, "👁️ Otro usuario conectado: " + userId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando user_connected", e);
        }
    }

    private void handleWebSocketUserDisconnected(Object[] args) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            String userId = data.getString("userId");

            if (userId.equals(otroUsuarioId)) {
                runOnUiThread(() -> {
                    if (tvEstadoChat != null) {
                        tvEstadoChat.setText(FirestoreConstants.DEFAULT_STATUS_TEXT);
                        tvEstadoChat.setTextColor(getColor(R.color.gray_text));
                        tvEstadoChat.setVisibility(View.VISIBLE);
                    }
                });
                Log.d(TAG, "👁️ Otro usuario desconectado: " + userId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando user_disconnected", e);
        }
    }

    private void handleWebSocketOnlineUsersResponse(Object[] args) {
        if (args.length == 0) return;

        try {
            JSONObject data = (JSONObject) args[0];
            org.json.JSONArray onlineUsers = data.getJSONArray("online");

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
                        tvEstadoChat.setText(FirestoreConstants.ONLINE_STATUS_TEXT);
                        tvEstadoChat.setTextColor(getColor(R.color.green_success));
                    } else {
                        tvEstadoChat.setText(FirestoreConstants.DEFAULT_STATUS_TEXT);
                        tvEstadoChat.setTextColor(getColor(R.color.gray_text));
                    }
                    tvEstadoChat.setVisibility(View.VISIBLE);
                }
            });

            Log.d(TAG, "📊 Estado de presencia: " + (finalIsOnline ? "online" : "offline"));
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando online_users_response", e);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onResume() {
        super.onResume();
        currentChatId = chatId;

        cancelNotificationGroup();
        actualizarEstadoEscribiendo(false);

        if (USE_WEBSOCKET && socketManager.isConnected()) {
            socketManager.joinChat(chatId);
            socketManager.resetUnreadCount(chatId);
        } else {
            attachNewMessagesListener();
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> marcarTodosLeidos(), 300);

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .update(FirestoreConstants.FIELD_CHAT_ABIERTO + "." + currentUserId, chatId);
    }

    private void cancelNotificationGroup() {
        if (chatId != null) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(chatId.hashCode());

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    boolean otherChatsExist = false;
                    for (android.service.notification.StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
                        if (sbn.getId() != 0 && "com.mjc.mascotalink.MESSAGES".equals(sbn.getNotification().getGroup())) {
                            otherChatsExist = true;
                            break;
                        }
                    }
                    if (!otherChatsExist) {
                        notificationManager.cancel(0);
                    }
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatId = null;

        if (USE_WEBSOCKET && socketManager.isConnected()) {
            socketManager.leaveChat(chatId);
            socketManager.sendStopTyping(chatId);
        }

        db.collection(FirestoreConstants.COLLECTION_CHATS).document(chatId)
                .update(FirestoreConstants.FIELD_ESTADO_USUARIOS + "." + currentUserId, FirestoreConstants.STATUS_OFFLINE,
                        FirestoreConstants.FIELD_ULTIMA_ACTIVIDAD + "." + currentUserId, FieldValue.serverTimestamp(),
                        FirestoreConstants.FIELD_CHAT_ABIERTO + "." + currentUserId, null);

        removeListeners();
    }

    private void removeListeners() {
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

        // FASE 1 - CRÍTICO: Limpiar listeners de Firestore (MEMORY LEAK FIX)
        if (newMessagesListener != null) {
            newMessagesListener.remove();
            newMessagesListener = null;
            Log.d(TAG, "✅ newMessagesListener removido");
        }

        if (statusUpdatesListener != null) {
            statusUpdatesListener.remove();
            statusUpdatesListener = null;
            Log.d(TAG, "✅ statusUpdatesListener removido");
        }

        if (networkMonitor != null) {
            networkMonitor.unregister();
        }

        if (USE_WEBSOCKET && otroUsuarioId != null) {
            socketManager.unsubscribePresence(new String[]{otroUsuarioId});
        }

        if (USE_WEBSOCKET) {
            cleanupWebSocketListeners();
        }

        Log.d(TAG, "ChatActivity destroyed, listeners limpiados");
    }

    private void cleanupWebSocketListeners() {
        socketManager.off("new_message");
        socketManager.off("user_typing");
        socketManager.off("user_stop_typing");
        socketManager.off("message_read");
        socketManager.off("user_connected");
        socketManager.off("user_disconnected");
        socketManager.off("online_users_response");
    }
}
