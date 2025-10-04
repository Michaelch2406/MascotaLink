package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MascotaRegistroPaso1Activity extends AppCompatActivity {

    private static final String PREFS = "MascotaWizard";

    private ImageView btnBack;
    private EditText etNombre;
    private AutoCompleteTextView etRaza;
    private RadioGroup rgSexo;
    private EditText etFechaNac;
    private RadioGroup rgTamanoFila1;
    private RadioGroup rgTamanoFila2;
    private EditText etPeso;
    private ImageView ivFoto;
    private Button btnGaleria, btnCamara, btnGuardar;

    private Uri fotoUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso1);

        bindViews();
        setupRazas();
        setupDatePicker();
        setupPhotoPickers();
        loadState();
        updateSaveEnabled();

        btnBack.setOnClickListener(v -> finish());
        btnGuardar.setOnClickListener(v -> {
            if (!validateAll()) { Toast.makeText(this, "Completa los campos requeridos", Toast.LENGTH_SHORT).show(); return; }
            saveState();
            startActivity(new Intent(this, MascotaRegistroPaso2Activity.class));
        });
    }

    private void bindViews() {
        btnBack = findViewById(R.id.iv_back);
        etNombre = findViewById(R.id.et_nombre_mascota);
        etRaza = findViewById(R.id.et_raza);
        rgSexo = findViewById(R.id.rg_sexo);
        etFechaNac = findViewById(R.id.et_fecha_nac);
        rgTamanoFila1 = findViewById(R.id.rg_tamano_1);
        rgTamanoFila2 = findViewById(R.id.rg_tamano_2);
        etPeso = findViewById(R.id.et_peso);
        ivFoto = findViewById(R.id.iv_foto_principal);
        btnGaleria = findViewById(R.id.btn_galeria);
        btnCamara = findViewById(R.id.btn_camara);
        btnGuardar = findViewById(R.id.btn_guardar);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSaveEnabled(); }
            @Override public void afterTextChanged(Editable s) { updateSaveEnabled(); }
        };
        etNombre.addTextChangedListener(watcher);
        etRaza.addTextChangedListener(watcher);
        etFechaNac.addTextChangedListener(watcher);
        etPeso.addTextChangedListener(watcher);
        rgSexo.setOnCheckedChangeListener((g, id) -> updateSaveEnabled());
        rgTamanoFila1.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgTamanoFila2.clearCheck(); updateSaveEnabled(); });
        rgTamanoFila2.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgTamanoFila1.clearCheck(); updateSaveEnabled(); });
    }

    private void setupRazas() {
        String[] razas = getResources().getStringArray(R.array.razas_perros);
        etRaza.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, razas));
    }

    private void setupDatePicker() {
        etFechaNac.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this, (DatePicker view, int year, int month, int dayOfMonth) -> {
            String formatted = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month+1, year);
            etFechaNac.setText(formatted);
            updateSaveEnabled();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private void setupPhotoPickers() {
        btnGaleria.setOnClickListener(v -> {
            Intent galleryIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
            galleryIntent.setType("image/*");
            galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            galleryLauncher.launch(galleryIntent);
        });
        btnCamara.setOnClickListener(v -> {
            Intent intent = new Intent(this, SelfieActivity.class);
            intent.putExtra("front", false);
            cameraLauncher.launch(intent);
        });
    }

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Uri local = FileStorageHelper.copyFileToInternalStorage(this, uri, "MASCOTA_");
                        if (local != null) {
                            fotoUri = local;
                            Glide.with(this).load(fotoUri).into(ivFoto);
                            saveState();
                            updateSaveEnabled();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Uri local = FileStorageHelper.copyFileToInternalStorage(this, uri, "MASCOTA_");
                        if (local != null) {
                            fotoUri = local;
                            Glide.with(this).load(fotoUri).into(ivFoto);
                            saveState();
                            updateSaveEnabled();
                        }
                    }
                }
            });

    private boolean validateAll() {
        String nombre = etNombre.getText().toString().trim();
        if (TextUtils.isEmpty(nombre) || nombre.length() > 50) return false;
        if (TextUtils.isEmpty(etRaza.getText())) return false;
        if (rgSexo.getCheckedRadioButtonId() == -1) return false;
        if (TextUtils.isEmpty(etFechaNac.getText())) return false;
        if (getTamanoSeleccionado() == null) return false;
        if (TextUtils.isEmpty(etPeso.getText())) return false;
        return true;
    }

    private void updateSaveEnabled() { btnGuardar.setEnabled(validateAll()); }

    private String getTamanoSeleccionado() {
        int id1 = rgTamanoFila1.getCheckedRadioButtonId();
        int id2 = rgTamanoFila2.getCheckedRadioButtonId();
        int id = id1 != -1 ? id1 : id2;
        if (id == -1) return null;
        RadioButton rb = findViewById(id);
        return rb != null ? rb.getText().toString().trim() : null;
    }

    private long parseFechaToMillis(String ddMMyyyy) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return sdf.parse(ddMMyyyy).getTime();
        } catch (ParseException e) { return 0; }
    }

    private void saveState() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putString("nombre", etNombre.getText().toString().trim());
        ed.putString("raza", etRaza.getText().toString().trim());
        ed.putString("sexo", ((RadioButton)findViewById(rgSexo.getCheckedRadioButtonId())).getText().toString());
        ed.putLong("fecha_nacimiento", parseFechaToMillis(etFechaNac.getText().toString().trim()));
        ed.putString("tamano", getTamanoSeleccionado());
        ed.putString("peso", etPeso.getText().toString().trim());
        ed.putString("foto_uri", fotoUri != null ? fotoUri.toString() : "");
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etNombre.setText(prefs.getString("nombre", ""));
        etRaza.setText(prefs.getString("raza", ""));
        String sexo = prefs.getString("sexo", "Macho");
        if ("Macho".equals(sexo)) ((RadioButton)findViewById(R.id.rb_macho)).setChecked(true); else ((RadioButton)findViewById(R.id.rb_hembra)).setChecked(true);
        long f = prefs.getLong("fecha_nacimiento", 0);
        if (f > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            etFechaNac.setText(sdf.format(f));
        }
        String tamano = prefs.getString("tamano", "");
        if (!TextUtils.isEmpty(tamano)) {
            checkTamano(tamano);
        }
        etPeso.setText(prefs.getString("peso", ""));
        String foto = prefs.getString("foto_uri", "");
        if (!TextUtils.isEmpty(foto)) {
            fotoUri = Uri.parse(foto);
            Glide.with(this).load(fotoUri).into(ivFoto);
        }
    }

    private void checkTamano(String t) {
        if ("Pequeño".equals(t)) ((RadioButton)findViewById(R.id.rb_pequeno)).setChecked(true);
        else if ("Mediano".equals(t)) ((RadioButton)findViewById(R.id.rb_mediano)).setChecked(true);
        else if ("Grande".equals(t)) ((RadioButton)findViewById(R.id.rb_grande)).setChecked(true);
        else if ("Extra Grande".equals(t)) ((RadioButton)findViewById(R.id.rb_extra_grande)).setChecked(true);
    }
}
