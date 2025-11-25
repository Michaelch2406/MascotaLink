package com.mjc.mascotalink;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.adapters.FotosPaseoAdapter;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.WhatsAppUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PaseoEnCursoActivity extends AppCompatActivity {

    private static final String TAG = "PaseoEnCursoActivity";
    private static final int REQUEST_CODE_CAMERA = 2101;
    private static final int REQUEST_CODE_GALLERY = 2102;
    private static final int REQUEST_PERMISSION_CALL = 2201;
    private static final int REQUEST_PERMISSION_CAMERA = 2202;
    private static final int REQUEST_PERMISSION_LOCATION = 2203;
    private static final int SAVE_DELAY_MS = 500;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private DocumentReference reservaRef;
    private ListenerRegistration reservaListener;

    private TextView tvNombreMascota;
    private TextView tvPaseador;
    private TextView tvFechaHora;
    private TextView tvHoras;
    private TextView tvMinutos;
    private TextView tvSegundos;
    private TextView tvEstado;
    private com.google.android.material.imageview.ShapeableImageView ivFotoMascota;
    private TextInputEditText etNotas;
    private RecyclerView rvFotos;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnContactar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnCancelar;
    private MaterialButton btnAdjuntar;
    private BottomNavigationView bottomNav;
    private ProgressBar pbLoading;

    private FotosPaseoAdapter fotosAdapter;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private boolean isUpdatingNotesFromRemote = false;

    private Date fechaInicioPaseo;
    private long duracionMinutos = 0L;
    private String idReserva;
    private String contactoDueno;
    private String telefonoPendiente;
    private String nombreMascota = "";
    private String nombreDueno = "Dueno";
    private String roleActual = "PASEADOR";
    private String currentPaseadorNombre = ""; // New member variable

    private String mascotaIdActual;
    private String paseadorIdActual;
    private String duenoIdActual;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_paseo_en_curso);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupNotesWatcher();
        setupButtons();
        cargarRoleYBottomNav();

        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        reservaRef = db.collection("reservas").document(idReserva);
        escucharReserva();
        setupLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRoleYBottomNav();
        if (fechaInicioPaseo != null) {
            startTimer();
        }
        startLocationUpdates();
    }

    private void initViews() {
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        tvPaseador = findViewById(R.id.tv_paseador);
        tvFechaHora = findViewById(R.id.tv_fecha_hora);
        tvHoras = findViewById(R.id.tv_horas);
        tvMinutos = findViewById(R.id.tv_minutos);
        tvSegundos = findViewById(R.id.tv_segundos);
        tvEstado = findViewById(R.id.tv_estado);
        ivFotoMascota = findViewById(R.id.iv_foto_mascota);
        etNotas = findViewById(R.id.et_notas);
        rvFotos = findViewById(R.id.rv_fotos);
        btnContactar = findViewById(R.id.btn_contactar);
        btnCancelar = findViewById(R.id.btn_cancelar_paseo);
        btnAdjuntar = findViewById(R.id.btn_adjuntar_fotos);
        bottomNav = findViewById(R.id.bottom_nav);
        pbLoading = findViewById(R.id.pb_loading);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        fotosAdapter = new FotosPaseoAdapter(this, new FotosPaseoAdapter.OnFotoInteractionListener() {
            @Override
            public void onFotoClick(String url) {
                mostrarFotoCompleta(url);
            }

            @Override
            public void onFotoLongClick(String url) {
                mostrarDialogEliminarFoto(url);
            }
        });
        rvFotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvFotos.setAdapter(fotosAdapter);
    }

    private void setupNotesWatcher() {
        etNotas.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingNotesFromRemote) return;
                if (reservaRef == null) return;
                saveHandler.removeCallbacks(saveRunnable);
                saveRunnable = () -> guardarNotasEnFirebase(s.toString());
                saveHandler.postDelayed(saveRunnable, SAVE_DELAY_MS);
            }
        });
    }

    private void setupButtons() {
        btnContactar.setOnClickListener(v -> mostrarOpcionesContacto());
        btnCancelar.setOnClickListener(v -> iniciarProcesoCancelacion());
        btnAdjuntar.setOnClickListener(v -> mostrarOpcionesAdjuntar());
        
        // Click listeners for direct navigation
        ivFotoMascota.setOnClickListener(v -> navigateToPetProfile());
        tvNombreMascota.setOnClickListener(v -> navigateToPetProfile());
        tvPaseador.setOnClickListener(v -> navigateToOwnerProfile());
    }

    private void navigateToPetProfile() {
        if (mascotaIdActual != null && duenoIdActual != null && !mascotaIdActual.isEmpty() && !duenoIdActual.isEmpty()) {
            Intent intent = new Intent(this, PerfilMascotaActivity.class);
            intent.putExtra("mascota_id", mascotaIdActual);
            intent.putExtra("owner_id", duenoIdActual); // Pass owner ID for context
            startActivity(intent);
        } else {
            Toast.makeText(this, "Información de la mascota no disponible.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Cannot navigate to pet profile: mascotaIdActual or duenoIdActual is null/empty.");
        }
    }

    private void navigateToOwnerProfile() {
        if (duenoIdActual != null && !duenoIdActual.isEmpty()) {
            Intent intent = new Intent(this, PerfilDuenoActivity.class);
            intent.putExtra("id_dueno", duenoIdActual);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Información del dueño no disponible.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Cannot navigate to owner profile: duenoIdActual is null/empty.");
        }
    }


    private void cargarRoleYBottomNav() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        db.collection("usuarios").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    roleActual = doc != null ? doc.getString("rol") : "PASEADOR";
                    if (roleActual == null) roleActual = "PASEADOR";
                    BottomNavManager.setupBottomNav(this, bottomNav, roleActual, R.id.menu_walks);
                    if (doc != null) {
                        paseadorIdActual = doc.getId();
                        String nombre = doc.getString("nombre_display");
                        if (nombre != null && !nombre.isEmpty()) {
                            tvPaseador.setText(getString(R.string.paseo_en_curso_label_paseador, nombre));
                            currentPaseadorNombre = nombre; // Store the walker's name
                        }
                    }
                })
                .addOnFailureListener(e -> BottomNavManager.setupBottomNav(this, bottomNav, "PASEADOR", R.id.menu_walks));
    }

    private void escucharReserva() {
        mostrarLoading(true);
        reservaListener = reservaRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error escuchando reserva", error);
                mostrarLoading(false);
                Toast.makeText(this, "Error al cargar el paseo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                mostrarLoading(false);
                Toast.makeText(this, "La reserva ya no está disponible", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            manejarSnapshotReserva(snapshot);
            mostrarLoading(false);
        });
    }

    private void manejarSnapshotReserva(@NonNull DocumentSnapshot snapshot) {
        String estado = snapshot.getString("estado");
        if (estado == null) estado = "";

        // --- FIX: Manejo de Solicitud de Cancelación Bilateral ---
        if ("SOLICITUD_CANCELACION".equalsIgnoreCase(estado)) {
            String motivo = snapshot.getString("motivo_cancelacion");
            mostrarDialogoSolicitudCancelacion(motivo != null ? motivo : "Sin motivo especificado");
            return; // Detener actualización de UI normal mientras se resuelve
        }
        // ---------------------------------------------------------

        //vibe-fix: Validar transiciones de estado cuando el paseo se completa
        if (!estado.equalsIgnoreCase("EN_CURSO") && !estado.equalsIgnoreCase("EN_PROGRESO")) {
            if (estado.equalsIgnoreCase("COMPLETADO")) {
                // Si el paseo fue completado (por acción propia o remota), cerrar la actividad
                Log.d(TAG, "Paseo completado, cerrando actividad");
                Toast.makeText(this, "El paseo ha sido completado.", Toast.LENGTH_SHORT).show();
                stopTimer();
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
            } else {
                // Otro estado (CANCELADO, etc.)
                Toast.makeText(this, "Este paseo ya no está en curso.", Toast.LENGTH_SHORT).show();
                stopTimer();
                finish();
            }
            return;
        }
        tvEstado.setText(getString(R.string.paseo_en_curso_state_en_progreso));

        Timestamp inicioTimestamp = snapshot.getTimestamp("fecha_inicio_paseo");
        if (inicioTimestamp == null) {
            inicioTimestamp = snapshot.getTimestamp("hora_inicio");
        }
        if (inicioTimestamp != null) {
            Date nuevaFecha = inicioTimestamp.toDate();
            boolean reiniciarTimer = fechaInicioPaseo == null || fechaInicioPaseo.getTime() != nuevaFecha.getTime();
            fechaInicioPaseo = nuevaFecha;
            if (reiniciarTimer) {
                startTimer();
            }
            actualizarFechaHora(inicioTimestamp.toDate());
        }

        Long duracion = snapshot.getLong("duracion_minutos");
        if (duracion != null) {
            duracionMinutos = duracion;
            actualizarFechaHora(inicioTimestamp != null ? inicioTimestamp.toDate() : null);
        }

        String notas = snapshot.getString("notas_paseador");
        actualizarNotasDesdeServidor(notas);

        Object contactoEmergencia = snapshot.get("contacto_emergencia");
        if (contactoEmergencia instanceof String && !((String) contactoEmergencia).isEmpty()) {
            contactoDueno = (String) contactoEmergencia;
        }

        // Safely cast fotos_paseo to List<String>
        Object fotosObj = snapshot.get("fotos_paseo");
        List<String> fotos = new ArrayList<>();
        if (fotosObj instanceof List) {
            for (Object item : (List<?>) fotosObj) {
                if (item instanceof String) {
                    fotos.add((String) item);
                } else {
                    Log.w(TAG, "Unexpected item type in fotos_paseo: " + (item != null ? item.getClass().getName() : "null"));
                }
            }
        } else if (fotosObj != null) {
            Log.w(TAG, "fotos_paseo is not a List: " + fotosObj.getClass().getName());
        }
        actualizarGaleria(fotos);

        String mascotaId = snapshot.getString("id_mascota");
        if (mascotaId != null && !mascotaId.isEmpty() && !mascotaId.equals(mascotaIdActual)) {
            mascotaIdActual = mascotaId;
            cargarDatosMascota(mascotaId);
        }

        DocumentReference duenoRef = snapshot.getDocumentReference("id_dueno");
        if (duenoRef == null) {
            String duenoPath = snapshot.getString("id_dueno");
            if (duenoPath != null && !duenoPath.isEmpty()) {
                duenoRef = db.document(duenoPath);
            }
        }
        if (duenoRef != null) {
            String nuevoDuenoId = duenoRef.getId();
            if (!nuevoDuenoId.equals(duenoIdActual)) {
                duenoIdActual = nuevoDuenoId;
                cargarDatosDueno(duenoRef);
            }
        }

        DocumentReference paseadorRef = snapshot.getDocumentReference("id_paseador");
        if (paseadorRef == null) {
            String paseadorPath = snapshot.getString("id_paseador");
            if (paseadorPath != null && !paseadorPath.isEmpty()) {
                paseadorRef = db.document(paseadorPath);
            }
        }
        if (paseadorRef != null) {
            String nuevoPaseadorId = paseadorRef.getId();
            if (!nuevoPaseadorId.equals(paseadorIdActual)) {
                paseadorIdActual = nuevoPaseadorId;
                cargarDatosPaseador(paseadorRef);
            }
        }
    }

    private void cargarDatosMascota(String mascotaId) {
        if (duenoIdActual == null || duenoIdActual.isEmpty()) {
            Log.w(TAG, "duenoIdActual es nulo o vacío. Intentando cargar mascota desde colección global.");
            cargarMascotaDesdeColeccionGlobal(mascotaId);
            return;
        }

        // First, try to load from the subcollection within the owner's document
        db.collection("duenos").document(duenoIdActual).collection("mascotas").document(mascotaId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        processMascotaDocument(doc);
                    } else {
                        cargarMascotaDesdeColeccionGlobal(mascotaId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando mascota desde subcolección", e);
                    cargarMascotaDesdeColeccionGlobal(mascotaId);
                });
    }

    private void cargarMascotaDesdeColeccionGlobal(String mascotaId) {
        db.collection("mascotas").document(mascotaId).get()
                .addOnSuccessListener(globalDoc -> {
                    if (globalDoc != null && globalDoc.exists()) {
                        processMascotaDocument(globalDoc);
                    } else {
                        Log.w(TAG, "Mascota document not found in subcollection or top-level collection for ID: " + mascotaId);
                        tvNombreMascota.setText("Mascota");
                        ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando mascota desde colección global", e));
    }

    private void processMascotaDocument(@NonNull DocumentSnapshot doc) {
        nombreMascota = doc.getString("nombre") != null ? doc.getString("nombre") : "Mascota";
        tvNombreMascota.setText(nombreMascota);
        
        String urlFoto = doc.getString("foto_url");
        String urlFotoPrincipal = doc.getString("foto_principal_url");
        
        Log.d(TAG, "Procesando mascota ID: " + doc.getId());
        Log.d(TAG, " - Nombre: " + nombreMascota);
        Log.d(TAG, " - foto_url: " + urlFoto);
        Log.d(TAG, " - foto_principal_url: " + urlFotoPrincipal);

        if (urlFoto == null || urlFoto.isEmpty()) {
            urlFoto = urlFotoPrincipal;
        }
        
        if (urlFoto != null && !urlFoto.isEmpty()) {
            Log.d(TAG, "Intentando cargar foto con Glide: " + urlFoto);
            Glide.with(this)
                    .load(urlFoto)
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .error(R.drawable.ic_pet_placeholder)
                    .into(ivFotoMascota);
        } else {
            Log.w(TAG, "No se encontró URL de foto válida para la mascota.");
            ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
        }
    }

    private void cargarDatosDueno(DocumentReference duenoRef) {
        duenoRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String telefono = doc.getString("telefono");
                    if (telefono != null && !telefono.isEmpty()) {
                        contactoDueno = telefono;
                    }
                    // Guardar nombre para nombre de archivo
                    String nombre = doc.getString("nombre_display");
                    if (nombre != null && !nombre.isEmpty()) {
                        nombreDueno = nombre.replace(" ", "_"); // Sanitizar para nombre de archivo
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando datos del dueño", e));
    }

    private void cargarDatosPaseador(DocumentReference paseadorRef) {
        paseadorRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String nombre = doc.getString("nombre_display");
                    if (nombre == null || nombre.isEmpty()) {
                        nombre = doc.getString("nombre");
                    }
                    if (nombre != null && !nombre.isEmpty()) {
                        tvPaseador.setText(getString(R.string.paseo_en_curso_label_paseador, nombre));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando paseo", e));
    }

    private void actualizarFechaHora(@Nullable Date inicio) {
        if (inicio == null) {
            tvFechaHora.setText("");
            return;
        }
        Date fin = new Date(inicio.getTime() + TimeUnit.MINUTES.toMillis(Math.max(duracionMinutos, 0)));
        SimpleDateFormat fechaFormat = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
        SimpleDateFormat horaFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        String texto = fechaFormat.format(inicio) + ", " + horaFormat.format(inicio) + " - " + horaFormat.format(fin);
        tvFechaHora.setText(texto);
    }

    private void actualizarNotasDesdeServidor(@Nullable String notas) {
        isUpdatingNotesFromRemote = true;
        if (notas == null) notas = "";
        if (!notas.equals(etNotas.getText() != null ? etNotas.getText().toString() : "")) {
            etNotas.setText(notas);
            if (notas.length() > 0) {
                etNotas.setSelection(notas.length());
            }
        }
        isUpdatingNotesFromRemote = false;
    }

    private void actualizarGaleria(@Nullable List<String> fotos) {
        if (fotos == null || fotos.isEmpty()) {
            rvFotos.setVisibility(View.GONE);
            fotosAdapter.submitList(new ArrayList<>());
        } else {
            rvFotos.setVisibility(View.VISIBLE);
            fotosAdapter.submitList(fotos);
        }
    }

    private void guardarNotasEnFirebase(String notas) {
        reservaRef.update("notas_paseador", notas)
                .addOnFailureListener(e -> Log.e(TAG, "Error guardando notas", e));
    }

    private void mostrarOpcionesAdjuntar() {
        final String[] opciones = {"Cámara", "Galería"};
        new AlertDialog.Builder(this)
                .setTitle("Adjuntar fotos")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        intentarAbrirCamara();
                    } else {
                        abrirGaleria();
                    }
                })
                .show();
    }

    private void intentarAbrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
        } else {
            abrirCamara();
        }
    }

    private void abrirCamara() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        } else {
            Toast.makeText(this, "No hay cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_CODE_CAMERA) {
            Bitmap foto = (Bitmap) data.getParcelableExtra("data");
            if (foto != null) {
                subirFotoAFirebase(foto);
            }
        } else if (requestCode == REQUEST_CODE_GALLERY) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);
                    if (inputStream != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();
                        if (bitmap != null) {
                            subirFotoAFirebase(bitmap);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error leyendo imagen de galería", e);
                    Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void subirFotoAFirebase(Bitmap foto) {
        mostrarLoading(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        foto.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        //vibe-fix: Validar tamaño de foto < 5MB antes de subir
        final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
        if (data.length > MAX_SIZE_BYTES) {
            mostrarLoading(false);
            double sizeMB = data.length / (1024.0 * 1024.0);
            String mensaje = String.format(Locale.getDefault(), 
                "La imagen es demasiado grande (%.2f MB). El tamaño máximo permitido es 5 MB. Por favor, selecciona una imagen más pequeña.", 
                sizeMB);
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            Log.w(TAG, "Intento de subir foto mayor a 5MB: " + sizeMB + " MB");
            return;
        }

        // Formato descriptivo: Mascota_Dueno_FechaHora.jpg
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fechaHora = sdf.format(new Date());
        String nombreMascotaSanitizado = nombreMascota.replace(" ", "_");
        
        String nombreArchivo = String.format("%s_%s_%s.jpg", nombreMascotaSanitizado, nombreDueno, fechaHora);
        
        StorageReference ref = FirebaseStorage.getInstance().getReference("paseos/" + idReserva + "/" + nombreArchivo);

        ref.putBytes(data)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> agregarFotoAlArray(uri.toString()))
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al subir foto", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error subiendo foto", e);
                });
    }

    private void agregarFotoAlArray(String url) {
        Map<String, Object> nuevaActividad = new HashMap<>();
        nuevaActividad.put("evento", "FOTO_SUBIDA");
        nuevaActividad.put("descripcion", "El paseador ha subido una nueva foto");
        nuevaActividad.put("timestamp", new Date());

        reservaRef.update(
                "fotos_paseo", FieldValue.arrayUnion(url),
                "actividad", FieldValue.arrayUnion(nuevaActividad)
        )
                .addOnSuccessListener(unused -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Foto guardada", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "No se pudo guardar la foto", Toast.LENGTH_SHORT).show();
                });
    }

    private void mostrarDialogEliminarFoto(String url) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar foto")
                .setMessage("¿Deseas eliminar esta foto del paseo?")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarFoto(url))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminarFoto(String url) {
        reservaRef.update("fotos_paseo", FieldValue.arrayRemove(url))
                .addOnFailureListener(e -> Log.e(TAG, "No se pudo eliminar la foto", e));
    }

    private void mostrarFotoCompleta(String url) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_image, null);
        com.google.android.material.imageview.ShapeableImageView imageView = dialogView.findViewById(R.id.iv_fullscreen);
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_pet_placeholder)
                .error(R.drawable.ic_pet_placeholder)
                .into(imageView);
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void mostrarOpcionesContacto() {
        if (contactoDueno == null || contactoDueno.isEmpty()) {
            Toast.makeText(this, "Contacto no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] opciones = {"Llamar", "WhatsApp", "SMS"};
        new AlertDialog.Builder(this)
                .setTitle("Contactar dueño")
                .setItems(opciones, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            intentarLlamar(contactoDueno);
                            break;
                        case 1:
                            enviarWhatsApp(contactoDueno);
                            break;
                        case 2:
                            enviarSms(contactoDueno);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void intentarLlamar(String telefono) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            telefonoPendiente = telefono;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PERMISSION_CALL);
        } else {
            realizarLlamada(telefono);
        }
    }

    private void realizarLlamada(String telefono) {
        if (telefono == null || telefono.isEmpty()) {
            Toast.makeText(this, "Número no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + telefono));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar la llamada", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarWhatsApp(String telefono) {
        String mensaje = String.format(Locale.getDefault(),
                "¡Hola! Soy %s, el paseador a cargo de %s el día de hoy.",
                currentPaseadorNombre, nombreMascota);
        WhatsAppUtil.abrirWhatsApp(this, telefono, mensaje);
    }

    private void enviarSms(String telefono) {
        String mensaje = String.format(Locale.getDefault(),
                "¡Hola! Soy %s, el paseador a cargo de %s el día de hoy.",
                currentPaseadorNombre, nombreMascota);
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + telefono));
        intent.putExtra("sms_body", mensaje);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo enviar SMS", Toast.LENGTH_SHORT).show();
        }
    }

    private void iniciarProcesoCancelacion() {
        if (fechaInicioPaseo == null) {
            Toast.makeText(this, "El paseo aún no ha iniciado correctamente", Toast.LENGTH_SHORT).show();
            return;
        }

        long tiempoTranscurrido = calcularTiempoTranscurrido();
        // Regla: Bloquear cancelación antes de 10 minutos (600,000 ms)
        if (tiempoTranscurrido < 10 * 60 * 1000) {
            long minutosRestantes = 10 - TimeUnit.MILLISECONDS.toMinutes(tiempoTranscurrido);
            Toast.makeText(this, "Debes esperar " + minutosRestantes + " minutos más para cancelar.", Toast.LENGTH_LONG).show();
            return;
        }

        mostrarDialogoCancelacion();
    }

    private void mostrarDialogoCancelacion() {
        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_cancelar_paseo, null);
        builder.setView(view);

        android.widget.RadioGroup rgMotivos = view.findViewById(R.id.rg_motivos);
        TextInputEditText etOtroMotivo = view.findViewById(R.id.et_otro_motivo);
        
        rgMotivos.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_otro) {
                etOtroMotivo.setVisibility(View.VISIBLE);
            } else {
                etOtroMotivo.setVisibility(View.GONE);
            }
        });

        builder.setPositiveButton("Confirmar Cancelación", (dialog, which) -> {
            String motivo = "";
            int selectedId = rgMotivos.getCheckedRadioButtonId();
            
            if (selectedId == -1) {
                Toast.makeText(this, "Debes seleccionar un motivo", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedId == R.id.rb_perro_no_disponible) motivo = "Perro no disponible";
            else if (selectedId == R.id.rb_emergencia) motivo = "Emergencia con la mascota";
            else if (selectedId == R.id.rb_seguridad) motivo = "Problema de seguridad";
            else if (selectedId == R.id.rb_otro) motivo = etOtroMotivo.getText().toString();
            else if (selectedId == R.id.rb_finalizar_exito) { // Opción para finalizar con éxito
                 confirmarFinalizacionExito(); // Reutilizar lógica de éxito
                 return;
            }

            if (motivo.isEmpty()) {
                Toast.makeText(this, "Debes especificar el motivo", Toast.LENGTH_SHORT).show();
                return;
            }

            confirmarCancelacionPaseador(motivo);
        });

        builder.setNegativeButton("Volver al paseo", null);
        builder.show();
    }

    private void confirmarCancelacionPaseador(String motivo) {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "CANCELADO"); // Estado final para que el sistema lo reconozca como terminado
        data.put("sub_estado", "CANCELADO_PASEADOR"); // Detalle para lógica interna
        data.put("motivo_cancelacion", motivo);
        data.put("cancelado_por", "PASEADOR");
        data.put("fecha_fin_paseo", new Date());

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Paseo cancelado.", Toast.LENGTH_SHORT).show();
                    mostrarLoading(false);
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al cancelar paseo", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error cancelando paseo", e);
                });
    }

    private void confirmarFinalizacionExito() {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "COMPLETADO");
        data.put("fecha_fin_paseo", new Date());
        data.put("tiempo_total_minutos", TimeUnit.MILLISECONDS.toMinutes(calcularTiempoTranscurrido()));

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "¡Paseo finalizado con éxito!", Toast.LENGTH_SHORT).show();
                    mostrarLoading(false);
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al finalizar paseo", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error finalizando paseo", e);
                });
    }

    private void mostrarDialogoSolicitudCancelacion(String motivo) {
        if (isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("Solicitud de cancelación")
                .setMessage("El dueño ha solicitado cancelar el paseo.\n\nMotivo: " + motivo)
                .setCancelable(false)
                .setPositiveButton("Aceptar cancelación", (dialog, which) -> aceptarCancelacionMutua())
                .setNegativeButton("Rechazar / Continuar", (dialog, which) -> rechazarCancelacion())
                .show();
    }

    private void aceptarCancelacionMutua() {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "CANCELADO");
        data.put("sub_estado", "CANCELADO_MUTUO");
        data.put("fecha_fin_paseo", new Date());

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Paseo cancelado mutuamente.", Toast.LENGTH_SHORT).show();
                    mostrarLoading(false);
                    finish();
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al aceptar cancelación", Toast.LENGTH_SHORT).show();
                });
    }

    private void rechazarCancelacion() {
        mostrarLoading(true);
        // Volver al estado activo
        reservaRef.update("estado", "EN_CURSO")
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Cancelación rechazada. El paseo continúa.", Toast.LENGTH_SHORT).show();
                    mostrarLoading(false);
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al rechazar", Toast.LENGTH_SHORT).show();
                });
    }

    private long calcularTiempoTranscurrido() {
        if (fechaInicioPaseo == null) return 0;
        return Math.max(0, System.currentTimeMillis() - fechaInicioPaseo.getTime());
    }

    private long obtenerTiempoMinimo() {
        if (duracionMinutos <= 0) return 0;
        return (long) (duracionMinutos * 60000 * 0.8f);
    }

    private void startTimer() {
        stopTimer();
        //vibe-fix: Validar fecha_inicio_paseo NULL antes de iniciar temporizador
        if (fechaInicioPaseo == null) {
            Log.w(TAG, "startTimer: fechaInicioPaseo es null, cancelando temporizador");
            Toast.makeText(this, "Error: No se pudo obtener la fecha de inicio del paseo", Toast.LENGTH_LONG).show();
            tvHoras.setText("00");
            tvMinutos.setText("00");
            tvSegundos.setText("00");
            return;
        }
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                //vibe-fix: Validar fecha_inicio_paseo NULL en cada tick del temporizador
                if (isFinishing() || isDestroyed()) {
                    stopTimer();
                    return;
                }
                if (fechaInicioPaseo == null) {
                    Log.w(TAG, "Timer tick: fechaInicioPaseo es null, deteniendo temporizador");
                    stopTimer();
                    runOnUiThread(() -> {
                        Toast.makeText(PaseoEnCursoActivity.this, 
                            "Error: La fecha de inicio del paseo no está disponible", 
                            Toast.LENGTH_LONG).show();
                        tvHoras.setText("00");
                        tvMinutos.setText("00");
                        tvSegundos.setText("00");
                    });
                    return;
                }
                long elapsed = calcularTiempoTranscurrido();
                long horas = TimeUnit.MILLISECONDS.toHours(elapsed);
                long minutos = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
                long segundos = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        tvHoras.setText(String.format(Locale.getDefault(), "%02d", horas));
                        tvMinutos.setText(String.format(Locale.getDefault(), "%02d", minutos));
                        tvSegundos.setText(String.format(Locale.getDefault(), "%02d", segundos));
                        // La lógica de habilitar botón Finalizar ahora está en el diálogo
                    }
                });
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }



    private void mostrarLoading(boolean mostrar) {
        pbLoading.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        stopLocationUpdates();
        saveHandler.removeCallbacksAndMessages(null);
        if (reservaListener != null) {
            reservaListener.remove();
        }
    }

    private void setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
                .setMinUpdateDistanceMeters(10)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (android.location.Location location : locationResult.getLocations()) {
                    if (location != null) {
                        actualizarUbicacionFirestore(location);
                    }
                }
            }
        };

        checkLocationPermissionAndStart();
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            }
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void actualizarUbicacionFirestore(android.location.Location location) {
        if (reservaRef == null) return;

        GeoPoint punto = new GeoPoint(location.getLatitude(), location.getLongitude());

        // 1. Guardar en el historial del paseo (para el dueño de este paseo)
        reservaRef.update("ubicaciones", FieldValue.arrayUnion(punto))
                .addOnFailureListener(e -> Log.e(TAG, "Error actualizando historial de ubicación", e));

        // 2. Publicar ubicación en tiempo real en el perfil del usuario (para la búsqueda global)
        if (auth.getCurrentUser() != null) {
            db.collection("usuarios").document(auth.getCurrentUser().getUid())
                    .update("ubicacion_actual", punto)
                    .addOnFailureListener(e -> Log.e(TAG, "Error publicando ubicación en tiempo real", e));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //vibe-fix: Agregar callback onRequestPermissionsResult() para llamadas telefónicas
        if (requestCode == REQUEST_PERMISSION_CALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (telefonoPendiente != null && !telefonoPendiente.isEmpty()) {
                    realizarLlamada(telefonoPendiente);
                } else {
                    Log.w(TAG, "Permiso de llamada concedido pero telefonoPendiente es null");
                    Toast.makeText(this, "Error: No se pudo obtener el número de teléfono", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Permiso de llamada denegado. No se puede realizar la llamada.", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Usuario denegó permiso de llamada");
            }
            telefonoPendiente = null;
        } else if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                abrirCamara();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado. No se pueden tomar fotos.", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Usuario denegó permiso de cámara");
            }
        } else if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado. No se podrá rastrear el paseo.", Toast.LENGTH_LONG).show();
            }
        }
    }
}

