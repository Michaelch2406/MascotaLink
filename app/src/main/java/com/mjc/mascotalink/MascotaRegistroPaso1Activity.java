package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;

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
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.mjc.mascotalink.utils.InputUtils;

public class MascotaRegistroPaso1Activity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistroPaso1";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long RATE_LIMIT_MS = 1000;

    private ImageView fotoPrincipalImageView;
    private TextInputLayout nombreTextField, pesoTextField, fechaNacimientoTextField, razaTextField;
    private AutoCompleteTextView razaAutoComplete;
    private RadioGroup sexoRadioGroup;
    private ChipGroup tamanoChipGroup;
    private Button siguienteButton, elegirGaleriaButton, tomarFotoButton;
    private TextInputEditText fechaNacimientoEditText;
    private NestedScrollView scrollView;

    private Uri fotoUri;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private TextWatcher validationTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        scrollView = findViewById(R.id.scroll_view);
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

        setupInputFilters(); // Setup input filters
        setupLaunchers(); // Setup launchers first
        setupListeners();
        setupRazaSpinner();
        validateInputs(); // Call initially to set button state
    }

    private void setupInputFilters() {
        // InputFilter para el nombre de la mascota - solo letras y espacios
        InputFilter letterFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isLetter(c) && !Character.isSpaceChar(c)) {
                    return "";
                }
            }
            return null;
        };
        nombreTextField.getEditText().setFilters(new InputFilter[]{letterFilter, new InputFilter.LengthFilter(50)});
    }

    private void setupListeners() {
        fechaNacimientoEditText.setOnClickListener(v -> showDatePickerDialog());

        elegirGaleriaButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        tomarFotoButton.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        siguienteButton.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            if (validateFields()) {
                collectDataAndProceed();
            }
        }));

        validationTextWatcher = InputUtils.createDebouncedTextWatcher(
            "validacion_mascota_paso1",
            DEBOUNCE_DELAY_MS,
            text -> validateInputs()
        );

        nombreTextField.getEditText().addTextChangedListener(validationTextWatcher);
        pesoTextField.getEditText().addTextChangedListener(validationTextWatcher);
        razaAutoComplete.addTextChangedListener(validationTextWatcher);
        fechaNacimientoEditText.addTextChangedListener(validationTextWatcher);

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
                        Uri selectedUri = result.getData().getData();
                        if (InputUtils.isValidImageFile(this, selectedUri, 5 * 1024 * 1024)) {
                            fotoUri = selectedUri;
                            fotoPrincipalImageView.setImageURI(fotoUri);
                            validateInputs();
                        } else {
                            Toast.makeText(this, "La imagen no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                        }
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success) {
                        if (InputUtils.isValidImageFile(this, fotoUri, 5 * 1024 * 1024)) {
                            fotoPrincipalImageView.setImageURI(fotoUri);
                            validateInputs();
                        } else {
                            Toast.makeText(this, "La imagen no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                            fotoUri = null;
                        }
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
        View firstErrorView = null;

        String nombre = nombreTextField.getEditText().getText().toString().trim();
        if (nombre.isEmpty()) {
            nombreTextField.setError("El nombre no puede estar vacío");
            if (firstErrorView == null) firstErrorView = nombreTextField;
            isValid = false;
        } else if (!InputUtils.isValidName(nombre, 2, 50)) {
            nombreTextField.setError("Nombre inválido (solo letras, 2-50 caracteres)");
            if (firstErrorView == null) firstErrorView = nombreTextField;
            isValid = false;
        } else {
            nombreTextField.setError(null);
        }

        if (razaAutoComplete.getText().toString().trim().isEmpty() || razaAutoComplete.getText().toString().equals(getString(R.string.raza_mascota_prompt))) {
            razaTextField.setError("Debes seleccionar una raza");
            if (firstErrorView == null) firstErrorView = razaTextField;
            isValid = false;
        } else {
            razaTextField.setError(null);
        }

        if (fechaNacimientoEditText.getText().toString().trim().isEmpty()) {
            fechaNacimientoTextField.setError("Selecciona una fecha de nacimiento");
            if (firstErrorView == null) firstErrorView = fechaNacimientoTextField;
            isValid = false;
        } else {
            fechaNacimientoTextField.setError(null);
        }

        if (pesoTextField.getEditText().getText().toString().trim().isEmpty()) {
            pesoTextField.setError("Ingresa el peso de la mascota");
            if (firstErrorView == null) firstErrorView = pesoTextField;
            isValid = false;
        } else {
            pesoTextField.setError(null);
        }

        if (fotoUri == null) {
            Toast.makeText(this, "Debes seleccionar una foto", Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        // Scroll to first error
        if (!isValid && firstErrorView != null) {
            scrollToView(firstErrorView);
        }

        return isValid;
    }

    private void scrollToView(View view) {
        if (scrollView != null && view != null) {
            scrollView.post(() -> {
                int scrollY = view.getTop() - 100;
                scrollView.smoothScrollTo(0, scrollY);
                view.requestFocus();
            });
        }
    }

    private void collectDataAndProceed() {
        // Ocultar teclado antes de procesar
        InputUtils.hideKeyboard(this);
        InputUtils.setButtonLoading(siguienteButton, true, "Procesando...");

        try {
            Intent intent = new Intent(this, MascotaRegistroPaso2Activity.class);

            if (getIntent().hasExtra("FROM_RESERVA")) {
                intent.putExtra("FROM_RESERVA", getIntent().getBooleanExtra("FROM_RESERVA", false));
            }

            // Sanitizar inputs
            String nombreSanitizado = InputUtils.sanitizeInput(nombreTextField.getEditText().getText().toString().trim());
            String razaSanitizada = InputUtils.sanitizeInput(razaAutoComplete.getText().toString());

            intent.putExtra("nombre", nombreSanitizado);
            intent.putExtra("raza", razaSanitizada);

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
                Log.e(TAG, "Error al parsear fecha de nacimiento", e);
                Toast.makeText(this, "Error en el formato de fecha", Toast.LENGTH_SHORT).show();
                InputUtils.setButtonLoading(siguienteButton, false);
                return;
            }

            int selectedTamanoId = tamanoChipGroup.getCheckedChipId();
            Chip tamanoChip = findViewById(selectedTamanoId);
            intent.putExtra("tamano", tamanoChip.getText().toString());

            intent.putExtra("peso", Double.parseDouble(pesoTextField.getEditText().getText().toString()));
            intent.putExtra("foto_uri", fotoUri.toString());

            Log.d(TAG, "Datos del paso 1 recolectados correctamente");
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error al recolectar datos", e);
            Toast.makeText(this, "Error al procesar los datos. Intenta nuevamente.", Toast.LENGTH_SHORT).show();
            InputUtils.setButtonLoading(siguienteButton, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (validationTextWatcher != null) {
            if (nombreTextField != null && nombreTextField.getEditText() != null) {
                nombreTextField.getEditText().removeTextChangedListener(validationTextWatcher);
            }
            if (pesoTextField != null && pesoTextField.getEditText() != null) {
                pesoTextField.getEditText().removeTextChangedListener(validationTextWatcher);
            }
            if (razaAutoComplete != null) {
                razaAutoComplete.removeTextChangedListener(validationTextWatcher);
            }
            if (fechaNacimientoEditText != null) {
                fechaNacimientoEditText.removeTextChangedListener(validationTextWatcher);
            }
        }

        // Cancelar debounces pendientes
        InputUtils.cancelDebounce("validacion_mascota_paso1");

        Log.d(TAG, "Activity destruida y recursos limpiados");
    }
}