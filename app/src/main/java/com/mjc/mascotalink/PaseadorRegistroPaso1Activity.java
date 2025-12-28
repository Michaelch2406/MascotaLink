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
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

    private EditText etNombre, etApellido, etCedula, etFechaNac, etDomicilio, etTelefono, etEmail, etPassword;
    private TextInputLayout tilPassword;
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

    // Usar InputUtils para debouncing y rate limiting
    private static final String DEBOUNCE_KEY = "paso1_save";
    private final InputUtils.RateLimiter rateLimiter = new InputUtils.RateLimiter(1000);

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
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etCedula = findViewById(R.id.et_cedula);
        etFechaNac = findViewById(R.id.et_fecha_nacimiento);
        etDomicilio = findViewById(R.id.et_domicilio);
        etTelefono = findViewById(R.id.et_telefono);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        tilPassword = findViewById(R.id.til_password);
        cbAceptaTerminos = findViewById(R.id.cb_acepta_terminos);
        btnContinuar = findViewById(R.id.btn_continuar);
        ivGeolocate = findViewById(R.id.iv_geolocate);
        pbGeolocate = findViewById(R.id.pb_geolocate);
    }

    private void setupListeners() {
        btnContinuar.setOnClickListener(v -> onContinuar());
        etFechaNac.setOnClickListener(v -> openDatePicker());
        etDomicilio.setOnClickListener(v -> launchAutocomplete());
        ivGeolocate.setOnClickListener(v -> onGeolocateClick());

        findViewById(R.id.tv_login_link).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // TextWatcher con debouncing usando InputUtils
        generalTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateButtonEnabled();
                // Usar InputUtils.debounce() para evitar guardar en cada tecla
                InputUtils.debounce(DEBOUNCE_KEY, DEBOUNCE_DELAY_MS,
                    PaseadorRegistroPaso1Activity.this::saveState);
            }
        };

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
        // Rate limiting usando InputUtils.RateLimiter
        if (!rateLimiter.shouldProcess()) {
            return;
        }

        if (!validarCamposPantalla1()) {
            Toast.makeText(this, "Por favor corrige los errores antes de continuar", Toast.LENGTH_SHORT).show();
            return;
        }

        btnContinuar.setEnabled(false);
        guardarDatosCompletos();
        startActivity(new Intent(this, PaseadorRegistroPaso2Activity.class));
        btnContinuar.setEnabled(true);
    }

    private void guardarDatosCompletos() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();

        // Sanitizar entradas usando InputUtils para prevenir ataques XSS/injection
        editor.putString("nombre", InputUtils.sanitizeInput(etNombre.getText().toString().trim()));
        editor.putString("apellido", InputUtils.sanitizeInput(etApellido.getText().toString().trim()));
        editor.putString("cedula", etCedula.getText().toString().trim());
        editor.putString("fecha_nacimiento", etFechaNac.getText().toString().trim());
        editor.putString("domicilio", InputUtils.sanitizeInput(domicilio));
        if (domicilioLatLng != null) {
            editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
            editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
        }
        editor.putString("telefono", etTelefono.getText().toString().trim());
        editor.putString("email", etEmail.getText().toString().trim().toLowerCase());

        // SEGURIDAD: Almacenar contraseña encriptada
        String password = etPassword.getText().toString().trim();
        if (encryptedPrefs != null && !password.isEmpty()) {
            encryptedPrefs.putString(PREFS + "_password", password);
        }
        editor.putString("password", password);

        editor.putBoolean("paso1_completo", true);
        editor.putBoolean("acepto_terminos", cbAceptaTerminos.isChecked());
        editor.apply();
    }

    private boolean validarCamposPantalla1() {
        boolean ok = true;
        if (TextUtils.isEmpty(etNombre.getText().toString().trim())) ok = false;
        if (TextUtils.isEmpty(etApellido.getText().toString().trim())) ok = false;
        if (!validarCedulaEcuador(etCedula.getText().toString().trim())) ok = false;
        Date fecha = parseFecha(etFechaNac.getText().toString().trim());
        if (fecha == null || !validarEdad(fecha)) ok = false;
        if (TextUtils.isEmpty(domicilio)) ok = false;
        if (!etTelefono.getText().toString().trim().matches("^(\\+593[0-9]{9}|09[0-9]{8})$")) ok = false;
        if (!Patterns.EMAIL_ADDRESS.matcher(etEmail.getText().toString().trim()).matches()) ok = false;
        if (etPassword.getText().toString().trim().length() < 6) ok = false;
        if (!cbAceptaTerminos.isChecked()) ok = false;
        return ok;
    }

    private boolean validarEdad(Date nacimiento) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -18);
        return nacimiento.before(cal.getTime());
    }

    private boolean validarCedulaEcuador(String cedula) {
        if (!cedula.matches("\\d{10}")) return false;
        try {
            int provincia = Integer.parseInt(cedula.substring(0, 2));
            if (provincia < 1 || provincia > 24) return false;
            int[] coeficientes = {2, 1, 2, 1, 2, 1, 2, 1, 2};
            int suma = 0;
            for (int i = 0; i < 9; i++) {
                int producto = Character.getNumericValue(cedula.charAt(i)) * coeficientes[i];
                suma += (producto >= 10) ? producto - 9 : producto;
            }
            int digitoVerificador = (suma % 10 == 0) ? 0 : (10 - (suma % 10));
            return digitoVerificador == Character.getNumericValue(cedula.charAt(9));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString("nombre", InputUtils.sanitizeInput(etNombre.getText().toString()));
        editor.putString("apellido", InputUtils.sanitizeInput(etApellido.getText().toString()));
        editor.putString("cedula", etCedula.getText().toString());
        editor.putString("fecha_nacimiento", etFechaNac.getText().toString());
        editor.putString("domicilio", InputUtils.sanitizeInput(domicilio));
        if (domicilioLatLng != null) {
            editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
            editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
        }
        editor.putString("telefono", etTelefono.getText().toString());
        editor.putString("email", etEmail.getText().toString());
        editor.putString("password", etPassword.getText().toString());
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
