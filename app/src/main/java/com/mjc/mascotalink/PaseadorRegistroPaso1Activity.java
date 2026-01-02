package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.GeoPoint;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.utils.InputUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaseadorRegistroPaso1Activity extends AppCompatActivity {

    private static final String TAG = "Paso1Paseador";
    private static final String PREFS = "WizardPaseador";
    private static final long DEBOUNCE_DELAY_MS = 500;

    private NestedScrollView scrollView;
    private TextInputLayout tilNombre, tilApellido, tilCedula, tilFechaNacimiento, tilDomicilio, tilTelefono, tilEmail, tilPassword;
    private EditText etNombre, etApellido, etCedula, etFechaNac, etDomicilio, etTelefono, etEmail, etPassword;
    private CheckBox cbAceptaTerminos;
    private Button btnContinuar;
    private ImageView ivGeolocate;
    private ProgressBar pbGeolocate;

    private String domicilio;
    private GeoPoint domicilioLatLng;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    // Prevención de memory leaks: almacenar referencias de TextWatchers
    private TextWatcher generalTextWatcher;
    private EncryptedPreferencesHelper encryptedPrefs;
    private static final String DEBOUNCE_KEY = "paso1_save";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        encryptedPrefs = EncryptedPreferencesHelper.getInstance(this);

        bindViews();
        setupListeners();
        setupLocationServices();
        setupAutocompleteLauncher();
        configurarTerminosYCondiciones();
        loadSavedState(); // Cargar al final para que el texto del domicilio se setee
        updateButtonEnabled();
    }

    private void bindViews() {
        scrollView = findViewById(R.id.scroll);

        tilNombre = findViewById(R.id.til_nombre);
        tilApellido = findViewById(R.id.til_apellido);
        tilCedula = findViewById(R.id.til_cedula);
        tilFechaNacimiento = findViewById(R.id.til_fecha_nacimiento);
        tilDomicilio = findViewById(R.id.til_domicilio);
        tilTelefono = findViewById(R.id.til_telefono);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);

        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etCedula = findViewById(R.id.et_cedula);
        etFechaNac = findViewById(R.id.et_fecha_nacimiento);
        etDomicilio = findViewById(R.id.et_domicilio);
        etTelefono = findViewById(R.id.et_telefono);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);

        cbAceptaTerminos = findViewById(R.id.cb_acepta_terminos);
        btnContinuar = findViewById(R.id.btn_continuar);
        ivGeolocate = findViewById(R.id.iv_geolocate);
        pbGeolocate = findViewById(R.id.pb_geolocate);

        setupInputFilters();
    }

    private void setupInputFilters() {
        // InputFilter para nombres - solo letras y espacios
        InputFilter letterFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isLetter(c) && !Character.isSpaceChar(c)) {
                    return "";
                }
            }
            return null;
        };

        etNombre.setFilters(new InputFilter[]{letterFilter, new InputFilter.LengthFilter(50)});
        etApellido.setFilters(new InputFilter[]{letterFilter, new InputFilter.LengthFilter(50)});
    }

    private void setupListeners() {
        // Usar SafeClickListener para el botón continuar (rate limiting integrado)
        btnContinuar.setOnClickListener(InputUtils.createSafeClickListener(v -> onContinuar()));
        etFechaNac.setOnClickListener(v -> openDatePicker());
        etDomicilio.setOnClickListener(v -> launchAutocomplete());

        // Click en el endIcon de domicilio para geolocalizar
        tilDomicilio.setEndIconOnClickListener(v -> onGeolocateClick());

        findViewById(R.id.tv_login_link).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // TextWatcher simplificado con debouncing integrado usando InputUtils
        generalTextWatcher = InputUtils.createSimpleTextWatcher(text -> {
            updateButtonEnabled();
            InputUtils.debounce(DEBOUNCE_KEY, DEBOUNCE_DELAY_MS, this::saveState);
        });

        etNombre.addTextChangedListener(generalTextWatcher);
        etApellido.addTextChangedListener(generalTextWatcher);
        etCedula.addTextChangedListener(generalTextWatcher);
        etTelefono.addTextChangedListener(generalTextWatcher);
        etEmail.addTextChangedListener(generalTextWatcher);
        etPassword.addTextChangedListener(generalTextWatcher);
        cbAceptaTerminos.setOnCheckedChangeListener((v, isChecked) -> updateButtonEnabled());
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
                        domicilio = place.getAddress();
                        if (place.getLatLng() != null) {
                            domicilioLatLng = new GeoPoint(place.getLatLng().latitude, place.getLatLng().longitude);
                        }
                        etDomicilio.setText(domicilio);
                        saveState();
                        updateButtonEnabled();
                    } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                        Status status = Autocomplete.getStatusFromIntent(result.getData());
                        Log.e(TAG, "Autocomplete error: " + status.getStatusMessage());
                    }
                });
    }

    private void launchAutocomplete() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("EC")
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
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String addressLine = addresses.get(0).getAddressLine(0);
                        domicilio = addressLine;
                        domicilioLatLng = new GeoPoint(location.getLatitude(), location.getLongitude());
                        etDomicilio.setText(domicilio);
                        saveState();
                        updateButtonEnabled();
                        Toast.makeText(this, "Dirección autocompletada.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No se pudo encontrar una dirección.", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Geocoder service not available", e);
                }
            }
            showGeolocateLoading(false);
        }).addOnFailureListener(e -> {
            showGeolocateLoading(false);
            Toast.makeText(this, "No se pudo obtener la ubicación.", Toast.LENGTH_LONG).show();
        });
    }

    private void showGeolocateLoading(boolean isLoading) {
        pbGeolocate.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        ivGeolocate.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    private void openDatePicker() {
        etFechaNac.setInputType(InputType.TYPE_NULL);
        Calendar now = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
            String txt = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y);
            etFechaNac.setText(txt);
            saveState();
            updateButtonEnabled();
        }, now.get(Calendar.YEAR) - 18, now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));

        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -18);
        dlg.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        dlg.show();
    }

    private void configurarTerminosYCondiciones() {
        String textoTerminos = "Acepto los <a href='#'>Términos y Condiciones</a> y la <a href='#'>Política de Privacidad</a>";
        cbAceptaTerminos.setText(Html.fromHtml(textoTerminos, Html.FROM_HTML_MODE_LEGACY));
        cbAceptaTerminos.setMovementMethod(LinkMovementMethod.getInstance());
        cbAceptaTerminos.setOnClickListener(v -> {
            if (!cbAceptaTerminos.isChecked()) {
                mostrarDialogoTerminos();
            }
        });
    }

    private void mostrarDialogoTerminos() {
        new AlertDialog.Builder(this)
            .setTitle("Términos y Condiciones")
            .setMessage(getTextoTerminosCompleto())
            .setPositiveButton("Aceptar", (dialog, which) -> {
                cbAceptaTerminos.setChecked(true);
                updateButtonEnabled();
            })
            .setNegativeButton("Cancelar", (dialog, which) -> cbAceptaTerminos.setChecked(false))
            .setCancelable(false)
            .show();
    }

    private String getTextoTerminosCompleto() {
        return "TÉRMINOS Y CONDICIONES DE USO - Walki\n\n" +
                "1. ACEPTACIÓN DE TÉRMINOS\n" +
                "Al registrarte como paseador en Walki, aceptas cumplir con estos términos y condiciones.\n\n" +
                "2. RESPONSABILIDADES DEL PASEADOR\n" +
                "- Cuidar la seguridad y bienestar de las mascotas bajo tu responsabilidad\n" +
                "- Seguir las instrucciones específicas del dueño de la mascota\n" +
                "- Reportar cualquier incidente o emergencia inmediatamente\n" +
                "- Mantener actualizados tus documentos de verificación\n\n" +
                "3. VERIFICACIÓN Y DOCUMENTOS\n" +
                "- Proporcionar documentación veraz y actualizada\n" +
                "- Someterte al proceso de verificación de antecedentes\n" +
                "- Mantener vigentes los certificados médicos requeridos\n\n" +
                "4. CÓDIGO DE CONDUCTA\n" +
                "- Tratar a las mascotas con respeto y cuidado\n" +
                "- Comunicarte de manera profesional con los dueños\n" +
                "- Seguir todas las normas de seguridad establecidas\n\n" +
                "5. PRIVACIDAD Y DATOS\n" +
                "- Proteger la información personal de los clientes\n" +
                "- No compartir datos de contacto fuera de la plataforma\n" +
                "- Respetar la privacidad de los hogares visitados\n\n" +
                "6. TERMINACIÓN DEL SERVICIO\n" +
                "Walki se reserva el derecho de suspender o terminar el acceso del paseador en caso de incumplimiento de estos términos.\n\n" +
                "Al marcar esta casilla, confirmas que has leído, entendido y aceptas cumplir con todos estos términos y condiciones.\n\n" +
                "Fecha de última actualización: Septiembre 2025";
    }

    private void onContinuar() {
        // Rate limiting ya integrado en SafeClickListener
        if (!validarCamposDetallado()) {
            return;
        }

        // Ocultar teclado antes de procesar
        InputUtils.hideKeyboard(this);
        InputUtils.setButtonLoading(btnContinuar, true, "Procesando...");

        guardarDatosCompletos();
        startActivity(new Intent(this, PaseadorRegistroPaso2Activity.class));
        InputUtils.setButtonLoading(btnContinuar, false);
    }

    private void guardarDatosCompletos() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();

        // Sanitizar y capitalizar nombres usando InputUtils
        String nombre = InputUtils.capitalizeWords(etNombre.getText().toString());
        String apellido = InputUtils.capitalizeWords(etApellido.getText().toString());

        editor.putString("nombre", InputUtils.sanitizeInput(nombre));
        editor.putString("apellido", InputUtils.sanitizeInput(apellido));
        editor.putString("cedula", InputUtils.trimSafe(etCedula.getText().toString()));
        editor.putString("fecha_nacimiento", InputUtils.trimSafe(etFechaNac.getText().toString()));
        editor.putString("domicilio", InputUtils.sanitizeInput(domicilio));

        if (domicilioLatLng != null) {
            editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
            editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
        }

        // Formatear teléfono a formato internacional
        editor.putString("telefono", InputUtils.formatTelefonoEcuador(etTelefono.getText().toString()));
        editor.putString("email", InputUtils.trimSafe(etEmail.getText().toString()).toLowerCase());

        // SEGURIDAD: Almacenar contraseña encriptada
        String password = InputUtils.trimSafe(etPassword.getText().toString());
        if (encryptedPrefs != null && !password.isEmpty()) {
            encryptedPrefs.putString(PREFS + "_password", password);
        }
        editor.putString("password", password);

        editor.putBoolean("paso1_completo", true);
        editor.putBoolean("acepto_terminos", cbAceptaTerminos.isChecked());
        editor.apply();
    }

    private boolean validarCamposPantalla1() {
        // Usar InputUtils para todas las validaciones
        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        if (!InputUtils.isValidName(nombre, 2, 50)) return false;
        if (!InputUtils.isValidName(apellido, 2, 50)) return false;
        if (!InputUtils.isValidCedulaEcuador(etCedula.getText().toString().trim())) return false;
        Date fecha = parseFecha(etFechaNac.getText().toString().trim());
        if (fecha == null || !validarEdad(fecha)) return false;
        if (!InputUtils.isNotEmpty(domicilio)) return false;
        if (!InputUtils.isValidTelefonoEcuador(etTelefono.getText().toString())) return false;
        if (!InputUtils.isValidEmail(etEmail.getText().toString())) return false;
        if (!InputUtils.isValidPassword(etPassword.getText().toString())) return false;
        if (!cbAceptaTerminos.isChecked()) return false;
        return true;
    }

    private boolean validarCamposDetallado() {
        boolean isValid = true;
        View firstErrorView = null;

        // Validar nombre
        String nombre = etNombre.getText().toString().trim();
        if (nombre.isEmpty()) {
            tilNombre.setError("El nombre no puede estar vacío");
            if (firstErrorView == null) firstErrorView = tilNombre;
            isValid = false;
        } else if (!InputUtils.isValidName(nombre, 2, 50)) {
            tilNombre.setError("Nombre inválido (solo letras, 2-50 caracteres)");
            if (firstErrorView == null) firstErrorView = tilNombre;
            isValid = false;
        } else {
            tilNombre.setError(null);
        }

        // Validar apellido
        String apellido = etApellido.getText().toString().trim();
        if (apellido.isEmpty()) {
            tilApellido.setError("El apellido no puede estar vacío");
            if (firstErrorView == null) firstErrorView = tilApellido;
            isValid = false;
        } else if (!InputUtils.isValidName(apellido, 2, 50)) {
            tilApellido.setError("Apellido inválido (solo letras, 2-50 caracteres)");
            if (firstErrorView == null) firstErrorView = tilApellido;
            isValid = false;
        } else {
            tilApellido.setError(null);
        }

        // Validar cédula
        String cedula = etCedula.getText().toString().trim();
        if (cedula.isEmpty()) {
            tilCedula.setError("La cédula no puede estar vacía");
            if (firstErrorView == null) firstErrorView = tilCedula;
            isValid = false;
        } else if (!InputUtils.isValidCedulaEcuador(cedula)) {
            tilCedula.setError("Cédula inválida (10 dígitos válidos)");
            if (firstErrorView == null) firstErrorView = tilCedula;
            isValid = false;
        } else {
            tilCedula.setError(null);
        }

        // Validar fecha de nacimiento
        String fechaStr = etFechaNac.getText().toString().trim();
        if (fechaStr.isEmpty()) {
            tilFechaNacimiento.setError("Selecciona tu fecha de nacimiento");
            if (firstErrorView == null) firstErrorView = tilFechaNacimiento;
            isValid = false;
        } else {
            Date fecha = parseFecha(fechaStr);
            if (fecha == null || !validarEdad(fecha)) {
                tilFechaNacimiento.setError("Debes ser mayor de 18 años");
                if (firstErrorView == null) firstErrorView = tilFechaNacimiento;
                isValid = false;
            } else {
                tilFechaNacimiento.setError(null);
            }
        }

        // Validar domicilio
        if (domicilio == null || domicilio.trim().isEmpty()) {
            tilDomicilio.setError("Ingresa tu domicilio");
            if (firstErrorView == null) firstErrorView = tilDomicilio;
            isValid = false;
        } else {
            tilDomicilio.setError(null);
        }

        // Validar teléfono
        String telefono = etTelefono.getText().toString().trim();
        if (telefono.isEmpty()) {
            tilTelefono.setError("El teléfono no puede estar vacío");
            if (firstErrorView == null) firstErrorView = tilTelefono;
            isValid = false;
        } else if (!InputUtils.isValidTelefonoEcuador(telefono)) {
            tilTelefono.setError("Teléfono inválido (10 dígitos)");
            if (firstErrorView == null) firstErrorView = tilTelefono;
            isValid = false;
        } else {
            tilTelefono.setError(null);
        }

        // Validar email
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            tilEmail.setError("El email no puede estar vacío");
            if (firstErrorView == null) firstErrorView = tilEmail;
            isValid = false;
        } else if (!InputUtils.isValidEmail(email)) {
            tilEmail.setError("Email inválido");
            if (firstErrorView == null) firstErrorView = tilEmail;
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Validar contraseña
        String password = etPassword.getText().toString().trim();
        if (password.isEmpty()) {
            tilPassword.setError("La contraseña no puede estar vacía");
            if (firstErrorView == null) firstErrorView = tilPassword;
            isValid = false;
        } else if (!InputUtils.isValidPassword(password)) {
            tilPassword.setError("Contraseña inválida (mínimo 6 caracteres)");
            if (firstErrorView == null) firstErrorView = tilPassword;
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        // Validar términos
        if (!cbAceptaTerminos.isChecked()) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        // Scroll al primer error
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

    private boolean validarEdad(Date nacimiento) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -18);
        return nacimiento.before(cal.getTime());
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString("nombre", InputUtils.sanitizeInput(etNombre.getText().toString()));
        editor.putString("apellido", InputUtils.sanitizeInput(etApellido.getText().toString()));
        editor.putString("cedula", InputUtils.trimSafe(etCedula.getText().toString()));
        editor.putString("fecha_nacimiento", InputUtils.trimSafe(etFechaNac.getText().toString()));
        editor.putString("domicilio", InputUtils.sanitizeInput(domicilio));
        if (domicilioLatLng != null) {
            editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
            editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
        }
        editor.putString("telefono", InputUtils.trimSafe(etTelefono.getText().toString()));
        editor.putString("email", InputUtils.trimSafe(etEmail.getText().toString()));
        editor.putString("password", InputUtils.trimSafe(etPassword.getText().toString()));
        editor.apply();
    }

    private void loadSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etNombre.setText(prefs.getString("nombre", ""));
        etApellido.setText(prefs.getString("apellido", ""));
        etCedula.setText(prefs.getString("cedula", ""));
        etFechaNac.setText(prefs.getString("fecha_nacimiento", ""));
        domicilio = prefs.getString("domicilio", "");
        etDomicilio.setText(domicilio);
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if(lat != 0 && lng != 0) {
            domicilioLatLng = new GeoPoint(lat, lng);
        }
        etTelefono.setText(prefs.getString("telefono", ""));
        etEmail.setText(prefs.getString("email", ""));
        etPassword.setText(prefs.getString("password", ""));
        cbAceptaTerminos.setChecked(prefs.getBoolean("acepto_terminos", false));
    }

    private void updateButtonEnabled() {
        boolean camposCompletos = validarCamposPantalla1();
        btnContinuar.setEnabled(camposCompletos);
        btnContinuar.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, 
                camposCompletos ? R.color.blue_primary : R.color.gray_disabled)));
    }

    private Date parseFecha(String fechaStr) {
        try {
            return sdf.parse(fechaStr);
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (generalTextWatcher != null) {
            if (etNombre != null) etNombre.removeTextChangedListener(generalTextWatcher);
            if (etApellido != null) etApellido.removeTextChangedListener(generalTextWatcher);
            if (etCedula != null) etCedula.removeTextChangedListener(generalTextWatcher);
            if (etTelefono != null) etTelefono.removeTextChangedListener(generalTextWatcher);
            if (etEmail != null) etEmail.removeTextChangedListener(generalTextWatcher);
            if (etPassword != null) etPassword.removeTextChangedListener(generalTextWatcher);
        }

        // Cancelar debounce pendiente usando InputUtils
        InputUtils.cancelDebounce(DEBOUNCE_KEY);
    }
}
