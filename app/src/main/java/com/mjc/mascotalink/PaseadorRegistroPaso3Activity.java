package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.Task;

import android.net.Uri;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class PaseadorRegistroPaso3Activity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseFirestore db;

    private TextView tvAntecedentesNombre, tvMedicoNombre;
    private ImageView previewAntecedentes, previewMedico;
    private Button btnSubirAntecedentes, btnSubirMedico, btnEnviarVerificacion;
    private Button btnEliminarAntecedentes, btnEliminarMedico;

    private Uri antecedentesUri;
    private Uri medicoUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso3);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        String host = "192.168.0.147";
        mAuth = FirebaseAuth.getInstance();
        mAuth.useEmulator(host, 9099);
        storage = FirebaseStorage.getInstance();
        storage.useEmulator(host, 9199);
        db = FirebaseFirestore.getInstance();
        db.useEmulator(host, 8080);

        tvAntecedentesNombre = findViewById(R.id.tv_antecedentes_nombre);
        tvMedicoNombre = findViewById(R.id.tv_medico_nombre);
        previewAntecedentes = findViewById(R.id.preview_antecedentes);
        previewMedico = findViewById(R.id.preview_medico);

        btnSubirAntecedentes = findViewById(R.id.btn_subir_antecedentes);
        btnSubirMedico = findViewById(R.id.btn_subir_medico);
        btnEnviarVerificacion = findViewById(R.id.btn_enviar_verificacion);
        btnEliminarAntecedentes = findViewById(R.id.btn_eliminar_antecedentes);
        btnEliminarMedico = findViewById(R.id.btn_eliminar_medico);

        btnSubirAntecedentes.setOnClickListener(v -> subirDocumento("antecedentes"));
        btnSubirMedico.setOnClickListener(v -> subirDocumento("medico"));
        btnEnviarVerificacion.setOnClickListener(v -> enviarParaVerificacion());
        
        // Configurar botones de eliminar
        btnEliminarAntecedentes.setOnClickListener(v -> eliminarDocumento("antecedentes"));
        btnEliminarMedico.setOnClickListener(v -> eliminarDocumento("medico"));

        loadState();
        
        // Verificar estado inicial de los documentos
        verificarEstadoDocumentos();
    }

    private void subirDocumento(String tipo) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if ("antecedentes".equals(tipo)) {
            antecedentesPdfLauncher.launch(intent);
        } else {
            medicoPdfLauncher.launch(intent);
        }
    }

    private final ActivityResultLauncher<Intent> antecedentesPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri pdfUri = result.getData().getData();
                    int flags = result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (pdfUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(pdfUri, flags);
                        } catch (SecurityException ignored) {}
                        antecedentesUri = pdfUri;
                        tvAntecedentesNombre.setText(getFileNameFromUri(pdfUri));
                        saveState();
                        actualizarUIDocumentoSubido("antecedentes", pdfUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> medicoPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri pdfUri = result.getData().getData();
                    int flags = result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (pdfUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(pdfUri, flags);
                        } catch (SecurityException ignored) {}
                        medicoUri = pdfUri;
                        tvMedicoNombre.setText(getFileNameFromUri(pdfUri));
                        saveState();
                        actualizarUIDocumentoSubido("medico", pdfUri);
                    }
                }
            });

    private void subirPdfFirebase(Uri pdfUri, String tipoDocumento) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "❌ Error: Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        
        String uid = mAuth.getCurrentUser().getUid();
        String fileName = uid + "_" + tipoDocumento + ".pdf";
        StorageReference pdfRef = storage.getReference().child("documentos/" + fileName);
        
        // Mostrar mensaje de progreso
        String nombreDocumento = "antecedentes".equals(tipoDocumento) ? "certificado de antecedentes" : "certificado médico";
        Toast.makeText(this, "🔄 Subiendo " + nombreDocumento + "...", Toast.LENGTH_SHORT).show();
        
        pdfRef.putFile(pdfUri)
            .addOnSuccessListener(taskSnapshot -> {
                // Obtener URL de descarga
                pdfRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    // Guardar URL en SharedPreferences
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    if ("antecedentes".equals(tipoDocumento)) {
                        prefs.edit().putString("antecedentesUrl", uri.toString()).apply();
                        // Toast de éxito removido para evitar spam
                    } else {
                        prefs.edit().putString("medicoUrl", uri.toString()).apply();
                        // Toast de éxito removido para evitar spam
                    }
                    
                    // Actualizar UI para mostrar que el archivo fue subido
                    // Ya se actualizó la UI cuando se seleccionó el archivo
                    
                }).addOnFailureListener(e -> {
                    String errorMsg = obtenerMensajeErrorStorage(e, "obtener URL");
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
                
            })
            .addOnFailureListener(e -> {
                String errorMsg = obtenerMensajeErrorStorage(e, "subir");
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            });
    }
    
    private void actualizarUIDocumentoSubido(String tipoDocumento, Uri uri) {
        if ("antecedentes".equals(tipoDocumento)) {
            antecedentesUri = uri;
            tvAntecedentesNombre.setText("✅ Archivo subido: " + getFileName(uri));
            tvAntecedentesNombre.setTextColor(Color.parseColor("#059669"));
            // Mostrar preview si es imagen
            if (isImageFile(uri)) {
                try {
                    previewAntecedentes.setImageURI(uri);
                    findViewById(R.id.container_preview_antecedentes).setVisibility(ImageView.VISIBLE);
                } catch (SecurityException e) {
                    // Error de permisos - continuar sin preview
                    Toast.makeText(this, "⚠️ No se puede mostrar preview del documento, pero fue guardado", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            medicoUri = uri;
            tvMedicoNombre.setText("✅ Archivo subido: " + getFileName(uri));
            tvMedicoNombre.setTextColor(Color.parseColor("#059669"));
            // Mostrar preview si es imagen
            if (isImageFile(uri)) {
                try {
                    previewMedico.setImageURI(uri);
                    findViewById(R.id.container_preview_medico).setVisibility(ImageView.VISIBLE);
                } catch (SecurityException e) {
                    // Error de permisos - continuar sin preview
                    Toast.makeText(this, "⚠️ No se puede mostrar preview del documento, pero fue guardado", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private String obtenerMensajeErrorStorage(Exception exception, String accion) {
        if (exception == null) {
            return "❌ Error desconocido al " + accion + " el archivo";
        }
        
        String mensaje = exception.getMessage();
        if (mensaje == null) {
            return "❌ Error desconocido al " + accion + " el archivo";
        }
        
        if (mensaje.contains("network") || mensaje.contains("connection")) {
            return "⚠️ Error de conexión. Verifica tu internet e inténtalo nuevamente.";
        } else if (mensaje.contains("storage") || mensaje.contains("quota")) {
            return "⚠️ Error de almacenamiento. El archivo puede ser muy grande.";
        } else if (mensaje.contains("permission")) {
            return "⚠️ Error de permisos. Contacta al soporte técnico.";
        } else {
            return "❌ Error al " + accion + " archivo: " + mensaje;
        }
    }

    private void enviarParaVerificacion() {
        // Validar que ambos documentos estén seleccionados
        if (!verificarDocumentosCompletos()) {
            return;
        }

        ensureSignedIn(() -> {
            // Subir ambos PDFs y actualizar Firestore, luego navegar a Paso 4
            subirAmbosDocumentosYActualizarFirestore();
        });
    }
    
    private void guardarDatosPaso3() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Marcar paso 3 como completo
        editor.putBoolean("paso3_completo", true);
        editor.putLong("timestamp_paso3", System.currentTimeMillis());
        
        // Guardar URLs de documentos si existen
        String antecedentesUrl = prefs.getString("antecedentesUrl", null);
        String medicoUrl = prefs.getString("medicoUrl", null);
        
        if (antecedentesUrl != null) {
            editor.putString("documentos_antecedentes_url", antecedentesUrl);
        }
        if (medicoUrl != null) {
            editor.putString("documentos_medico_url", medicoUrl);
        }
        
        // Marcar todo el wizard como completo
        editor.putBoolean("wizard_completo", true);
        editor.putLong("timestamp_wizard_completo", System.currentTimeMillis());
        
        editor.apply();
        
        Log.d("Paso3", "Datos del paso 3 guardados localmente. Wizard completo.");
    }
    
    // Métodos de Firebase comentados temporalmente - se usarán cuando el proceso completo esté listo
    /*
    private void crearCuentaCompletaFirebase() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String email = prefs.getString("email", null);
        String password = prefs.getString("password", null);
        
        if (email == null || password == null) {
            Toast.makeText(this, "❌ Error: Datos de registro incompletos. Vuelve al paso 1.", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Crear cuenta de Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = task.getResult().getUser() != null ? task.getResult().getUser().getUid() : null;
                        if (uid != null) {
                            // Subir todas las imágenes y crear documentos
                            subirTodasLasImagenesYCrearDocumentos(uid);
                        } else {
                            Toast.makeText(this, "❌ Error interno: No se pudo obtener identificador de usuario", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String errorMsg = obtenerMensajeErrorFirebase(task.getException());
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private String obtenerMensajeErrorFirebase(Exception exception) {
        if (exception == null) {
            return "❌ Error desconocido al crear la cuenta";
        }
        
        String mensaje = exception.getMessage();
        if (mensaje == null) {
            return "❌ Error desconocido al crear la cuenta";
        }
        
        if (mensaje.contains("email-already-in-use")) {
            return "⚠️ Este correo electrónico ya está registrado. Ve al paso 1 para cambiar el email.";
        } else if (mensaje.contains("weak-password")) {
            return "⚠️ La contraseña es muy débil. Ve al paso 1 para cambiar la contraseña.";
        } else if (mensaje.contains("network")) {
            return "⚠️ Error de conexión. Verifica tu internet e inténtalo nuevamente.";
        } else {
            return "❌ Error al crear cuenta: " + mensaje;
        }
    }
    
    private void subirTodasLasImagenesYCrearDocumentos(String uid) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        // Obtener URIs de las imágenes del paso 2
        String selfieUriStr = prefs.getString("selfieUri", null);
        String fotoPerfilUriStr = prefs.getString("fotoPerfilUri", null);
        
        // Obtener URIs de los documentos del paso 3
        String antecedentesUriStr = prefs.getString("antecedentesUri", null);
        String medicoUriStr = prefs.getString("medicoUri", null);
        
        if (selfieUriStr == null || fotoPerfilUriStr == null) {
            Toast.makeText(this, "❌ Error: Faltan imágenes del paso 2. Vuelve al paso anterior.", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (antecedentesUriStr == null || medicoUriStr == null) {
            Toast.makeText(this, "❌ Error: Faltan documentos del paso 3.", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            Uri selfieUri = Uri.parse(selfieUriStr);
            Uri fotoPerfilUri = Uri.parse(fotoPerfilUriStr);
            Uri antecedentesUri = Uri.parse(antecedentesUriStr);
            Uri medicoUri = Uri.parse(medicoUriStr);
            
            // Subir todas las imágenes/documentos a Firebase Storage
            subirArchivosFirebaseStorage(uid, selfieUri, fotoPerfilUri, antecedentesUri, medicoUri);
            
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error al procesar archivos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void subirArchivosFirebaseStorage(String uid, Uri selfieUri, Uri fotoPerfilUri, Uri antecedentesUri, Uri medicoUri) {
        // Referencias de Storage
        StorageReference selfieRef = storage.getReference().child("selfie/" + uid + ".jpg");
        StorageReference fotoPerfilRef = storage.getReference().child("foto_de_perfil/" + uid + ".jpg");
        StorageReference antecedentesRef = storage.getReference().child("documentos/" + uid + "_antecedentes.pdf");
        StorageReference medicoRef = storage.getReference().child("documentos/" + uid + "_medico.pdf");
        
        // Subir archivos
        Task<Uri> selfieUpload = selfieRef.putFile(selfieUri).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return selfieRef.getDownloadUrl();
        });
        
        Task<Uri> fotoPerfilUpload = fotoPerfilRef.putFile(fotoPerfilUri).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return fotoPerfilRef.getDownloadUrl();
        });
        
        Task<Uri> antecedentesUpload = antecedentesRef.putFile(antecedentesUri).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return antecedentesRef.getDownloadUrl();
        });
        
        Task<Uri> medicoUpload = medicoRef.putFile(medicoUri).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return medicoRef.getDownloadUrl();
        });
        
        // Esperar a que todas las subidas terminen
        Tasks.whenAll(selfieUpload, fotoPerfilUpload, antecedentesUpload, medicoUpload)
                .addOnSuccessListener(aVoid -> {
                    // Obtener las URLs de descarga
                    try {
                        String selfieUrl = selfieUpload.getResult().toString();
                        String fotoPerfilUrl = fotoPerfilUpload.getResult().toString();
                        String antecedentesUrl = antecedentesUpload.getResult().toString();
                        String medicoUrl = medicoUpload.getResult().toString();
                        
                        // Crear documentos en Firestore
                        crearDocumentosFirestore(uid, selfieUrl, fotoPerfilUrl, antecedentesUrl, medicoUrl);
                        
                    } catch (Exception e) {
                        Toast.makeText(this, "❌ Error al obtener URLs: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    String errorMsg = obtenerMensajeErrorStorage(e, "subir archivos");
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
    }
    
    private void crearDocumentosFirestore(String uid, String selfieUrl, String fotoPerfilUrl, String antecedentesUrl, String medicoUrl) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        // Crear documento de usuario
        Map<String, Object> usuario = new HashMap<>();
        usuario.put("nombre", prefs.getString("nombre", ""));
        usuario.put("apellido", prefs.getString("apellido", ""));
        usuario.put("correo", prefs.getString("email", ""));
        usuario.put("telefono", prefs.getString("telefono", ""));
        usuario.put("direccion", prefs.getString("domicilio", ""));
        
        // Parsear fecha de nacimiento
        String fechaStr = prefs.getString("fecha_nacimiento", "");
        if (!fechaStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date fecha = sdf.parse(fechaStr);
                if (fecha != null) {
                    usuario.put("fecha_nacimiento", new Timestamp(fecha));
                }
            } catch (ParseException e) {
                // Ignorar error de fecha
            }
        }
        
        usuario.put("selfie_url", selfieUrl);
        usuario.put("foto_perfil", fotoPerfilUrl);
        usuario.put("rol", "PASEADOR");
        usuario.put("activo", true);
        usuario.put("fecha_registro", FieldValue.serverTimestamp());
        
        // Crear documento de paseador
        Map<String, Object> paseador = new HashMap<>();
        paseador.put("cedula", prefs.getString("cedula", ""));
        paseador.put("acepto_terminos", true); // Garantizado por el checkbox
        paseador.put("fecha_aceptacion_terminos", FieldValue.serverTimestamp());
        paseador.put("verificacion_estado", "PENDIENTE");
        paseador.put("calificacion_promedio", 0.0);
        paseador.put("num_servicios_completados", 0);
        
        // Campos del quiz (iniciales)
        paseador.put("quiz_completado", false);
        paseador.put("quiz_aprobado", false);
        paseador.put("quiz_intentos", 0);
        paseador.put("quiz_score_total", 0);
        
        paseador.put("ultima_actualizacion", FieldValue.serverTimestamp());
        paseador.put("verificacion_fecha", FieldValue.serverTimestamp());
        
        // Documentos
        Map<String, Object> documentos = new HashMap<>();
        documentos.put("certificado_antecedentes_url", antecedentesUrl);
        documentos.put("certificado_medico_url", medicoUrl);
        paseador.put("documentos", documentos);
        
        // Guardar en Firestore
        Task<Void> usuarioTask = db.collection("usuarios").document(uid).set(usuario);
        Task<Void> paseadorTask = db.collection("paseadores").document(uid).set(paseador);
        
        Tasks.whenAll(usuarioTask, paseadorTask)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Registro completado exitosamente", Toast.LENGTH_SHORT).show();
                    limpiarDatosTemporales();
                    mostrarPantallaProximamente();
                })
                .addOnFailureListener(e -> {
                    String errorMsg = obtenerMensajeErrorFirestore(e);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
    }
    
    private void limpiarDatosTemporales() {
        // Limpiar SharedPreferences del wizard
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
    */
    
    // Fin de métodos de Firebase comentados
    
    private boolean isImageFile(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    private String getFileName(Uri uri) {
        String fileName = "archivo";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Usar nombre por defecto si hay error
        }
        return fileName;
    }
    
    private boolean verificarDocumentosCompletos() {
        // Verificar URIs locales en lugar de URLs de Firebase
        if (antecedentesUri == null && medicoUri == null) {
            Toast.makeText(this, "⚠️ Por favor sube ambos documentos requeridos:\n• Certificado de antecedentes penales\n• Certificado médico", Toast.LENGTH_LONG).show();
            return false;
        } else if (antecedentesUri == null) {
            Toast.makeText(this, "⚠️ Falta subir el certificado de antecedentes penales", Toast.LENGTH_LONG).show();
            return false;
        } else if (medicoUri == null) {
            Toast.makeText(this, "⚠️ Falta subir el certificado médico", Toast.LENGTH_LONG).show();
            return false;
        }
        
        return true;
    }
    
    private String obtenerMensajeErrorFirestore(Exception exception) {
        if (exception == null) {
            return "❌ Error desconocido al guardar los documentos";
        }
        
        String mensaje = exception.getMessage();
        if (mensaje == null) {
            return "❌ Error desconocido al guardar los documentos";
        }
        
        if (mensaje.contains("network") || mensaje.contains("connection")) {
            return "⚠️ Error de conexión. Verifica tu internet e inténtalo nuevamente.";
        } else if (mensaje.contains("permission")) {
            return "⚠️ Error de permisos. Contacta al soporte técnico.";
        } else {
            return "❌ Error al guardar documentos: " + mensaje;
        }
    }

    private void mostrarSiguientePaso() {
        Toast.makeText(this, "✅ Documentos enviados", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, PaseadorRegistroPaso4Activity.class));
        finish();
    }

    private void subirAmbosDocumentosYActualizarFirestore() {
        String uid = mAuth.getCurrentUser().getUid();
        if (uid == null) {
            Toast.makeText(this, "❌ Usuario no válido", Toast.LENGTH_LONG).show();
            return;
        }

        // Referencias de Storage
        StorageReference antecedentesRef = storage.getReference().child("documentos/" + uid + "_antecedentes.pdf");
        StorageReference medicoRef = storage.getReference().child("documentos/" + uid + "_medico.pdf");

        Toast.makeText(this, "🔄 Subiendo documentos...", Toast.LENGTH_SHORT).show();

        Task<Uri> upAnte = antecedentesRef.putFile(antecedentesUri)
                .continueWithTask(t -> { if (!t.isSuccessful()) throw t.getException(); return antecedentesRef.getDownloadUrl(); });
        Task<Uri> upMed = medicoRef.putFile(medicoUri)
                .continueWithTask(t -> { if (!t.isSuccessful()) throw t.getException(); return medicoRef.getDownloadUrl(); });

        Tasks.whenAllSuccess(upAnte, upMed)
                .addOnSuccessListener(results -> {
                    try {
                        String antecedentesUrl = upAnte.getResult().toString();
                        String medicoUrl = upMed.getResult().toString();

                        // Guardar en SharedPreferences para consistencia con pasos previos
                        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                        prefs.edit()
                                .putString("antecedentesUrl", antecedentesUrl)
                                .putString("medicoUrl", medicoUrl)
                                .apply();

                        // Actualizar Firestore: paseadores/{uid}.documentos + ultima_actualizacion
                        FirebaseFirestore.getInstance()
                                .collection("paseadores").document(uid)
                                .update("documentos.certificado_antecedentes_url", antecedentesUrl,
                                        "documentos.certificado_medico_url", medicoUrl,
                                        "ultima_actualizacion", FieldValue.serverTimestamp())
                                .addOnSuccessListener(v -> mostrarSiguientePaso())
                                .addOnFailureListener(e -> Toast.makeText(this, "Error Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show());

                    } catch (Exception e) {
                        Toast.makeText(this, "Error al obtener URLs: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, obtenerMensajeErrorStorage(e, "subir"), Toast.LENGTH_LONG).show());
    }

    // Garantiza una sesión (anónima) para poder subir a Storage/Firestore durante el registro
    private void ensureSignedIn(Runnable onReady) {
        if (mAuth.getCurrentUser() != null) { onReady.run(); return; }
        mAuth.signInAnonymously()
                .addOnSuccessListener(result -> onReady.run())
                .addOnFailureListener(e -> Toast.makeText(this, "Error de autenticación: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        if (antecedentesUri != null) ed.putString("antecedentesUri", antecedentesUri.toString());
        if (medicoUri != null) ed.putString("medicoUri", medicoUri.toString());
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String a = prefs.getString("antecedentesUri", null);
        String m = prefs.getString("medicoUri", null);
        
        if (a != null) {
            try {
                antecedentesUri = Uri.parse(a);
                tvAntecedentesNombre.setText("✅ Archivo subido: " + getFileNameFromUri(antecedentesUri));
                tvAntecedentesNombre.setTextColor(Color.parseColor("#059669"));
            } catch (Exception e) {
                // Error al cargar URI - limpiar
                prefs.edit().remove("antecedentesUri").apply();
                antecedentesUri = null;
            }
        }
        
        if (m != null) {
            try {
                medicoUri = Uri.parse(m);
                tvMedicoNombre.setText("✅ Archivo subido: " + getFileNameFromUri(medicoUri));
                tvMedicoNombre.setTextColor(Color.parseColor("#059669"));
            } catch (Exception e) {
                // Error al cargar URI - limpiar
                prefs.edit().remove("medicoUri").apply();
                medicoUri = null;
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String s = uri.getLastPathSegment();
        return s != null ? s : uri.toString();
    }
    
    private void eliminarDocumento(String tipoDocumento) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        if ("antecedentes".equals(tipoDocumento)) {
            antecedentesUri = null;
            tvAntecedentesNombre.setText("(ningún archivo seleccionado)");
            tvAntecedentesNombre.setTextColor(getResources().getColor(android.R.color.darker_gray));
            findViewById(R.id.container_preview_antecedentes).setVisibility(ImageView.GONE);
            
            // Limpiar de SharedPreferences
            prefs.edit()
                .remove("antecedentesUri")
                .remove("antecedentesUrl")
                .apply();
                
            Toast.makeText(this, "✅ Certificado de antecedentes eliminado. Puedes subir uno nuevo.", Toast.LENGTH_SHORT).show();
        } else {
            medicoUri = null;
            tvMedicoNombre.setText("(ningún archivo seleccionado)");
            tvMedicoNombre.setTextColor(getResources().getColor(android.R.color.darker_gray));
            findViewById(R.id.container_preview_medico).setVisibility(ImageView.GONE);
            
            // Limpiar de SharedPreferences
            prefs.edit()
                .remove("medicoUri")
                .remove("medicoUrl")
                .apply();
                
            Toast.makeText(this, "✅ Certificado médico eliminado. Puedes subir uno nuevo.", Toast.LENGTH_SHORT).show();
        }
        
        // Guardar el estado actualizado
        saveState();
    }
    
    private void verificarEstadoDocumentos() {
        // Solo mostrar mensaje si hay documentos faltantes, no si están completos
        String mensaje = "";
        if (antecedentesUri == null) mensaje += "• Falta certificado de antecedentes\n";
        if (medicoUri == null) mensaje += "• Falta certificado médico\n";
        
        if (!mensaje.isEmpty()) {
            Toast.makeText(this, "📋 Documentos pendientes:\n" + mensaje, Toast.LENGTH_SHORT).show();
        }
        // Eliminado el mensaje de éxito para evitar spam
    }
}
