package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class MascotaRegistroPaso1Activity extends AppCompatActivity {

    private ImageView arrowBack, fotoPrincipalImageView;
    private TextInputLayout nombreTextField, pesoTextField, fechaNacimientoTextField, razaTextField;
    private AutoCompleteTextView razaAutoComplete;
    private RadioGroup sexoRadioGroup;
    private ChipGroup tamanoChipGroup;
    private Button siguienteButton, elegirGaleriaButton, tomarFotoButton;
    private TextInputEditText fechaNacimientoEditText;

    private Uri fotoUri;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso1);

        arrowBack = findViewById(R.id.arrow_back);
        fotoPrincipalImageView = findViewById(R.id.fotoPrincipalImageView);
        nombreTextField = findViewById(R.id.nombreTextField);
        razaTextField = findViewById(R.id.razaTextField);
        razaAutoComplete = findViewById(R.id.razaAutoComplete);
        sexoRadioGroup = findViewById(R.id.sexoRadioGroup);
        fechaNacimientoTextField = findViewById(R.id.fechaNacimientoTextField);
        fechaNacimientoEditText = findViewById(R.id.fechaNacimientoEditText);
        tamanoChipGroup = findViewById(R.id.tamanoChipGroup);
        pesoTextField = findViewById(R.id.pesoTextField);
        siguienteButton = findViewById(R.id.siguienteButton);
        elegirGaleriaButton = findViewById(R.id.elegirGaleriaButton);
        tomarFotoButton = findViewById(R.id.tomarFotoButton);

        setupLaunchers(); // Setup launchers first
        setupListeners();
        setupRazaSpinner();
        validateInputs(); // Call initially to set button state
    }

    private void setupListeners() {
        arrowBack.setOnClickListener(v -> finish());

        fechaNacimientoEditText.setOnClickListener(v -> showDatePickerDialog());

        elegirGaleriaButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        tomarFotoButton.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        siguienteButton.setOnClickListener(v -> {
            if (validateFields()) {
                collectDataAndProceed();
            }
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
        };

        nombreTextField.getEditText().addTextChangedListener(textWatcher);
        pesoTextField.getEditText().addTextChangedListener(textWatcher);
        razaAutoComplete.addTextChangedListener(textWatcher);
        fechaNacimientoEditText.addTextChangedListener(textWatcher);

        sexoRadioGroup.setOnCheckedChangeListener((group, checkedId) -> validateInputs());
        tamanoChipGroup.setOnCheckedChangeListener((group, checkedId) -> validateInputs());
    }

    private void setupRazaSpinner() {
        String[] razas = getResources().getStringArray(R.array.razas_perros);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, razas);
        razaAutoComplete.setAdapter(adapter);
    }

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        fotoUri = result.getData().getData();
                        fotoPrincipalImageView.setImageURI(fotoUri);
                        validateInputs();
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success) {
                        fotoPrincipalImageView.setImageURI(fotoUri);
                        validateInputs();
                    }
                });

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission is granted. Continue the action
                launchCamera();
            } else {
                // Explain to the user that the feature is unavailable
                Toast.makeText(this, "El permiso de la cámara es necesario para tomar una foto.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted
            launchCamera();
        } else {
            // Directly ask for the permission
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        fotoUri = createTempImageUri();
        if (fotoUri != null) {
            cameraLauncher.launch(fotoUri);
        } else {
            Toast.makeText(this, "No se pudo crear el archivo para la foto", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String selectedDate = String.format(Locale.US, "%02d/%02d/%d", dayOfMonth, monthOfYear + 1, year1);
                    fechaNacimientoEditText.setText(selectedDate);
                }, year, month, day);
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // Cannot select a future date
        datePickerDialog.show();
    }

    private Uri createTempImageUri() {
        try {
            File sharedDir = new File(getFilesDir(), "shared_files");
            if (!sharedDir.exists()) {
                sharedDir.mkdirs();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
            File photoFile = new File(sharedDir, "MASCOTA_" + timeStamp + ".jpg");
            return FileProvider.getUriForFile(this, com.mjc.mascotalink.BuildConfig.APPLICATION_ID + ".provider", photoFile);
        } catch (Exception e) {
            // Log the error
            return null;
        }
    }

    private void validateInputs() {
        boolean allFilled = !nombreTextField.getEditText().getText().toString().trim().isEmpty()
                && !razaAutoComplete.getText().toString().trim().isEmpty()
                && !razaAutoComplete.getText().toString().equals(getString(R.string.raza_mascota_prompt))
                && sexoRadioGroup.getCheckedRadioButtonId() != -1
                && !fechaNacimientoEditText.getText().toString().trim().isEmpty()
                && tamanoChipGroup.getCheckedChipId() != View.NO_ID
                && !pesoTextField.getEditText().getText().toString().trim().isEmpty()
                && fotoUri != null;
        siguienteButton.setEnabled(allFilled);
    }

    private boolean validateFields() {
        boolean isValid = true;
        if (nombreTextField.getEditText().getText().toString().trim().isEmpty()) {
            nombreTextField.setError("El nombre no puede estar vacío");
            isValid = false;
        } else {
            nombreTextField.setError(null);
        }

        if (razaAutoComplete.getText().toString().trim().isEmpty() || razaAutoComplete.getText().toString().equals(getString(R.string.raza_mascota_prompt))) {
            razaTextField.setError("Debes seleccionar una raza");
            isValid = false;
        } else {
            razaTextField.setError(null);
        }

        if (fechaNacimientoEditText.getText().toString().trim().isEmpty()) {
            fechaNacimientoTextField.setError("Selecciona una fecha de nacimiento");
            isValid = false;
        } else {
            fechaNacimientoTextField.setError(null);
        }

        if (pesoTextField.getEditText().getText().toString().trim().isEmpty()) {
            pesoTextField.setError("Ingresa el peso de la mascota");
            isValid = false;
        } else {
            pesoTextField.setError(null);
        }

        if (fotoUri == null) {
            Toast.makeText(this, "Debes seleccionar una foto", Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        return isValid;
    }

    private void collectDataAndProceed() {
        Intent intent = new Intent(this, MascotaRegistroPaso2Activity.class);

        if (getIntent().hasExtra("FROM_RESERVA")) {
            intent.putExtra("FROM_RESERVA", getIntent().getBooleanExtra("FROM_RESERVA", false));
        }

        // Basic Info
        intent.putExtra("nombre", nombreTextField.getEditText().getText().toString().trim());
        intent.putExtra("raza", razaAutoComplete.getText().toString());

        int selectedSexoId = sexoRadioGroup.getCheckedRadioButtonId();
        RadioButton sexoRadioButton = findViewById(selectedSexoId);
        intent.putExtra("sexo", sexoRadioButton.getText().toString());

        String fechaNacimientoStr = fechaNacimientoEditText.getText().toString();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date date = sdf.parse(fechaNacimientoStr);
            if (date != null) {
                intent.putExtra("fecha_nacimiento", date.getTime());
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        int selectedTamanoId = tamanoChipGroup.getCheckedChipId();
        Chip tamanoChip = findViewById(selectedTamanoId);
        intent.putExtra("tamano", tamanoChip.getText().toString());

        intent.putExtra("peso", Double.parseDouble(pesoTextField.getEditText().getText().toString()));
        intent.putExtra("foto_uri", fotoUri.toString());

        startActivity(intent);
    }
}