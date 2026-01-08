package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.utils.InputUtils;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class PaseadorRegistroPaso5Activity extends AppCompatActivity {

    private static final String TAG = "PaseadorPaso5";
    private static final String PREFS = "WizardPaseador";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final String DEBOUNCE_KEY = "paso5_save";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private NestedScrollView scrollView;
    private Button btnGuardar;
    private TextView tvValidationMessages;
    private ImageView ivPagoCheck, ivDisponibilidadCheck, ivPerrosCheck, ivZonasCheck;
    private TextInputLayout tilPrecioHora;
    private TextInputEditText etPrecioHora;
    private View layoutVideoEmpty;

    private android.text.TextWatcher precioTextWatcher;

    private final ActivityResultLauncher<Intent> videoLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), this::handleVideoResult
    );

    private final ActivityResultLauncher<Intent> videoPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), this::handleVideoResult // Reutilizamos el mismo handler
    );

    private final ActivityResultLauncher<Intent> activityLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            // Refresh the validation when returning from any child activity
            verificarCompletitudTotal();
        }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso5);

        setupFirebase();
        initViews();
        setupListeners();
        loadState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarCompletitudTotal();
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        scrollView = findViewById(R.id.scroll_view);
        btnGuardar = findViewById(R.id.btn_guardar_continuar);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        ivPagoCheck = findViewById(R.id.iv_pago_check);
        ivDisponibilidadCheck = findViewById(R.id.iv_disponibilidad_check);
        ivPerrosCheck = findViewById(R.id.iv_perros_check);
        ivZonasCheck = findViewById(R.id.iv_zonas_check);
        tilPrecioHora = findViewById(R.id.til_precio_hora);
        etPrecioHora = findViewById(R.id.et_precio_hora);
        layoutVideoEmpty = findViewById(R.id.layout_video_empty);
    }

    private void setupListeners() {
        Button btnMetodoPago = findViewById(R.id.btn_metodo_pago);
        if (btnMetodoPago != null) {
            btnMetodoPago.setOnClickListener(v -> 
                activityLauncher.launch(new Intent(this, MetodoPagoActivity.class).putExtra("prefs", PREFS)));
        }
        
        Button btnGrabarVideo = findViewById(R.id.btn_grabar_video);
        if (btnGrabarVideo != null) {
            btnGrabarVideo.setOnClickListener(v -> grabarVideoPresentacion());
        }

        Button btnSubirVideo = findViewById(R.id.btn_subir_video);
        if (btnSubirVideo != null) {
            btnSubirVideo.setOnClickListener(v -> subirVideoPresentacion());
        }
        
        View btnEliminarVideo = findViewById(R.id.btn_eliminar_video);
        if (btnEliminarVideo != null) {
            btnEliminarVideo.setOnClickListener(v -> eliminarVideo());
        }
        
        View rowDisponibilidad = findViewById(R.id.row_disponibilidad);
        if (rowDisponibilidad != null) {
            rowDisponibilidad.setOnClickListener(v -> 
                activityLauncher.launch(new Intent(this, DisponibilidadActivity.class)));
        }
        
        View rowTiposPerros = findViewById(R.id.row_tipos_perros);
        if (rowTiposPerros != null) {
            rowTiposPerros.setOnClickListener(v -> 
                activityLauncher.launch(new Intent(this, TiposPerrosActivity.class)));
        }

        View rowZonasServicio = findViewById(R.id.row_zonas_servicio);
        if (rowZonasServicio != null) {
            rowZonasServicio.setOnClickListener(v ->
                activityLauncher.launch(new Intent(this, ZonasServicioActivity.class)));
        }
        
        // SafeClickListener con 2 segundos para registro (operación crítica)
        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(InputUtils.createSafeClickListener(2000, v -> completarRegistro()));
        }

        // TextWatcher simplificado con debouncing usando InputUtils
        precioTextWatcher = InputUtils.createSimpleTextWatcher(text -> {
            verificarCompletitudTotal();
            InputUtils.debounce(DEBOUNCE_KEY, DEBOUNCE_DELAY_MS, this::saveState);
        });
        etPrecioHora.addTextChangedListener(precioTextWatcher);
    }

    private void grabarVideoPresentacion() {
        Intent intent = new Intent(this, VideoRecordActivity.class);
        videoLauncher.launch(intent);
    }

    private void subirVideoPresentacion() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        videoPickerLauncher.launch(intent);
    }

    private void handleVideoResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri tempUri = result.getData().getData();
            if (tempUri != null) {
                // Validar duración del video
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(this, tempUri);
                    String time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long durationMs = Long.parseLong(time);
                    if (durationMs < 30000) { // 30 segundos
                        Toast.makeText(this, "El video debe durar al menos 30 segundos", Toast.LENGTH_LONG).show();
                        return; // No guardar el video
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error al leer la duración del video", e);
                    Toast.makeText(this, "No se pudo verificar la duración del video.", Toast.LENGTH_SHORT).show();
                    return;
                } finally {
                    try {
                        retriever.release();
                    } catch (java.io.IOException e) {
                        Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
                    }
                }

                // Validar tamaño del video antes de copiar (máx 100MB para video de presentación)
                if (!InputUtils.isValidImageFile(this, tempUri, 100 * 1024 * 1024)) {
                    Toast.makeText(this, "El video es muy grande (máx 100MB)", Toast.LENGTH_LONG).show();
                    return;
                }

                Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "VIDEO_PRESENTACION_");
                if (permanentUri != null) {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    editor.putString("videoPresentacionUri", permanentUri.toString());
                    editor.apply();
                    mostrarPreviewVideo(permanentUri);
                    verificarCompletitudTotal();
                } else {
                    Toast.makeText(this, "Error al guardar el video.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void eliminarVideo() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.remove("videoPresentacionUri");
        editor.apply();
        layoutVideoEmpty.setVisibility(View.VISIBLE);
        findViewById(R.id.video_preview_container).setVisibility(View.GONE);
        verificarCompletitudTotal();
    }

    private void mostrarPreviewVideo(Uri uri) {
        FrameLayout container = findViewById(R.id.video_preview_container);
        ImageView thumbnail = findViewById(R.id.video_thumbnail);

        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            android.graphics.Bitmap bitmap = retriever.getFrameAtTime(-1, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            if (bitmap != null) {
                Glide.with(this)
                    .load(bitmap)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .into(thumbnail);
            } else {
                thumbnail.setImageResource(R.drawable.ic_launcher_background);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve video frame with MediaMetadataRetriever for URI: " + uri, e);
            thumbnail.setImageResource(R.drawable.ic_launcher_background);
        } finally {
            try {
                retriever.release();
            } catch (java.io.IOException e) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
            }
        }

        container.setVisibility(View.VISIBLE);
        layoutVideoEmpty.setVisibility(View.GONE);
    }

    private void verificarCompletitudTotal() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        List<String> faltantes = new ArrayList<>();

        if (!prefs.getBoolean("paso1_completo", false)) faltantes.add("• Faltan datos del Paso 1.");
        if (!prefs.getBoolean("paso2_completo", false)) faltantes.add("• Faltan fotos del Paso 2.");
        if (!prefs.getBoolean("paso3_completo", false)) faltantes.add("• Faltan documentos del Paso 3.");
        if (!prefs.getBoolean("paso4_completo", false)) faltantes.add("• Faltan datos del Paso 4 (Galería y Cuestionario).");

        boolean pagoOk = prefs.getBoolean("metodo_pago_completo", false);
        ivPagoCheck.setVisibility(pagoOk ? View.VISIBLE : View.GONE);
        if (!pagoOk) faltantes.add("• Falta configurar el método de pago.");

        boolean disponibilidadOk = prefs.getBoolean("disponibilidad_completa", false);
        ivDisponibilidadCheck.setVisibility(disponibilidadOk ? View.VISIBLE : View.GONE);
        if (!disponibilidadOk) faltantes.add("• Falta configurar tu disponibilidad.");

        boolean perrosOk = prefs.getBoolean("perros_completo", false);
        ivPerrosCheck.setVisibility(perrosOk ? View.VISIBLE : View.GONE);
        if (!perrosOk) faltantes.add("• Falta especificar los tipos de perros que manejas.");

        if (prefs.getString("videoPresentacionUri", null) == null) {
            faltantes.add("• Falta grabar el video de presentación.");
        }
        
        boolean zonasServicioOk = prefs.getBoolean("zonas_servicio_completo", false);
        ivZonasCheck.setVisibility(zonasServicioOk ? View.VISIBLE : View.GONE);
        if (!zonasServicioOk) {
            faltantes.add("• Falta configurar tus zonas de servicio.");
        }

        // Validar Precio por Hora
        String precioHoraStr = etPrecioHora.getText().toString().trim();
        if (precioHoraStr.isEmpty()) {
            faltantes.add("• Falta establecer el precio por hora.");
        } else {
            try {
                double precio = Double.parseDouble(precioHoraStr);
                if (precio <= 0) {
                    faltantes.add("• El precio por hora debe ser un número positivo.");
                }
            } catch (NumberFormatException e) {
                faltantes.add("• El precio por hora debe ser un número válido.");
            }
        }

        if (faltantes.isEmpty()) {
            tvValidationMessages.setVisibility(View.GONE);
            btnGuardar.setEnabled(true);
        } else {
            tvValidationMessages.setText(String.join("\n", faltantes));
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnGuardar.setEnabled(false);
        }
    }

    private void completarRegistro() {
        // Rate limiting ya integrado en SafeClickListener (2 segundos)
        // Ocultar teclado antes de procesar
        InputUtils.hideKeyboard(this);
        InputUtils.setButtonLoading(btnGuardar, true, "Registrando...");
        tvValidationMessages.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String email = prefs.getString("email", "");
        String password = prefs.getString("password", "");
        String nombre = prefs.getString("nombre", "");
        String apellido = prefs.getString("apellido", "");
        String cedula = prefs.getString("cedula", "");

        // Validación de seguridad para prevenir crashes
        if (email.isEmpty() || password.isEmpty() || nombre.isEmpty() || apellido.isEmpty() || cedula.isEmpty()) {
            mostrarError("Faltan datos críticos de los pasos anteriores. Por favor, retrocede y completa toda la información.");
            InputUtils.setButtonLoading(btnGuardar, false, "Reintentar Registro");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Usuario creado en Auth con UID: " + uid);
                    subirArchivosYGuardarDatos(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al crear usuario en Auth", e);
                    InputUtils.setButtonLoading(btnGuardar, false, "Reintentar Registro");
                    if (e.getMessage() != null && e.getMessage().contains("email address is already in use")) {
                        mostrarError("El correo electrónico ya está registrado. Por favor, usa otro o inicia sesión.");
                    } else {
                        mostrarError("Error al crear usuario: " + e.getMessage());
                    }
                });
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String type = cR.getType(uri);
        return mime.getExtensionFromMimeType(type);
    }

    private void subirArchivosYGuardarDatos(String uid) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 1. Construir la carpeta de usuario
        String nombre = prefs.getString("nombre", "").replaceAll("\\s", "");
        String apellido = prefs.getString("apellido", "").replaceAll("\\s", "");
        String cedula = prefs.getString("cedula", "");
        String userFolder = uid + "_" + cedula + "_" + nombre + "_" + apellido;

        // 2. Mapear URIs locales a sus carpetas de destino y nombres de archivo
        Map<String, Uri> filesToUpload = new HashMap<>();
        Map<String, String> fileDestinations = new HashMap<>();

        addFileToUpload(filesToUpload, fileDestinations, "foto_perfil", prefs.getString("fotoPerfilUri", null), "foto_de_perfil");
        addFileToUpload(filesToUpload, fileDestinations, "selfie", prefs.getString("selfieUri", null), "selfie");
        addFileToUpload(filesToUpload, fileDestinations, "certificado_antecedentes", prefs.getString("antecedentesUri", null), "documentos");
        addFileToUpload(filesToUpload, fileDestinations, "certificado_medico", prefs.getString("medicoUri", null), "documentos");
        addFileToUpload(filesToUpload, fileDestinations, "video_presentacion", prefs.getString("videoPresentacionUri", null), "video_presentacion");

        // Galería de paseos
        String galeriaUrisStr = prefs.getString("galeria_paseos_uris", "");
        List<Uri> galeriaUris = new ArrayList<>();
        if (!galeriaUrisStr.isEmpty()) {
            for (String s : galeriaUrisStr.split(",")) {
                galeriaUris.add(Uri.parse(s));
            }
        }

        List<Task<Uri>> uploadTasks = new ArrayList<>();
        AtomicReference<Map<String, Object>> downloadUrls = new AtomicReference<>(new HashMap<>());

        // 3. Subir archivos individuales
        for (Map.Entry<String, Uri> entry : filesToUpload.entrySet()) {
            String key = entry.getKey(); // ej: "foto_perfil"
            Uri uri = entry.getValue();
            String extension = getFileExtension(uri);
            String destinationFolder = fileDestinations.get(key);
            String fileName = key + "." + extension; // ej: "foto_perfil.jpg"
            
            StorageReference ref = storage.getReference().child(destinationFolder + "/" + userFolder + "/" + fileName);

            Log.d(TAG, "UID del usuario: " + uid);
            Log.d(TAG, "Ruta de Storage construida (PaseadorRegistroPaso5 - individual): " + ref.getPath());

            uploadTasks.add(
                ref.putFile(uri).continueWithTask(task -> {
                    if (!task.isSuccessful()) { throw task.getException(); }
                    return ref.getDownloadUrl();
                }).addOnSuccessListener(url -> {
                    downloadUrls.get().put(key + "_url", url.toString());
                })
            );
        }

        // 4. Subir galería
        List<String> galeriaUrls = new ArrayList<>();
        for (int i = 0; i < galeriaUris.size(); i++) {
            Uri uri = galeriaUris.get(i);
            String extension = getFileExtension(uri);
            String fileName = "paseo_" + (i + 1) + "." + extension;
            StorageReference ref = storage.getReference().child("galeria_paseos/" + userFolder + "/" + fileName);
            
            Log.d(TAG, "UID del usuario: " + uid);
            Log.d(TAG, "Ruta de Storage construida (PaseadorRegistroPaso5 - galeria): " + ref.getPath());

            uploadTasks.add(
                ref.putFile(uri).continueWithTask(task -> {
                    if (!task.isSuccessful()) { throw task.getException(); }
                    return ref.getDownloadUrl();
                }).addOnSuccessListener(url -> {
                    galeriaUrls.add(url.toString());
                })
            );
        }

        // 5. Esperar a que todo se complete
        Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(results -> {
            downloadUrls.get().put("galeria_paseos_urls", galeriaUrls);
            Log.d(TAG, "Todos los archivos subidos con éxito. URLs: " + downloadUrls.get());
            guardarDatosEnFirestore(uid, downloadUrls.get());
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Fallo al subir uno o más archivos", e);
            mostrarError("Error al subir archivos: " + e.getMessage());
            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().delete();
            }
            InputUtils.setButtonLoading(btnGuardar, false, "Reintentar Registro");
        });
    }

    private void addFileToUpload(Map<String, Uri> files, Map<String, String> paths, String key, String uriString, String storagePath) {
        if (uriString != null && !uriString.isEmpty()) {
            files.put(key, Uri.parse(uriString));
            paths.put(key, storagePath);
        }
    }

    private void guardarDatosEnFirestore(String uid, Map<String, Object> urls) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // --- 1. Construir el documento para la colección 'usuarios' ---
        Map<String, Object> usuarioData = new HashMap<>();
        // Capitalizar y sanitizar nombres usando InputUtils
        String nombre = InputUtils.capitalizeWords(prefs.getString("nombre", ""));
        String apellido = InputUtils.capitalizeWords(prefs.getString("apellido", ""));
        usuarioData.put("nombre", InputUtils.sanitizeInput(nombre));
        usuarioData.put("apellido", InputUtils.sanitizeInput(apellido));
        usuarioData.put("correo", InputUtils.trimSafe(prefs.getString("email", "")).toLowerCase());
        usuarioData.put("telefono", InputUtils.formatTelefonoEcuador(prefs.getString("telefono", "")));
        usuarioData.put("direccion", InputUtils.sanitizeInput(prefs.getString("domicilio", "")));
        try {
            String fechaNacStr = prefs.getString("fecha_nacimiento", "");
            if (!fechaNacStr.isEmpty()) {
                Date fechaNac = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(fechaNacStr);
                usuarioData.put("fecha_nacimiento", new Timestamp(fechaNac));
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error al parsear fecha de nacimiento", e);
        }
        usuarioData.put("foto_perfil", urls.get("foto_perfil_url"));
        usuarioData.put("selfie_url", urls.get("selfie_url"));
        usuarioData.put("rol", "PASEADOR");
        usuarioData.put("activo", true);
        usuarioData.put("fecha_registro", FieldValue.serverTimestamp());
        usuarioData.put("perfil_ref", db.collection("paseadores").document(uid));
        String nombreDisplay = prefs.getString("nombre", "") + " " + prefs.getString("apellido", "");
        usuarioData.put("nombre_display", nombreDisplay);
        usuarioData.put("nombre_lowercase", nombreDisplay.toLowerCase());
        usuarioData.put("cedula", prefs.getString("cedula", ""));

        // --- 2. Construir el documento para la colección 'paseadores' ---
        Map<String, Object> paseadorData = new HashMap<>();
        paseadorData.put("domicilio", prefs.getString("domicilio", ""));
        paseadorData.put("acepto_terminos", prefs.getBoolean("acepto_terminos", false));
        paseadorData.put("fecha_aceptacion_terminos", new Timestamp(new Date(prefs.getLong("fecha_aceptacion_terminos", System.currentTimeMillis()))));
        paseadorData.put("verificacion_estado", "PENDIENTE");
        paseadorData.put("verificacion_fecha", FieldValue.serverTimestamp());
        paseadorData.put("ultima_actualizacion", FieldValue.serverTimestamp());
        paseadorData.put("calificacion_promedio", 0.0);
        paseadorData.put("num_servicios_completados", 0);

        // Sub-mapa: documentos
        Map<String, Object> documentos = new HashMap<>();
        documentos.put("certificado_antecedentes_url", urls.get("certificado_antecedentes_url"));
        documentos.put("certificado_medico_url", urls.get("certificado_medico_url"));
        paseadorData.put("documentos", documentos);

        // Sub-mapa: conocimientos (del quiz)
        Map<String, Object> conocimientos = new HashMap<>();
        conocimientos.put("comportamiento_canino_score", prefs.getInt("score_comportamiento_canino", 0));
        conocimientos.put("primeros_auxilios_score", prefs.getInt("score_primeros_auxilios", 0));
        conocimientos.put("manejo_emergencia_score", prefs.getInt("score_manejo_emergencia", 0));
        paseadorData.put("conocimientos", conocimientos);
        
        paseadorData.put("quiz_completado", prefs.getBoolean("quiz_completado", false));
        paseadorData.put("quiz_aprobado", prefs.getBoolean("quiz_aprobado", false));
        paseadorData.put("quiz_score_total", prefs.getInt("quiz_score_total", 0));
        paseadorData.put("quiz_intentos", prefs.getInt("quiz_intentos", 0));
        paseadorData.put("quiz_fecha", new Timestamp(new Date(prefs.getLong("quiz_fecha", System.currentTimeMillis()))));

        // Sub-mapa: perfil_profesional
        Map<String, Object> perfilProfesional = new HashMap<>();
        // Guardar años de experiencia como número en lugar de texto
        perfilProfesional.put("anos_experiencia", prefs.getInt("anos_experiencia", 0));
        perfilProfesional.put("motivacion", InputUtils.sanitizeInput(prefs.getString("motivacion", "")));
        perfilProfesional.put("video_presentacion_url", urls.get("video_presentacion_url"));
        perfilProfesional.put("galeria_paseos_urls", urls.get("galeria_paseos_urls"));
        paseadorData.put("perfil_profesional", perfilProfesional);

        // --- FIX INICIO: Añadir la lógica faltante para guardar los tipos de perro ---
        // RIESGO: Este dato no se estaba guardando, por lo que el perfil del paseador
        // en la base de datos estaba incompleto.
        // SOLUCIÓN: Se leen las preferencias guardadas desde TiposPerrosActivity,
        // se reconstruye la lista de tamaños y se añade al mapa 'manejo_perros'
        // con la estructura que la Cloud Function espera.
        Map<String, Object> manejoPerros = new HashMap<>();
        List<String> tamanosAceptados = new ArrayList<>();
        if (prefs.getBoolean("perros_pequeno", false)) tamanosAceptados.add("Pequeño");
        if (prefs.getBoolean("perros_mediano", false)) tamanosAceptados.add("Mediano");
        if (prefs.getBoolean("perros_grande", false)) tamanosAceptados.add("Grande");
        manejoPerros.put("tamanos", tamanosAceptados);
        // Aquí también se podrían añadir los temperamentos si fueran necesarios en el futuro
        paseadorData.put("manejo_perros", manejoPerros);
        // --- FIX FIN ---

        // Precio por hora
        String precioHoraStr = prefs.getString("precio_hora", "0");
        try {
            double precioHora = Double.parseDouble(precioHoraStr);
            paseadorData.put("precio_hora", precioHora);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error al parsear precio_hora, guardando 0 por defecto", e);
            paseadorData.put("precio_hora", 0.0);
        }

        // --- 3. Ejecutar la escritura en lote (atomic) ---
        db.runBatch(batch -> {
            batch.set(db.collection("usuarios").document(uid), usuarioData);
            batch.set(db.collection("paseadores").document(uid), paseadorData);
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "¡Registro completado y datos guardados en Firestore!");
            SharedPreferences finalPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            guardarMetodoDePago(uid, finalPrefs);
            guardarDisponibilidad(uid, finalPrefs);
            guardarZonasDeServicio(uid, finalPrefs);
            limpiarDatosTemporales();
            mostrarMensajeFinalRegistro();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al guardar datos en Firestore", e);
            mostrarError("Error final al guardar tu perfil: " + e.getMessage());
            // Limpieza crítica: si Firestore falla, eliminar usuario de Auth y archivos de Storage
            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().delete();
            }
            // (Opcional) Aquí iría la lógica para eliminar los archivos ya subidos de Storage
            InputUtils.setButtonLoading(btnGuardar, false, "Reintentar Registro");
        });
    }


    private void mostrarError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle(" Error")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString("precio_hora", etPrecioHora.getText().toString());
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String videoUriString = prefs.getString("videoPresentacionUri", null);
        if (videoUriString != null) {
            mostrarPreviewVideo(Uri.parse(videoUriString));
        }
        etPrecioHora.setText(prefs.getString("precio_hora", ""));
    }

    // Removed loadMapState as ZonasServicioActivity handles it

    private void limpiarDatosTemporales() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    private void mostrarMensajeFinalRegistro() {
        new AlertDialog.Builder(this)
                .setTitle("¡Registro Completado!")
                .setMessage("Tu perfil ha sido enviado para revisión. Te notificaremos cuando sea aprobado.")
                .setPositiveButton("Finalizar", (dialog, which) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void guardarMetodoDePago(String uid, SharedPreferences prefs) {
        boolean metodoPagoCompleto = prefs.getBoolean("metodo_pago_completo", false);
        if (!metodoPagoCompleto) {
            return; // No hay método de pago para guardar
        }

        String banco = prefs.getString("pago_banco", "");
        String cuenta = prefs.getString("pago_cuenta", "");

        if (banco.isEmpty() || cuenta.isEmpty()) {
            Log.w(TAG, "metodo_pago_completo era true, pero los datos del banco/cuenta están vacíos.");
            return;
        }

        Map<String, Object> metodoPagoData = new HashMap<>();
        metodoPagoData.put("banco", banco);
        metodoPagoData.put("numero_cuenta", cuenta);
        metodoPagoData.put("predeterminado", true); // El primer método siempre es el predeterminado
        metodoPagoData.put("fecha_registro", FieldValue.serverTimestamp());

        db.collection("usuarios").document(uid).collection("metodos_pago")
                .add(metodoPagoData)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Método de pago guardado con ID: " + documentReference.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Error al guardar método de pago", e));
    }

    private void guardarDisponibilidad(String uid, SharedPreferences prefs) {
        if (!prefs.getBoolean("disponibilidad_completa", false)) {
            return;
        }

        // Inicializar EncryptedPreferences
        android.content.SharedPreferences encryptedPrefs = null;
        try {
            encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                "WizardPaseador",
                androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
                this,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error al crear EncryptedPreferences en guardarDisponibilidad", e);
            encryptedPrefs = prefs;
        }

        com.google.gson.Gson gson = new com.google.gson.Gson();

        // 1. Guardar horario por defecto (si existe)
        try {
            String horarioJson = encryptedPrefs.getString("disponibilidad_horario_default", null);
            if (horarioJson != null && !horarioJson.isEmpty()) {
                Map<String, Object> horarioDefault = gson.fromJson(horarioJson, Map.class);
                if (horarioDefault != null) {
                    db.collection("paseadores").document(uid)
                        .collection("disponibilidad").document("horario_default")
                        .set(horarioDefault)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Horario por defecto guardado"))
                        .addOnFailureListener(e -> Log.e(TAG, "Error al guardar horario por defecto", e));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al migrar horario por defecto", e);
        }

        // 2. Guardar bloqueos (si existen)
        try {
            String bloqueosJson = encryptedPrefs.getString("disponibilidad_bloqueos", "[]");
            java.util.List<Map<String, Object>> bloqueos = gson.fromJson(bloqueosJson, java.util.List.class);

            if (bloqueos != null && !bloqueos.isEmpty()) {
                for (Map<String, Object> bloqueo : bloqueos) {
                    try {
                        db.collection("paseadores").document(uid)
                            .collection("disponibilidad").document("bloqueos")
                            .collection("items")
                            .add(bloqueo)
                            .addOnFailureListener(e -> Log.e(TAG, "Error al guardar bloqueo", e));
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar bloqueo individual", e);
                    }
                }
                Log.d(TAG, "Bloqueos guardados: " + bloqueos.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al migrar bloqueos", e);
        }

        // 3. Guardar horarios especiales (si existen)
        try {
            String horariosJson = encryptedPrefs.getString("disponibilidad_horarios_especiales", "[]");
            java.util.List<Map<String, Object>> horarios = gson.fromJson(horariosJson, java.util.List.class);

            if (horarios != null && !horarios.isEmpty()) {
                for (Map<String, Object> horario : horarios) {
                    try {
                        db.collection("paseadores").document(uid)
                            .collection("disponibilidad").document("horarios_especiales")
                            .collection("items")
                            .add(horario)
                            .addOnFailureListener(e -> Log.e(TAG, "Error al guardar horario especial", e));
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar horario especial individual", e);
                    }
                }
                Log.d(TAG, "Horarios especiales guardados: " + horarios.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al migrar horarios especiales", e);
        }
    }

    private void guardarZonasDeServicio(String uid, SharedPreferences prefs) {
        if (!prefs.getBoolean("zonas_servicio_completo", false)) {
            return;
        }
        Set<String> zonasGuardadas = prefs.getStringSet("zonas_servicio", new HashSet<>());
        if (zonasGuardadas.isEmpty()) {
            return;
        }

        for (String zonaStr : zonasGuardadas) {
            try {
                String[] parts = zonaStr.split(",", 4);
                if (parts.length >= 4) {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    float radio = Float.parseFloat(parts[2]);
                    String direccion = parts[3];

                    Map<String, Object> zonaData = new HashMap<>();
                    zonaData.put("centro", new GeoPoint(lat, lon));
                    zonaData.put("radio_km", radio);
                    zonaData.put("direccion", direccion);

                    db.collection("paseadores").document(uid).collection("zonas_servicio")
                            .add(zonaData)
                            .addOnSuccessListener(documentReference -> Log.d(TAG, "Zona de servicio guardada con ID: " + documentReference.getId()))
                            .addOnFailureListener(e -> Log.e(TAG, "Error al guardar zona de servicio", e));
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error al parsear zona de servicio desde SharedPreferences", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (precioTextWatcher != null && etPrecioHora != null) {
            etPrecioHora.removeTextChangedListener(precioTextWatcher);
        }

        // Cancelar debounce pendiente usando InputUtils
        InputUtils.cancelDebounce(DEBOUNCE_KEY);
    }
}