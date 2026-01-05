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
import android.text.InputFilter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

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

    private TextInputEditText etNombre, etApellido, etFechaNacimiento, etTelefono, etCorreo, etCedula, etDomicilio;
    private TextInputLayout tilNombre, tilApellido, tilFechaNacimiento, tilCedula, tilTelefono, tilCorreo, tilDomicilio, tilPassword;
    private TextInputEditText etPassword;
    private CheckBox cbTerminos;
    private Button btnRegistrarse;
    private ImageView ivGeolocate;
    private ProgressBar pbGeolocate;
    private NestedScrollView scrollView;

    private String domicilio;
    private GeoPoint domicilioLatLng;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    private TextWatcher validationTextWatcher;

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

        scrollView = findViewById(R.id.scroll_view);

        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etFechaNacimiento = findViewById(R.id.et_fecha_nacimiento);
        etTelefono = findViewById(R.id.et_telefono);
        etCorreo = findViewById(R.id.et_correo);
        etCedula = findViewById(R.id.et_cedula);
        etDomicilio = findViewById(R.id.et_domicilio);

        tilNombre = findViewById(R.id.et_nombre).getParent().getParent() instanceof TextInputLayout
            ? (TextInputLayout) findViewById(R.id.et_nombre).getParent().getParent() : null;
        tilApellido = findViewById(R.id.et_apellido).getParent().getParent() instanceof TextInputLayout
            ? (TextInputLayout) findViewById(R.id.et_apellido).getParent().getParent() : null;
        tilFechaNacimiento = findViewById(R.id.et_fecha_nacimiento).getParent().getParent() instanceof TextInputLayout
            ? (TextInputLayout) findViewById(R.id.et_fecha_nacimiento).getParent().getParent() : null;
        tilCedula = findViewById(R.id.til_cedula);
        tilTelefono = findViewById(R.id.til_telefono);
        tilCorreo = findViewById(R.id.til_correo);
        tilDomicilio = findViewById(R.id.til_domicilio);
        tilPassword = findViewById(R.id.til_password);

        etPassword = findViewById(R.id.et_password);
        cbTerminos = findViewById(R.id.cb_terminos);
        btnRegistrarse = findViewById(R.id.btn_registrarse);
        ivGeolocate = findViewById(R.id.iv_geolocate);
        pbGeolocate = findViewById(R.id.pb_geolocate);

        // Aplicar filtros de entrada para nombres (solo letras y espacios)
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
        btnRegistrarse.setOnClickListener(InputUtils.createSafeClickListener(v -> intentarRegistro()));
        etFechaNacimiento.setOnClickListener(v -> mostrarDatePicker());
        etDomicilio.setOnClickListener(v -> launchAutocomplete());

        // Listener para el icono de ubicación en el domicilio
        if (tilDomicilio != null) {
            tilDomicilio.setEndIconOnClickListener(v -> onGeolocateClick());
        }

        validationTextWatcher = InputUtils.createDebouncedTextWatcher(
            "validacion_paso1",
            DEBOUNCE_DELAY_MS,
            text -> actualizarBoton()
        );

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
        String texto = "Acepto los Términos y Condiciones y la Política de Privacidad";
        android.text.SpannableString ss = new android.text.SpannableString(texto);

        // ClickableSpan para Términos y Condiciones
        android.text.style.ClickableSpan terminosSpan = new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // Evitar que el checkbox cambie de estado al pulsar el link
                widget.cancelPendingInputEvents();
                startActivity(new Intent(DuenoRegistroPaso1Activity.this, TerminosCondicionesActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(DuenoRegistroPaso1Activity.this, R.color.blue_primary));
            }
        };

        // ClickableSpan para Política de Privacidad
        android.text.style.ClickableSpan politicaSpan = new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                widget.cancelPendingInputEvents();
                startActivity(new Intent(DuenoRegistroPaso1Activity.this, PoliticaPrivacidadActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(DuenoRegistroPaso1Activity.this, R.color.blue_primary));
            }
        };

        // Índices para "Términos y Condiciones" (11 a 33) y "Política de Privacidad" (39 a 61)
        ss.setSpan(terminosSpan, 11, 33, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(politicaSpan, 39, 61, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        cbTerminos.setText(ss);
        cbTerminos.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
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
        View firstErrorView = null;

        // Validar nombre
        String nombre = etNombre.getText().toString().trim();
        if (!InputUtils.isValidName(nombre)) {
            if (tilNombre != null) {
                tilNombre.setError("Nombre inválido (solo letras, 2-50 caracteres)");
                if (firstErrorView == null) firstErrorView = tilNombre;
            }
            ok = false;
        } else {
            if (tilNombre != null) tilNombre.setError(null);
        }

        // Validar apellido
        String apellido = etApellido.getText().toString().trim();
        if (!InputUtils.isValidName(apellido)) {
            if (tilApellido != null) {
                tilApellido.setError("Apellido inválido (solo letras, 2-50 caracteres)");
                if (firstErrorView == null) firstErrorView = tilApellido;
            }
            ok = false;
        } else {
            if (tilApellido != null) tilApellido.setError(null);
        }

        // Validar fecha de nacimiento
        Date fechaNac = parseFecha(etFechaNacimiento.getText().toString().trim());
        if (fechaNac == null) {
            if (tilFechaNacimiento != null) {
                tilFechaNacimiento.setError("Selecciona tu fecha de nacimiento");
                if (firstErrorView == null) firstErrorView = tilFechaNacimiento;
            }
            ok = false;
        } else if (!validarEdad(fechaNac)) {
            if (tilFechaNacimiento != null) {
                tilFechaNacimiento.setError("Debes ser mayor de 18 años");
                if (firstErrorView == null) firstErrorView = tilFechaNacimiento;
            }
            ok = false;
        } else {
            if (tilFechaNacimiento != null) tilFechaNacimiento.setError(null);
        }

        // Validar cédula
        String cedula = etCedula.getText().toString().trim();
        if (cedula.isEmpty()) {
            tilCedula.setError("La cédula no puede estar vacía");
            if (firstErrorView == null) firstErrorView = tilCedula;
        } else if (cedula.length() != 10) {
            tilCedula.setError("La cédula debe tener exactamente 10 dígitos");
            if (firstErrorView == null) firstErrorView = tilCedula;
        } else if (!InputUtils.isValidCedulaEcuador(cedula)) {
            tilCedula.setError("Cédula ecuatoriana inválida (dígito verificador incorrecto)");
            if (firstErrorView == null) firstErrorView = tilCedula;
        } else {
            tilCedula.setError(null);
        }

        // Validar teléfono
        if (!validarTelefono(etTelefono.getText().toString().trim())) {
            tilTelefono.setError("Teléfono inválido (10 dígitos)");
            if (firstErrorView == null) firstErrorView = tilTelefono;
            ok = false;
        } else {
            tilTelefono.setError(null);
        }

        // Validar correo
        if (!validarEmail(etCorreo.getText().toString().trim())) {
            tilCorreo.setError("Correo inválido");
            if (firstErrorView == null) firstErrorView = tilCorreo;
            ok = false;
        } else {
            tilCorreo.setError(null);
        }

        // Validar domicilio
        if (TextUtils.isEmpty(domicilio)) {
            tilDomicilio.setError("La dirección es obligatoria");
            if (firstErrorView == null) firstErrorView = tilDomicilio;
            ok = false;
        } else {
            tilDomicilio.setError(null);
        }

        // Validar contraseña
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (!InputUtils.isValidPassword(password)) {
            tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
            if (firstErrorView == null) firstErrorView = tilPassword;
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        // Scroll al primer error encontrado
        if (!ok && firstErrorView != null) {
            scrollToView(firstErrorView);
        }

        return ok;
    }

    private void scrollToView(View view) {
        if (scrollView != null && view != null) {
            scrollView.post(() -> {
                int scrollY = view.getTop() - 100; // 100px de padding superior
                scrollView.smoothScrollTo(0, scrollY);
                view.requestFocus();
            });
        }
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

    private boolean isEmpty(TextInputEditText e) { return TextUtils.isEmpty(e.getText()); }

    private boolean validarEmail(String email) { return InputUtils.isValidEmail(email); }

    private boolean validarTelefono(String t) { return InputUtils.isValidTelefonoEcuador(t); }

    private boolean validarCedula(String c) { return InputUtils.isValidCedulaEcuador(c); }

    private void intentarRegistro() {
        if (!validarCamposDetallado()) {
            toast("Por favor, corrige los campos marcados.");
            return;
        }
        if (!cbTerminos.isChecked()) {
            toast("Debes aceptar los términos y condiciones para continuar.");
            return;
        }

        // Ocultar teclado
        InputUtils.hideKeyboard(this);

        // Mostrar estado de carga
        InputUtils.setButtonLoading(btnRegistrarse, true, "Guardando...");

        try {
            // Sanitizar y formatear inputs
            String nombreSanitizado = InputUtils.capitalizeWords(
                InputUtils.sanitizeInput(etNombre.getText().toString())
            );
            String apellidoSanitizado = InputUtils.capitalizeWords(
                InputUtils.sanitizeInput(etApellido.getText().toString())
            );
            String telefonoSanitizado = InputUtils.formatTelefonoEcuador(
                InputUtils.sanitizeInput(etTelefono.getText().toString())
            );
            String correoSanitizado = InputUtils.sanitizeInput(etCorreo.getText().toString()).toLowerCase().trim();
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

            // Restaurar botón antes de cambiar de activity
            InputUtils.setButtonLoading(btnRegistrarse, false);

            startActivity(new Intent(this, DuenoRegistroPaso2Activity.class));
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar datos del registro", e);
            toast("Error al guardar los datos. Por favor, intenta nuevamente.");

            // Restaurar botón en caso de error
            InputUtils.setButtonLoading(btnRegistrarse, false);
        }
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
