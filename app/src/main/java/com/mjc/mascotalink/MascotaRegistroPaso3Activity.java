package com.mjc.mascotalink;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class MascotaRegistroPaso3Activity extends AppCompatActivity {

    private static final String PREFS = "MascotaWizard";

    private RadioGroup rgNivelEnergia, rgConPersonas, rgConOtrosAnimales, rgConOtrosPerrosFila1, rgConOtrosPerrosFila2, rgHabitosFila1, rgHabitosFila2;
    private EditText etComandos, etMiedos, etManias;
    private Button btnGuardar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso3);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        loadState();

        rgConOtrosPerrosFila1.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgConOtrosPerrosFila2.clearCheck(); });
        rgConOtrosPerrosFila2.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgConOtrosPerrosFila1.clearCheck(); });
        rgHabitosFila1.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgHabitosFila2.clearCheck(); });
        rgHabitosFila2.setOnCheckedChangeListener((g, id) -> { if (id != -1) rgHabitosFila1.clearCheck(); });

        btnGuardar.setOnClickListener(v -> { saveState(); finish(); });
    }

    private void bindViews() {
        rgNivelEnergia = findViewById(R.id.rg_nivel_energia);
        rgConPersonas = findViewById(R.id.rg_con_personas);
        rgConOtrosAnimales = findViewById(R.id.rg_con_otros_animales);
        rgConOtrosPerrosFila1 = findViewById(R.id.rg_con_otros_perros_1);
        rgConOtrosPerrosFila2 = findViewById(R.id.rg_con_otros_perros_2);
        rgHabitosFila1 = findViewById(R.id.rg_habitos_correa_1);
        rgHabitosFila2 = findViewById(R.id.rg_habitos_correa_2);
        etComandos = findViewById(R.id.et_comandos);
        etMiedos = findViewById(R.id.et_miedos);
        etManias = findViewById(R.id.et_manias);
        btnGuardar = findViewById(R.id.btn_guardar);
    }

    private void saveState() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putString("comp_nivel_energia", getTextFrom(rgNivelEnergia));
        ed.putString("comp_con_personas", getTextFrom(rgConPersonas));
        ed.putString("comp_con_otros_animales", getTextFrom(rgConOtrosAnimales));
        ed.putString("comp_con_otros_perros", getTextFromTwo(rgConOtrosPerrosFila1, rgConOtrosPerrosFila2));
        ed.putString("comp_habitos_correa", getTextFromTwo(rgHabitosFila1, rgHabitosFila2));
        ed.putString("comp_comandos_conocidos", etComandos.getText().toString().trim());
        ed.putString("comp_miedos_fobias", etMiedos.getText().toString().trim());
        ed.putString("comp_manias_habitos", etManias.getText().toString().trim());
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        checkRadio(rgNivelEnergia, prefs.getString("comp_nivel_energia", ""));
        checkRadio(rgConPersonas, prefs.getString("comp_con_personas", ""));
        checkRadio(rgConOtrosAnimales, prefs.getString("comp_con_otros_animales", ""));
        checkTwo(rgConOtrosPerrosFila1, rgConOtrosPerrosFila2, prefs.getString("comp_con_otros_perros", ""));
        checkTwo(rgHabitosFila1, rgHabitosFila2, prefs.getString("comp_habitos_correa", ""));
        etComandos.setText(prefs.getString("comp_comandos_conocidos", ""));
        etMiedos.setText(prefs.getString("comp_miedos_fobias", ""));
        etManias.setText(prefs.getString("comp_manias_habitos", ""));
    }

    private String getTextFrom(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return "";
        RadioButton rb = findViewById(id);
        return rb != null ? rb.getText().toString().trim() : "";
    }

    private String getTextFromTwo(RadioGroup g1, RadioGroup g2) {
        String t = getTextFrom(g1);
        if (!TextUtils.isEmpty(t)) return t;
        return getTextFrom(g2);
    }

    private void checkRadio(RadioGroup group, String text) {
        if (TextUtils.isEmpty(text)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof RadioButton) {
                RadioButton rb = (RadioButton) group.getChildAt(i);
                if (text.equalsIgnoreCase(rb.getText().toString())) {
                    rb.setChecked(true);
                    break;
                }
            }
        }
    }

    private void checkTwo(RadioGroup g1, RadioGroup g2, String text) {
        if (TextUtils.isEmpty(text)) return;
        checkRadio(g1, text);
        checkRadio(g2, text);
    }
}
