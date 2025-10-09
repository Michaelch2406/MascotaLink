package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.GeoPoint;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PaseadorRegistroPaso1Activity extends AppCompatActivity {

    private static final String TAG = "Paso1Paseador";
    private static final String PREFS = "WizardPaseador";

    private EditText etNombre, etApellido, etCedula, etFechaNac, etTelefono, etEmail, etPassword;
    private TextInputLayout tilPassword;
    private CheckBox cbAceptaTerminos;
    private Button btnContinuar;

    // Variables para el domicilio
    private String domicilio;
    private GeoPoint domicilioLatLng;
    private AutocompleteSupportFragment autocompleteFragment;

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        setupPlacesAutocomplete();
        wireDatePicker();
        loadSavedState();
        setupWatchers();
        configurarTerminosYCondiciones();

        btnContinuar.setOnClickListener(v -> onContinuar());

        findViewById(R.id.tv_login_link).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        updateButtonEnabled();
    }

    private void bindViews() {
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etCedula = findViewById(R.id.et_cedula);
        etFechaNac = findViewById(R.id.et_fecha_nacimiento);
        etTelefono = findViewById(R.id.et_telefono);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        tilPassword = findViewById(R.id.til_password);
        cbAceptaTerminos = findViewById(R.id.cb_acepta_terminos);
        btnContinuar = findViewById(R.id.btn_continuar);
    }

    private void setupPlacesAutocomplete() {
        // Initialization is now done in MyApplication.java
        autocompleteFragment = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment_domicilio);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));
            autocompleteFragment.setHint("Empieza a escribir tu dirección...");
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    domicilio = place.getAddress();
                    if (place.getLatLng() != null) {
                        domicilioLatLng = new GeoPoint(place.getLatLng().latitude, place.getLatLng().longitude);
                    }
                    Log.i(TAG, "Place Selected: " + domicilio);
                    saveState();
                    updateButtonEnabled();
                }

                @Override
                public void onError(@NonNull Status status) {
                    Log.e(TAG, "An error occurred during place selection: " + status);
                }
            });
        }
    }

    private void wireDatePicker() {
        etFechaNac.setInputType(InputType.TYPE_NULL);
        etFechaNac.setOnClickListener(v -> {
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
        });
    }

    private void setupWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                saveState();
                updateButtonEnabled();
            }
        };

        etNombre.addTextChangedListener(textWatcher);
        etApellido.addTextChangedListener(textWatcher);
        etCedula.addTextChangedListener(textWatcher);
        etFechaNac.addTextChangedListener(textWatcher);
        etTelefono.addTextChangedListener(textWatcher);
        etEmail.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);
    }

    private void configurarTerminosYCondiciones() {
        String textoTerminos = "Acepto los <a href='#'>Términos y Condiciones</a> y la <a href='#'>Política de Privacidad</a>";
        cbAceptaTerminos.setText(Html.fromHtml(textoTerminos, Html.FROM_HTML_MODE_LEGACY));
        cbAceptaTerminos.setMovementMethod(LinkMovementMethod.getInstance());

        cbAceptaTerminos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            validarCamposYHabilitarBoton();
        });

        cbAceptaTerminos.setOnClickListener(v -> {
            if (!cbAceptaTerminos.isChecked()) {
                mostrarDialogoTerminos();
            }
        });
    }

    private void mostrarDialogoTerminos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setText(getTextoTerminosCompleto());
        scrollView.addView(textView);

        builder.setTitle("Términos y Condiciones")
                .setView(scrollView)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    cbAceptaTerminos.setChecked(true);
                    validarCamposYHabilitarBoton();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> cbAceptaTerminos.setChecked(false))
                .setCancelable(false)
                .show();
    }

    private String getTextoTerminosCompleto() {
        return "TÉRMINOS Y CONDICIONES DE USO - MascotaLink\n\n" +
                "1. ACEPTACIÓN DE TÉRMINOS\n" +
                "Al registrarte como paseador en MascotaLink, aceptas cumplir con estos términos y condiciones.\n\n" +
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
                "MascotaLink se reserva el derecho de suspender o terminar el acceso del paseador en caso de incumplimiento de estos términos.\n\n" +
                "Al marcar esta casilla, confirmas que has leído, entendido y aceptas cumplir con todos estos términos y condiciones.\n\n" +
                "Fecha de última actualización: Septiembre 2025";
    }

    private void onContinuar() {
        if (!cbAceptaTerminos.isChecked()) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones para continuar", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!validarCamposPantalla1()) {
            Toast.makeText(this, "⚠️ Por favor corrige los errores antes de continuar", Toast.LENGTH_SHORT).show();
            return;
        }

        guardarDatosCompletos();
        Toast.makeText(this, "✅ Datos guardados. Continuando...", Toast.LENGTH_SHORT).show();

        startActivity(new Intent(this, PaseadorRegistroPaso2Activity.class));
    }

    private void guardarDatosCompletos() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString("nombre", etNombre.getText().toString().trim());
        editor.putString("apellido", etApellido.getText().toString().trim());
        editor.putString("cedula", etCedula.getText().toString().trim());
        editor.putString("fecha_nacimiento", etFechaNac.getText().toString().trim());
        editor.putString("domicilio", domicilio); // <-- Cambio aquí
        if (domicilioLatLng != null) {
            editor.putFloat("domicilio_lat", (float) domicilioLatLng.getLatitude());
            editor.putFloat("domicilio_lng", (float) domicilioLatLng.getLongitude());
        }
        editor.putString("telefono", etTelefono.getText().toString().trim());
        editor.putString("email", etEmail.getText().toString().trim());
        editor.putString("password", etPassword.getText().toString().trim());
        editor.putBoolean("paso1_completo", true);
        editor.putBoolean("acepto_terminos", true);
        editor.apply();
        Log.d(TAG, "Datos del paso 1 guardados localmente");
    }

    private Date parseFecha() {
        String f = etFechaNac.getText().toString().trim();
        try {
            return sdf.parse(f);
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean validarCamposPantalla1() {
        boolean ok = true;

        if (TextUtils.isEmpty(etNombre.getText().toString().trim())) {
            etNombre.setError("⚠️ El nombre es obligatorio");
            ok = false;
        }
        if (TextUtils.isEmpty(etApellido.getText().toString().trim())) {
            etApellido.setError("⚠️ El apellido es obligatorio");
            ok = false;
        }
        if (!validarCedulaEcuador(etCedula.getText().toString().trim())) {
            etCedula.setError("⚠️ Cédula ecuatoriana inválida");
            ok = false;
        }
        Date fecha = parseFecha();
        if (fecha == null || !validarEdad(fecha)) {
            etFechaNac.setError("⚠️ Debes ser mayor de 18 años");
            ok = false;
        }
        if (TextUtils.isEmpty(domicilio)) { // <-- Cambio aquí
            Toast.makeText(this, "El domicilio es obligatorio", Toast.LENGTH_SHORT).show();
            ok = false;
        }
        if (!etTelefono.getText().toString().trim().matches("^(\\+593[0-9]{9}|09[0-9]{8})$")) {
            etTelefono.setError("⚠️ Formato inválido. Usa: 09XXXXXXXX o +593XXXXXXXXX");
            ok = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(etEmail.getText().toString().trim()).matches()) {
            etEmail.setError("⚠️ Formato de correo inválido");
            ok = false;
        }
        if (etPassword.getText().toString().trim().length() < 6) {
            tilPassword.setError("⚠️ La contraseña debe tener al menos 6 caracteres");
            ok = false;
        }

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
        editor.putString("nombre", etNombre.getText().toString());
        editor.putString("apellido", etApellido.getText().toString());
        editor.putString("cedula", etCedula.getText().toString());
        editor.putString("fecha", etFechaNac.getText().toString());
        editor.putString("domicilio", domicilio);
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
        etFechaNac.setText(prefs.getString("fecha", ""));
        domicilio = prefs.getString("domicilio", "");
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if(lat != 0 && lng != 0) {
            domicilioLatLng = new GeoPoint(lat, lng);
        }
        if (autocompleteFragment != null && !TextUtils.isEmpty(domicilio)) {
            autocompleteFragment.setText(domicilio);
        }
        etTelefono.setText(prefs.getString("telefono", ""));
        etEmail.setText(prefs.getString("email", ""));
        etPassword.setText(prefs.getString("password", ""));
    }

    private void validarCamposYHabilitarBoton() {
        boolean camposCompletos = validarTodosLosCampos();
        boolean terminosAceptados = cbAceptaTerminos.isChecked();
        btnContinuar.setEnabled(camposCompletos && terminosAceptados);
        btnContinuar.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                btnContinuar.isEnabled() ? R.color.blue_primary : R.color.gray_disabled)));
    }

    private boolean validarTodosLosCampos() {
        return !TextUtils.isEmpty(etNombre.getText()) &&
                !TextUtils.isEmpty(etApellido.getText()) &&
                validarCedulaEcuador(etCedula.getText().toString()) &&
                !TextUtils.isEmpty(etFechaNac.getText()) &&
                !TextUtils.isEmpty(domicilio) && // <-- Cambio aquí
                etTelefono.getText().toString().matches("^(\\+593[0-9]{9}|09[0-9]{8})$") &&
                Patterns.EMAIL_ADDRESS.matcher(etEmail.getText().toString()).matches() &&
                etPassword.getText().toString().length() >= 6;
    }

    private void updateButtonEnabled() {
        validarCamposYHabilitarBoton();
    }


}