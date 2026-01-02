package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.appbar.MaterialToolbar;
import android.widget.ImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.utils.InputUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditarPerfilDuenoActivity extends AppCompatActivity {

    private static final String TAG = "EditarPerfilDueno";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String currentUserId, cedula;

    private ImageView ivAvatar;
    private TextView tvCambiarFoto, tvNombrePreview;
    private TextInputEditText etNombre, etApellido, etTelefono, etDomicilio;
    private TextInputLayout tilDomicilio;
    private ProgressBar pbGeolocate;
    private Button btnGuardarCambios;

    private Uri newPhotoUri;
    private Uri cameraPhotoUri;

    private String domicilio;
    private GeoPoint domicilioLatLng;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    private final Handler validationHandler = new Handler(Looper.getMainLooper());
    private Runnable validationRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil_dueno);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        initViews();
        setupToolbar();
        setupLocationServices();
        setupAutocompleteLauncher();
        loadDuenoData();
        setupListeners();
        validateFields(); // Initial validation
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvCambiarFoto = findViewById(R.id.tv_cambiar_foto);
        tvNombrePreview = findViewById(R.id.tv_nombre_preview);
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etTelefono = findViewById(R.id.et_telefono);
        etDomicilio = findViewById(R.id.et_domicilio);
        tilDomicilio = findViewById(R.id.til_domicilio);
        pbGeolocate = findViewById(R.id.pb_geolocate);
        btnGuardarCambios = findViewById(R.id.btn_guardar_cambios);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // Use iv_back for navigation
        View ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());
    }

    private void loadDuenoData() {
        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("usuarios").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                String nombre = userDoc.getString("nombre");
                String apellido = userDoc.getString("apellido");

                etNombre.setText(nombre);
                etApellido.setText(apellido);
                etTelefono.setText(userDoc.getString("telefono"));

                // Actualizar el nombre preview
                String nombreCompleto = nombre + " " + apellido;
                if (tvNombrePreview != null) {
                    tvNombrePreview.setText(nombreCompleto);
                }

                domicilio = userDoc.getString("direccion");
                etDomicilio.setText(domicilio);

                // Cargar coordenadas si existen
                GeoPoint geoPoint = userDoc.getGeoPoint("ubicacion");
                if (geoPoint != null) {
                    domicilioLatLng = geoPoint;
                }

                cedula = userDoc.getString("cedula"); // Store cedula

                String fotoUrl = userDoc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    Glide.with(this).load(MyApplication.getFixedUrl(fotoUrl)).circleCrop().into(ivAvatar);
                }
                validateFields();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al cargar datos del dueño.", Toast.LENGTH_SHORT).show();
        });
    }

    private final ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    // Validar imagen antes de usar
                    if (InputUtils.isValidImageFile(this, selectedUri, 5 * 1024 * 1024)) {
                        newPhotoUri = selectedUri;
                        ivAvatar.setImageURI(newPhotoUri);
                        validateFields();
                    } else {
                        Toast.makeText(this, "La imagen no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Validar imagen antes de usar
                    if (InputUtils.isValidImageFile(this, cameraPhotoUri, 5 * 1024 * 1024)) {
                        newPhotoUri = cameraPhotoUri;
                        ivAvatar.setImageURI(newPhotoUri);
                        validateFields();
                    } else {
                        Toast.makeText(this, "La imagen no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                    }
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

        // TextWatcher especial para nombre y apellido para actualizar el preview
        TextWatcher nombreTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateNombrePreview();
                if (validationRunnable != null) {
                    validationHandler.removeCallbacks(validationRunnable);
                }
                validationRunnable = () -> validateFields();
                validationHandler.postDelayed(validationRunnable, 300);
            }
        };

        etNombre.addTextChangedListener(nombreTextWatcher);
        etApellido.addTextChangedListener(nombreTextWatcher);
        etTelefono.addTextChangedListener(textWatcher);

        View.OnClickListener photoClickListener = v -> showPhotoOptionsDialog();
        ivAvatar.setOnClickListener(photoClickListener);
        tvCambiarFoto.setOnClickListener(photoClickListener);

        // Listeners para geolocalización
        etDomicilio.setOnClickListener(v -> launchAutocomplete());
        if (tilDomicilio != null) {
            tilDomicilio.setEndIconOnClickListener(v -> onGeolocateClick());
        }

        // SafeClickListener para prevenir doble-click
        btnGuardarCambios.setOnClickListener(InputUtils.createSafeClickListener(v -> saveDuenoData()));
    }

    private void showPhotoOptionsDialog() {
        String[] options = {"Tomar foto", "Elegir de la galería", "Cancelar"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cambiar foto de perfil");
        builder.setItems(options, (dialog, which) -> {
            if (options[which].equals("Tomar foto")) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "New Picture");
                values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
                cameraPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                cameraLauncher.launch(intent);
            } else if (options[which].equals("Elegir de la galería")) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                photoPickerLauncher.launch(intent);
            } else if (options[which].equals("Cancelar")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void validateFields() {
        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        String telefono = etTelefono.getText().toString().trim();

        // Usar InputUtils para validaciones
        boolean isValid = InputUtils.isValidName(nombre, 2, 50) &&
                InputUtils.isValidName(apellido, 2, 50) &&
                InputUtils.isValidTelefonoEcuador(telefono) &&
                InputUtils.isNotEmpty(domicilio);

        btnGuardarCambios.setEnabled(isValid);
        if (isValid) {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_primary)));
        } else {
            btnGuardarCambios.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (validationRunnable != null) {
            validationHandler.removeCallbacks(validationRunnable);
        }
    }

    private void saveDuenoData() {
        if (!btnGuardarCambios.isEnabled()) {
            Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId == null) return;

        // Ocultar teclado y mostrar estado de carga
        InputUtils.hideKeyboard(this);
        InputUtils.setButtonLoading(btnGuardarCambios, true, "Guardando...");

        if (newPhotoUri != null) {
            String nombre = etNombre.getText().toString().trim();
            String apellido = etApellido.getText().toString().trim();
            String folderName = currentUserId + "_" + (cedula != null ? cedula : "") + "_" + nombre + "_" + apellido;
            String fullStoragePath = "foto_de_perfil/" + folderName + "/foto_perfil.jpg";

            Intent serviceIntent = new Intent(this, FileUploadService.class);
            serviceIntent.setAction(FileUploadService.ACTION_UPLOAD);
            serviceIntent.putExtra(FileUploadService.EXTRA_FILE_URI, newPhotoUri);
            serviceIntent.putExtra(FileUploadService.EXTRA_FULL_STORAGE_PATH, fullStoragePath);
            serviceIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_COLLECTION, "usuarios");
            serviceIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_DOCUMENT, currentUserId);
            serviceIntent.putExtra(FileUploadService.EXTRA_FIRESTORE_FIELD, "foto_perfil");
            ContextCompat.startForegroundService(this, serviceIntent);
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
        userUpdates.put("direccion", domicilio);
        userUpdates.put("nombre_display", nombreDisplay);
        userUpdates.put("nombre_lowercase", nombreDisplay.toLowerCase());

        // Guardar coordenadas si existen
        if (domicilioLatLng != null) {
            userUpdates.put("ubicacion", domicilioLatLng);
        }

        db.collection("usuarios").document(currentUserId).set(userUpdates, SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                InputUtils.setButtonLoading(btnGuardarCambios, false);
                Toast.makeText(EditarPerfilDuenoActivity.this, "Perfil actualizado con éxito.", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(EditarPerfilDuenoActivity.this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                InputUtils.setButtonLoading(btnGuardarCambios, false);
            });
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchLocationAndFillAddress();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAutocompleteLauncher() {
        autocompleteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Place place = Autocomplete.getPlaceFromIntent(result.getData());
                        Log.i(TAG, "Place: " + place.getName() + ", " + place.getId() + ", " + place.getAddress());
                        domicilio = place.getAddress();
                        if (place.getLatLng() != null) {
                            domicilioLatLng = new GeoPoint(place.getLatLng().latitude, place.getLatLng().longitude);
                        }
                        etDomicilio.setText(domicilio);
                        validateFields();
                    } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                        Status status = Autocomplete.getStatusFromIntent(result.getData());
                        Log.e(TAG, status.getStatusMessage());
                        Toast.makeText(this, "Error en autocompletado: " + status.getStatusMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void launchAutocomplete() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("EC") // Opcional: Limitar a Ecuador
                .build(this);
        autocompleteLauncher.launch(intent);
    }

    private void onGeolocateClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndFillAddress();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationAndFillAddress() {
        showGeolocateLoading(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String addressLine = address.getAddressLine(0);
                        domicilio = addressLine;
                        domicilioLatLng = new GeoPoint(location.getLatitude(), location.getLongitude());
                        etDomicilio.setText(addressLine);
                        validateFields();
                        Toast.makeText(this, "Dirección autocompletada.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No se pudo encontrar una dirección para esta ubicación.", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Servicio de geocodificación no disponible", e);
                    Toast.makeText(this, "Error al obtener la dirección.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No se pudo obtener la ubicación. Asegúrate de que el GPS esté activado.", Toast.LENGTH_SHORT).show();
            }
            showGeolocateLoading(false);
        }).addOnFailureListener(e -> {
            showGeolocateLoading(false);
            Toast.makeText(this, "No se pudo obtener la ubicación. Asegúrate de que el GPS esté activado.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showGeolocateLoading(boolean isLoading) {
        if (pbGeolocate != null) {
            pbGeolocate.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (tilDomicilio != null) {
            tilDomicilio.setEndIconVisible(!isLoading);
        }
    }

    private void updateNombrePreview() {
        if (tvNombrePreview != null) {
            String nombre = etNombre.getText().toString().trim();
            String apellido = etApellido.getText().toString().trim();
            String nombreCompleto = nombre;
            if (!apellido.isEmpty()) {
                nombreCompleto += " " + apellido;
            }
            tvNombrePreview.setText(nombreCompleto.isEmpty() ? "Tu nombre" : nombreCompleto);
        }
    }
}
