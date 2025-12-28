package com.mjc.mascotalink;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.MyApplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditarPerfilPaseadorActivity extends AppCompatActivity {

    private static final String TAG = "EditarPerfilPaseador";
    private boolean isLoadingData = false;

    private final Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String currentUserId, cedula;

    private EditText etNombre, etApellido, etEmail, etTelefono, etDomicilio, etMotivacion;
    private Spinner spinnerAnosExperiencia;
    private VideoView video_preview;
    private ProgressBar video_progress;
    private Button btnGuardarCambios, btnSubirVideo, btnGrabarVideo;
    private ChipGroup cgTiposPerros;
    private com.google.android.material.imageview.ShapeableImageView ivAvatarPaseador;

    private Uri videoUri, newVideoUri, newAvatarUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    newAvatarUri = result.getData().getData();
                    ivAvatarPaseador.setImageURI(newAvatarUri);
                    validateFields(); 
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        initViews();
        setupToolbar();
        setupListeners(); // Configurar listeners ANTES de cargar datos
        loadPaseadorData();
        // NO validar aquí - validateFields() se llama después de cargar todos los datos
    }

    private void initViews() {
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etEmail = findViewById(R.id.et_email);
        etTelefono = findViewById(R.id.et_telefono);
        etDomicilio = findViewById(R.id.et_domicilio);
        etMotivacion = findViewById(R.id.et_motivacion);
        spinnerAnosExperiencia = findViewById(R.id.spinner_anos_experiencia);
        btnGuardarCambios = findViewById(R.id.btn_guardar_cambios);

        video_preview = findViewById(R.id.video_preview);
        video_progress = findViewById(R.id.video_progress);
        btnSubirVideo = findViewById(R.id.btn_subir_video);
        btnGrabarVideo = findViewById(R.id.btn_grabar_video);

        cgTiposPerros = findViewById(R.id.cg_tipos_perros);
        ivAvatarPaseador = findViewById(R.id.iv_avatar_paseador);

        // Configurar el adapter del spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.anos_experiencia, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAnosExperiencia.setAdapter(adapter);

        // Establecer una selección inicial para que el spinner funcione
        spinnerAnosExperiencia.setSelection(0);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadPaseadorData() {
        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isLoadingData = true; // Marcar que estamos cargando datos

        db.collection("usuarios").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                etNombre.setText(userDoc.getString("nombre"));
                etApellido.setText(userDoc.getString("apellido"));
                etTelefono.setText(userDoc.getString("telefono"));
                etDomicilio.setText(userDoc.getString("direccion"));
                etEmail.setText(mAuth.getCurrentUser().getEmail());
                cedula = userDoc.getString("cedula"); // Store cedula

                String fotoUrl = userDoc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                            .load(MyApplication.getFixedUrl(fotoUrl))
                            .circleCrop()
                            .into(ivAvatarPaseador);
                }

                // NO validar aquí, esperar a que se carguen TODOS los datos
                // validateFields() se llama después de cargar datos de paseadores
            }
        });

        db.collection("paseadores").document(currentUserId).get().addOnSuccessListener(paseadorDoc -> {
            if (paseadorDoc.exists()) {
                Map<String, Object> perfilProfesional = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
                if (perfilProfesional != null) {
                    etMotivacion.setText((String) perfilProfesional.get("motivacion"));

                    // Cargar años de experiencia
                    Object anosExpObj = perfilProfesional.get("anos_experiencia");
                    int anosExperiencia = 0;
                    if (anosExpObj != null) {
                        if (anosExpObj instanceof Long) {
                            anosExperiencia = ((Long) anosExpObj).intValue();
                        } else if (anosExpObj instanceof Integer) {
                            anosExperiencia = (Integer) anosExpObj;
                        }
                    }

                    // Convertir el número a la posición del spinner
                    int position = convertirNumeroAPosicion(anosExperiencia);
                    spinnerAnosExperiencia.setSelection(position);

                    String videoUrl = (String) perfilProfesional.get("video_presentacion_url");
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        video_progress.setVisibility(View.VISIBLE);
                        video_preview.setVideoURI(Uri.parse(MyApplication.getFixedUrl(videoUrl)));
                        MediaController mediaController = new MediaController(this);
                        video_preview.setMediaController(mediaController);
                        video_preview.setOnPreparedListener(mp -> {
                            video_progress.setVisibility(View.GONE);
                            mp.setLooping(true);
                            video_preview.start();
                        });
                    }
                }

                Map<String, Object> manejoPerros = (Map<String, Object>) paseadorDoc.get("manejo_perros");
                if (manejoPerros != null) {
                    List<String> tamanos = (List<String>) manejoPerros.get("tamanos");
                    if (tamanos != null && !tamanos.isEmpty()) {
                        for (int i = 0; i < cgTiposPerros.getChildCount(); i++) {
                            Chip chip = (Chip) cgTiposPerros.getChildAt(i);
                            if (tamanos.contains(chip.getText().toString())) {
                                chip.setChecked(true);
                            }
                        }
                    }
                }

                // Terminar la carga de datos y validar
                isLoadingData = false;
                validateFields();
            }
        }).addOnFailureListener(e -> {
            // En caso de error al cargar datos de paseador, aún validar
            isLoadingData = false;
            validateFields();
        });


    }

    private final ActivityResultLauncher<Intent> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    newVideoUri = result.getData().getData();
                    video_preview.setVideoURI(newVideoUri);
                    video_preview.start();
                    validateFields();
                }
            });

    private final ActivityResultLauncher<Intent> videoRecorderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    newVideoUri = videoUri;
                    video_preview.setVideoURI(newVideoUri);
                    video_preview.start();
                    validateFields();
                }
            });

    private void setupListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (validationRunnable != null) {
                    validationHandler.removeCallbacks(validationRunnable);
                }
                validationRunnable = () -> validateFields();
                validationHandler.postDelayed(validationRunnable, 300);
            }
        };

        etNombre.addTextChangedListener(textWatcher);
        etApellido.addTextChangedListener(textWatcher);
        etTelefono.addTextChangedListener(textWatcher);
        etEmail.addTextChangedListener(textWatcher);
        etDomicilio.addTextChangedListener(textWatcher);
        etMotivacion.addTextChangedListener(textWatcher);

        cgTiposPerros.setOnCheckedStateChangeListener((group, checkedIds) -> validateFields());

        // Listener para el spinner de años de experiencia
        spinnerAnosExperiencia.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                validateFields();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No hacer nada
            }
        });

        btnSubirVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            videoPickerLauncher.launch(intent);
        });

        btnGrabarVideo.setOnClickListener(v -> {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, "New Video");
            values.put(MediaStore.Video.Media.DESCRIPTION, "From Camera");
            videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
            videoRecorderLauncher.launch(intent);
        });

        ivAvatarPaseador.setOnClickListener(v -> openImagePicker());


        btnGuardarCambios.setOnClickListener(v -> savePaseadorData());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void validateFields() {
        if (isLoadingData) {
            return;
        }

        String telefono = etTelefono.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        boolean isValid = !etNombre.getText().toString().trim().isEmpty() &&
                !etApellido.getText().toString().trim().isEmpty() &&
                !telefono.isEmpty() &&
                isValidPhoneNumber(telefono) &&
                !email.isEmpty() &&
                isValidEmail(email) &&
                !etDomicilio.getText().toString().trim().isEmpty() &&
                !etMotivacion.getText().toString().trim().isEmpty() &&
                spinnerAnosExperiencia.getSelectedItem() != null &&
                cgTiposPerros.getCheckedChipIds().size() > 0;

        btnGuardarCambios.setEnabled(isValid);
        if (isValid) {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_primary)));
        } else {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light)));
        }
    }

    private boolean isValidPhoneNumber(String phone) {
        return phone.matches("^[0-9]{8,15}$");
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (validationRunnable != null) {
            validationHandler.removeCallbacks(validationRunnable);
        }
    }

    /**
     * Convierte el texto seleccionado del spinner a un número
     * Ej: "5 años" -> 5, "Más de 10 años" -> 11
     */
    private int convertirTextoANumero(String texto) {
        if (texto == null || texto.isEmpty()) {
            return 0;
        }

        if (texto.equals("Menos de 1 año")) {
            return 0;
        } else if (texto.equals("Más de 10 años")) {
            return 11;
        } else {
            // Extraer el número del texto (ej: "5 años" -> 5)
            String[] partes = texto.split(" ");
            try {
                return Integer.parseInt(partes[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /**
     * Convierte un número de años a la posición del spinner
     * Ej: 0 -> 0 ("Menos de 1 año"), 5 -> 5 ("5 años"), 11 -> 11 ("Más de 10 años")
     */
    private int convertirNumeroAPosicion(int numero) {
        if (numero == 0) {
            return 0; // "Menos de 1 año"
        } else if (numero >= 1 && numero <= 10) {
            return numero; // "1 año", "2 años", ..., "10 años"
        } else {
            return 11; // "Más de 10 años"
        }
    }

    private void savePaseadorData() {
        if (!btnGuardarCambios.isEnabled()) {
            Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId == null) return;

        btnGuardarCambios.setEnabled(false);
        Toast.makeText(this, "Guardando cambios...", Toast.LENGTH_SHORT).show();

        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        String folderName = currentUserId + "_" + (cedula != null ? cedula : "") + "_" + nombre + "_" + apellido;

        // Iniciar la subida del video en segundo plano si hay uno nuevo
        if (newVideoUri != null) {
            String videoPath = "video_presentacion/" + folderName + "/video_presentacion.mp4";
            Intent videoIntent = new Intent(this, FileUploadService.class);
            videoIntent.setAction(FileUploadService.ACTION_UPLOAD);
            videoIntent.putExtra(FileUploadService.EXTRA_FILE_URI, newVideoUri);
            videoIntent.putExtra(FileUploadService.EXTRA_FULL_STORAGE_PATH, videoPath);
            videoIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_COLLECTION, "paseadores");
            videoIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_DOCUMENT, currentUserId);
            videoIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_FIELD, "perfil_profesional.video_presentacion_url");
            ContextCompat.startForegroundService(this, videoIntent);
        }

        // Iniciar la subida del avatar en segundo plano si hay uno nuevo
        if (newAvatarUri != null) {
            String avatarPath = "foto_de_perfil/" + folderName + "/foto_perfil.jpg";
            Intent avatarIntent = new Intent(this, FileUploadService.class);
            avatarIntent.setAction(FileUploadService.ACTION_UPLOAD);
            avatarIntent.putExtra(FileUploadService.EXTRA_FILE_URI, newAvatarUri);
            avatarIntent.putExtra(FileUploadService.EXTRA_FULL_STORAGE_PATH, avatarPath);
            avatarIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_COLLECTION, "usuarios");
            avatarIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_DOCUMENT, currentUserId);
            avatarIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_FIELD, "foto_perfil");
            ContextCompat.startForegroundService(this, avatarIntent);
        }

        updateFirestore();
    }

    private void updateFirestore() {
        Map<String, Object> userUpdates = new HashMap<>();
        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        String nombreDisplay = nombre + " " + apellido;

        userUpdates.put("nombre", nombre);
        userUpdates.put("apellido", apellido);
        userUpdates.put("telefono", etTelefono.getText().toString().trim());
        userUpdates.put("direccion", etDomicilio.getText().toString().trim());
        userUpdates.put("nombre_display", nombreDisplay);
        userUpdates.put("nombre_lowercase", nombreDisplay.toLowerCase());

        Task<Void> userTask = db.collection("usuarios").document(currentUserId).update(userUpdates);

        Map<String, Object> paseadorUpdates = new HashMap<>();
        paseadorUpdates.put("perfil_profesional.motivacion", etMotivacion.getText().toString().trim());

        // Guardar años de experiencia como número
        int anosExperiencia = convertirTextoANumero((String) spinnerAnosExperiencia.getSelectedItem());
        paseadorUpdates.put("perfil_profesional.anos_experiencia", anosExperiencia);

        List<String> tamanosList = new ArrayList<>();
        for (int id : cgTiposPerros.getCheckedChipIds()) {
            Chip chip = findViewById(id);
            tamanosList.add(chip.getText().toString());
        }
        paseadorUpdates.put("manejo_perros.tamanos", tamanosList);

        Task<Void> paseadorTask = db.collection("paseadores").document(currentUserId).update(paseadorUpdates);

        Tasks.whenAllSuccess(userTask, paseadorTask).addOnSuccessListener(list -> {
            Toast.makeText(EditarPerfilPaseadorActivity.this, "Perfil actualizado con éxito.", Toast.LENGTH_SHORT).show();
            finish(); // Volver a la pantalla de perfil
        }).addOnFailureListener(e -> {
            Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al guardar datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            btnGuardarCambios.setEnabled(true);
        });
    }
}
