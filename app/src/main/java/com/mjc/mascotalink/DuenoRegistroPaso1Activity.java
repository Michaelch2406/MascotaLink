package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
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
import com.google.android.material.textfield.TextInputEditText;
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

public class DuenoRegistroPaso1Activity extends AppCompatActivity {

    private static final String TAG = "DuenoRegistroPaso1";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long RATE_LIMIT_MS = 1000;

    private EditText etNombre, etApellido, etFechaNacimiento, etTelefono, etCorreo, etCedula, etDomicilio;
    private TextInputLayout tilPassword;
    private TextInputEditText etPassword;
    private CheckBox cbTerminos;
    private Button btnRegistrarse;
    private ImageView ivGeolocate;
    private ProgressBar pbGeolocate;

    private String domicilio;
    private GeoPoint domicilioLatLng;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    private TextWatcher validationTextWatcher;
    private final InputUtils.RateLimiter rateLimiter = new InputUtils.RateLimiter(RATE_LIMIT_MS);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso1);

        setupViews();
        setupListeners();
        setupLocationServices();
        setupAutocompleteLauncher();
        configurarTerminosYCondiciones();
        actualizarBoton();
    }

    private void setupViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etFechaNacimiento = findViewById(R.id.et_fecha_nacimiento);
        etTelefono = findViewById(R.id.et_telefono);
        etCorreo = findViewById(R.id.et_correo);
        etCedula = findViewById(R.id.et_cedula);
        etDomicilio = findViewById(R.id.et_domicilio);
        tilPassword = findViewById(R.id.til_password);
        etPassword = findViewById(R.id.et_password);
        cbTerminos = findViewById(R.id.cb_terminos);
        btnRegistrarse = findViewById(R.id.btn_registrarse);
        ivGeolocate = findViewById(R.id.iv_geolocate);
        pbGeolocate = findViewById(R.id.pb_geolocate);
    }

    private void setupListeners() {
        btnRegistrarse.setOnClickListener(v -> {
            if (rateLimiter.shouldProcess()) {
                intentarRegistro();
            }
        });
        etFechaNacimiento.setOnClickListener(v -> mostrarDatePicker());
        etDomicilio.setOnClickListener(v -> launchAutocomplete());
        ivGeolocate.setOnClickListener(v -> onGeolocateClick());

        // TextWatcher con debouncing para validaciones
        validationTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                InputUtils.debounce("validacion_paso1", DEBOUNCE_DELAY_MS, () -> actualizarBoton());
            }
        };

        etNombre.addTextChangedListener(validationTextWatcher);
        etApellido.addTextChangedListener(validationTextWatcher);
        etFechaNacimiento.addTextChangedListener(validationTextWatcher);
        etTelefono.addTextChangedListener(validationTextWatcher);
        etCorreo.addTextChangedListener(validationTextWatcher);
        etCedula.addTextChangedListener(validationTextWatcher);
        etPassword.addTextChangedListener(validationTextWatcher);
        cbTerminos.setOnCheckedChangeListener((buttonView, isChecked) -> actualizarBoton());
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchLocationAndFillAddress();
            } else {
                toast("Permiso de ubicación denegado.");
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
                        actualizarBoton();
                    } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                        Status status = Autocomplete.getStatusFromIntent(result.getData());
                        Log.e(TAG, status.getStatusMessage());
                        toast("Error en autocompletado: " + status.getStatusMessage());
                    }
                });
    }

    private void launchAutocomplete() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("EC") // Opcional: Limitar a un país
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
                        actualizarBoton();
                        toast("Dirección autocompletada.");
                    } else {
                        toast("No se pudo encontrar una dirección para esta ubicación.");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Servicio de geocodificación no disponible", e);
                    toast("Error al obtener la dirección.");
                }
            }
            showGeolocateLoading(false);
        }).addOnFailureListener(e -> {
            showGeolocateLoading(false);
            toast("No se pudo obtener la ubicación. Asegúrate de que el GPS esté activado.");
        });
    }

    private void showGeolocateLoading(boolean isLoading) {
        pbGeolocate.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        ivGeolocate.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    private void mostrarDatePicker() {
        final Calendar calendario = Calendar.getInstance();
        int anio = calendario.get(Calendar.YEAR) - 18;
        int mes = calendario.get(Calendar.MONTH);
        int dia = calendario.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar fechaSeleccionada = Calendar.getInstance();
            fechaSeleccionada.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            etFechaNacimiento.setText(sdf.format(fechaSeleccionada.getTime()));
        }, anio, mes, dia);

        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -18);
        datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        datePickerDialog.show();
    }

    private void configurarTerminosYCondiciones() {
        String textoTerminos = "Acepto los <a href='#'>Terminos y Condiciones</a> y la <a href='#'>Politica de Privacidad</a>";
        cbTerminos.setText(Html.fromHtml(textoTerminos, Html.FROM_HTML_MODE_LEGACY));
        cbTerminos.setMovementMethod(LinkMovementMethod.getInstance());

        cbTerminos.setOnClickListener(v -> {
            if (!cbTerminos.isChecked()) {
                mostrarDialogoTerminos();
            }
        });
    }

    private void actualizarBoton() {
        btnRegistrarse.setEnabled(camposEstanLlenos() && cbTerminos.isChecked());
    }

    private boolean camposEstanLlenos() {
        return !isEmpty(etNombre) && !isEmpty(etApellido) && !isEmpty(etFechaNacimiento)
                && !TextUtils.isEmpty(domicilio)
                && !isEmpty(etCedula) && !isEmpty(etTelefono)
                && !isEmpty(etCorreo) && etPassword.getText() != null && !etPassword.getText().toString().isEmpty();
    }

    private boolean validarCamposDetallado() {
        boolean ok = true;

        if (TextUtils.isEmpty(etNombre.getText().toString().trim())) {
            etNombre.setError("El nombre es obligatorio");
            ok = false;
        } else {
            etNombre.setError(null);
        }

        if (TextUtils.isEmpty(etApellido.getText().toString().trim())) {
            etApellido.setError("El apellido es obligatorio");
            ok = false;
        } else {
            etApellido.setError(null);
        }

        Date fechaNac = parseFecha(etFechaNacimiento.getText().toString().trim());
        if (fechaNac == null) {
            etFechaNacimiento.setError("Selecciona tu fecha de nacimiento");
            ok = false;
        } else if (!validarEdad(fechaNac)) {
            etFechaNacimiento.setError("Debes ser mayor de 18 años");
            ok = false;
        } else {
            etFechaNacimiento.setError(null);
        }

        if (!validarCedula(etCedula.getText().toString().trim())) {
            etCedula.setError("Cédula inválida");
            ok = false;
        } else {
            etCedula.setError(null);
        }

        if (TextUtils.isEmpty(domicilio)) {
            toast("La dirección es obligatoria. Por favor, selecciónala de la lista.");
            ok = false;
        }

        if (!validarTelefono(etTelefono.getText().toString().trim())) {
            etTelefono.setError("Teléfono inválido");
            ok = false;
        } else {
            etTelefono.setError(null);
        }

        if (!validarEmail(etCorreo.getText().toString().trim())) {
            etCorreo.setError("Correo inválido");
            ok = false;
        } else {
            etCorreo.setError(null);
        }

        if (etPassword.getText() == null || etPassword.getText().toString().length() < 6) {
            tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        return ok;
    }

    private Date parseFecha(String fechaStr) {
        try {
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(fechaStr);
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean validarEdad(Date nacimiento) {
        Calendar calNacimiento = Calendar.getInstance();
        calNacimiento.setTime(nacimiento);
        Calendar calHoy = Calendar.getInstance();
        calHoy.add(Calendar.YEAR, -18);
        return !calNacimiento.after(calHoy);
    }

    private boolean isEmpty(EditText e) { return TextUtils.isEmpty(e.getText()); }

    private boolean validarEmail(String email) { return Patterns.EMAIL_ADDRESS.matcher(email).matches(); }

    private boolean validarTelefono(String t) { return t.matches("^(\\+593[0-9]{9}|09[0-9]{8})$"); }

    private boolean validarCedula(String c) {
        if (c == null || !c.matches("\\d{10}")) return false;
        try {
            int provincia = Integer.parseInt(c.substring(0, 2));
            if (provincia < 1 || provincia > 24) return false;
            int tercer = Character.getNumericValue(c.charAt(2));
            if (tercer >= 6) return false;
            int[] coef = {2,1,2,1,2,1,2,1,2};
            int suma = 0;
            for (int i=0;i<9;i++) {
                int d = Character.getNumericValue(c.charAt(i)) * coef[i];
                if (d >= 10) d -= 9;
                suma += d;
            }
            int dig = suma % 10 == 0 ? 0 : 10 - (suma % 10);
            return dig == Character.getNumericValue(c.charAt(9));
        } catch (Exception ex) { return false; }
    }

    private void intentarRegistro() {
        if (!validarCamposDetallado()) {
            toast("Por favor, corrige los campos marcados.");
            return;
        }
        if (!cbTerminos.isChecked()) {
            toast("Debes aceptar los términos y condiciones para continuar.");
            return;
        }

        try {
            // Sanitizar todos los inputs
            String nombreSanitizado = InputUtils.sanitizeInput(etNombre.getText().toString().trim());
            String apellidoSanitizado = InputUtils.sanitizeInput(etApellido.getText().toString().trim());
            String telefonoSanitizado = InputUtils.sanitizeInput(etTelefono.getText().toString().trim());
            String correoSanitizado = InputUtils.sanitizeInput(etCorreo.getText().toString().trim());
            String cedulaSanitizada = InputUtils.sanitizeInput(etCedula.getText().toString().trim());
            String domicilioSanitizado = InputUtils.sanitizeInput(domicilio);

            // Guardar en SharedPreferences normales
            SharedPreferences.Editor editor = getSharedPreferences("WizardDueno", MODE_PRIVATE).edit();
            editor.putString("nombre", nombreSanitizado);
            editor.putString("apellido", apellidoSanitizado);
            editor.putString("fecha_nacimiento", etFechaNacimiento.getText().toString().trim());
            editor.putString("telefono", telefonoSanitizado);
            editor.putString("correo", correoSanitizado);
            editor.putString("domicilio", domicilioSanitizado);
            if (domicilioLatLng != null) {
                editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
                editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
            }
            editor.putString("cedula", cedulaSanitizada);
            editor.putBoolean("acepta_terminos", cbTerminos.isChecked());
            editor.apply();

            // Guardar contraseña encriptada
            if (etPassword.getText() != null && !etPassword.getText().toString().isEmpty()) {
                EncryptedPreferencesHelper encryptedPrefs = EncryptedPreferencesHelper.getInstance(this);
                encryptedPrefs.putString("password_dueno", etPassword.getText().toString());
                Log.d(TAG, "Contraseña guardada de forma encriptada");
            }

            toast("Paso 1 completado. Continúa con el siguiente paso.");
            startActivity(new Intent(this, DuenoRegistroPaso2Activity.class));
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar datos del registro", e);
            toast("Error al guardar los datos. Por favor, intenta nuevamente.");
        }
    }

    private void mostrarDialogoTerminos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(14);
        textView.setText(getTextoTerminosCompleto());
        scrollView.addView(textView);

        builder.setTitle("Términos y Condiciones")
                .setView(scrollView)
                .setPositiveButton("Aceptar", (dialog, which) -> cbTerminos.setChecked(true))
                .setNegativeButton("Cancelar", (dialog, which) -> cbTerminos.setChecked(false))
                .setCancelable(false)
                .show();
    }

    private String getTextoTerminosCompleto() {
        return "TÉRMINOS Y CONDICIONES DE USO - Walki (Dueños)\n\n" +
                "1. ACEPTACIÓN DE TÉRMINOS\n" +
                "Al registrarte como dueño de mascota en Walki, aceptas cumplir con estos términos y condiciones.\n\n" +
                "2. RESPONSABILIDADES DEL DUEÑO\n" +
                "- Proporcionar información precisa y completa sobre tu mascota.\n" +
                "- Asegurarte de que tu mascota esté al día con sus vacunas y tratamientos.\n" +
                "- Informar al paseador sobre cualquier comportamiento o necesidad especial.\n\n" +
                "3. PAGOS Y CANCELACIONES\n" +
                "- Realizar los pagos de los servicios a través de la plataforma.\n" +
                "- Respetar las políticas de cancelación de los paseadores.\n\n" +
                "4. CÓDIGO DE CONDUCTA\n" +
                "- Tratar a los paseadores con respeto y profesionalismo.\n" +
                "- No solicitar ni realizar pagos fuera de la plataforma.\n\n" +
                "5. PRIVACIDAD Y DATOS\n" +
                "- Aceptas que la información de tu perfil sea visible para los paseadores.\n" +
                "- Walki se compromete a proteger tus datos según la política de privacidad.\n\n" +
                "6. LIMITACIÓN DE RESPONSABILIDAD\n" +
                "Walki actúa como intermediario y no se hace responsable de incidentes ocurridos durante los paseos.\n\n" +
                "Al marcar esta casilla, confirmas que has leído, entendido y aceptas cumplir con todos estos términos y condiciones.\n\n" +
                "Fecha de última actualización: Octubre 2025";
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (validationTextWatcher != null) {
            if (etNombre != null) etNombre.removeTextChangedListener(validationTextWatcher);
            if (etApellido != null) etApellido.removeTextChangedListener(validationTextWatcher);
            if (etFechaNacimiento != null) etFechaNacimiento.removeTextChangedListener(validationTextWatcher);
            if (etTelefono != null) etTelefono.removeTextChangedListener(validationTextWatcher);
            if (etCorreo != null) etCorreo.removeTextChangedListener(validationTextWatcher);
            if (etCedula != null) etCedula.removeTextChangedListener(validationTextWatcher);
            if (etPassword != null) etPassword.removeTextChangedListener(validationTextWatcher);
        }

        // Cancelar debounces pendientes
        InputUtils.cancelDebounce("validacion_paso1");

        Log.d(TAG, "Activity destruida y recursos limpiados");
    }
}
