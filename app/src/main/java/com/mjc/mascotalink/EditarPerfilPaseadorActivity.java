package com.mjc.mascotalink;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.ProgressBar;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditarPerfilPaseadorActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String currentUserId, cedula;

    private EditText etNombre, etApellido, etEmail, etTelefono, etDomicilio, etMotivacion, etInicioExperienciaPicker;
    private VideoView video_preview;
    private ProgressBar video_progress;
    private Button btnGuardarCambios, btnSubirVideo, btnGrabarVideo;
    private ChipGroup cgTiposPerros;
    private com.google.android.material.imageview.ShapeableImageView ivAvatarPaseador;

    private Uri videoUri, newVideoUri, newAvatarUri;
    private Calendar inicioExperienciaCalendar;

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
        loadPaseadorData();
        setupListeners();
        validateFields(); // Initial validation
    }

    private void initViews() {
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etEmail = findViewById(R.id.et_email);
        etTelefono = findViewById(R.id.et_telefono);
        etDomicilio = findViewById(R.id.et_domicilio);
        etMotivacion = findViewById(R.id.et_motivacion);
        etInicioExperienciaPicker = findViewById(R.id.et_inicio_experiencia_picker);
        btnGuardarCambios = findViewById(R.id.btn_guardar_cambios);

        video_preview = findViewById(R.id.video_preview);
        video_progress = findViewById(R.id.video_progress);
        btnSubirVideo = findViewById(R.id.btn_subir_video);
        btnGrabarVideo = findViewById(R.id.btn_grabar_video);

        cgTiposPerros = findViewById(R.id.cg_tipos_perros);
        inicioExperienciaCalendar = Calendar.getInstance();
        ivAvatarPaseador = findViewById(R.id.iv_avatar_paseador);


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
                            .load(fotoUrl)
                            .circleCrop()
                            .into(ivAvatarPaseador);
                }

                validateFields();
            }
        });

        db.collection("paseadores").document(currentUserId).get().addOnSuccessListener(paseadorDoc -> {
            if (paseadorDoc.exists()) {
                Map<String, Object> perfilProfesional = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
                if (perfilProfesional != null) {
                    etMotivacion.setText((String) perfilProfesional.get("motivacion"));

                    String inicioExpStr = (String) perfilProfesional.get("experiencia_general");
                    if (inicioExpStr != null && !inicioExpStr.isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
                            Date date = sdf.parse(inicioExpStr);
                            if (date != null) {
                                inicioExperienciaCalendar.setTime(date);
                                updateInicioExperienciaLabel();
                            }
                        } catch (java.text.ParseException e) {
                            e.printStackTrace();
                        }
                    }

                    String videoUrl = (String) perfilProfesional.get("video_presentacion_url");
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        video_progress.setVisibility(View.VISIBLE);
                        video_preview.setVideoURI(Uri.parse(videoUrl));
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
                validateFields();
            }
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
                validateFields();
            }
        };

        etNombre.addTextChangedListener(textWatcher);
        etApellido.addTextChangedListener(textWatcher);
        etTelefono.addTextChangedListener(textWatcher);
        etDomicilio.addTextChangedListener(textWatcher);
        etMotivacion.addTextChangedListener(textWatcher);

        cgTiposPerros.setOnCheckedStateChangeListener((group, checkedIds) -> validateFields());

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

        etInicioExperienciaPicker.setOnClickListener(v -> showDatePickerDialog());
        ivAvatarPaseador.setOnClickListener(v -> openImagePicker());


        btnGuardarCambios.setOnClickListener(v -> savePaseadorData());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void validateFields() {
        boolean isValid = !etNombre.getText().toString().trim().isEmpty() &&
                !etApellido.getText().toString().trim().isEmpty() &&
                !etTelefono.getText().toString().trim().isEmpty() &&
                !etDomicilio.getText().toString().trim().isEmpty() &&
                !etMotivacion.getText().toString().trim().isEmpty() &&
                !etInicioExperienciaPicker.getText().toString().trim().isEmpty() &&
                cgTiposPerros.getCheckedChipIds().size() > 0;

        btnGuardarCambios.setEnabled(isValid);
        if (isValid) {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_primary)));
        } else {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light)));
        }
    }

    private void showDatePickerDialog() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            inicioExperienciaCalendar.set(Calendar.YEAR, year);
            inicioExperienciaCalendar.set(Calendar.MONTH, month);
            inicioExperienciaCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateInicioExperienciaLabel();
            validateFields();
        };

        new DatePickerDialog(this,
                dateSetListener,
                inicioExperienciaCalendar.get(Calendar.YEAR),
                inicioExperienciaCalendar.get(Calendar.MONTH),
                inicioExperienciaCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateInicioExperienciaLabel() {
        String myFormat = "MMMM yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, new Locale("es", "ES"));
        etInicioExperienciaPicker.setText(sdf.format(inicioExperienciaCalendar.getTime()));
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
        userUpdates.put("nombre", etNombre.getText().toString().trim());
        userUpdates.put("apellido", etApellido.getText().toString().trim());
        userUpdates.put("telefono", etTelefono.getText().toString().trim());
        userUpdates.put("direccion", etDomicilio.getText().toString().trim());
        userUpdates.put("nombre_display", etNombre.getText().toString().trim() + " " + etApellido.getText().toString().trim());

        Task<Void> userTask = db.collection("usuarios").document(currentUserId).set(userUpdates, SetOptions.merge());

        Map<String, Object> paseadorUpdates = new HashMap<>();
        paseadorUpdates.put("perfil_profesional.motivacion", etMotivacion.getText().toString().trim());

        String myFormat = "MMMM yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, new Locale("es", "ES"));
        String fechaFormateada = sdf.format(inicioExperienciaCalendar.getTime());
        paseadorUpdates.put("perfil_profesional.experiencia_general", fechaFormateada);

        List<String> tamanosList = new ArrayList<>();
        for (int id : cgTiposPerros.getCheckedChipIds()) {
            Chip chip = findViewById(id);
            tamanosList.add(chip.getText().toString());
        }
        paseadorUpdates.put("manejo_perros.tamanos", tamanosList);

        Task<Void> paseadorTask = db.collection("paseadores").document(currentUserId).set(paseadorUpdates, SetOptions.merge());

        Tasks.whenAllSuccess(userTask, paseadorTask).addOnSuccessListener(list -> {
            Toast.makeText(EditarPerfilPaseadorActivity.this, "Perfil actualizado con éxito.", Toast.LENGTH_SHORT).show();
            finish(); // Volver a la pantalla de perfil
        }).addOnFailureListener(e -> {
            Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al guardar datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            btnGuardarCambios.setEnabled(true);
        });
    }
}
