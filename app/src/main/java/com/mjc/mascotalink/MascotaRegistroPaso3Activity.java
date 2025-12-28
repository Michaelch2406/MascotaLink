package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mjc.mascotalink.utils.InputUtils;

public class MascotaRegistroPaso3Activity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistroPaso3";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long RATE_LIMIT_MS = 1000;

    private ImageView arrowBack;
    private RadioGroup nivelEnergiaRadioGroup, conPersonasRadioGroup, conAnimalesRadioGroup, conPerrosRadioGroup, habitosCorreaRadioGroup;
    private TextInputEditText comandosConocidosEditText, miedosFobiasEditText, maniasHabitosEditText;
    private Button siguienteButton;

    private TextWatcher validationTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso3);

        arrowBack = findViewById(R.id.arrow_back);
        nivelEnergiaRadioGroup = findViewById(R.id.nivelEnergiaRadioGroup);
        conPersonasRadioGroup = findViewById(R.id.conPersonasRadioGroup);
        conAnimalesRadioGroup = findViewById(R.id.conAnimalesRadioGroup);
        conPerrosRadioGroup = findViewById(R.id.conPerrosRadioGroup);
        habitosCorreaRadioGroup = findViewById(R.id.habitosCorreaRadioGroup);
        comandosConocidosEditText = findViewById(R.id.comandosConocidosEditText);
        miedosFobiasEditText = findViewById(R.id.miedosFobiasEditText);
        maniasHabitosEditText = findViewById(R.id.maniasHabitosEditText);
        siguienteButton = findViewById(R.id.siguienteButton);

        setupListeners();
        validateInputs();
    }

    private void setupListeners() {
        arrowBack.setOnClickListener(v -> finish());
        siguienteButton.setOnClickListener(InputUtils.createSafeClickListener(v -> collectDataAndProceed()));

        RadioGroup.OnCheckedChangeListener radioListener = (group, checkedId) -> validateInputs();
        nivelEnergiaRadioGroup.setOnCheckedChangeListener(radioListener);
        conPersonasRadioGroup.setOnCheckedChangeListener(radioListener);
        conAnimalesRadioGroup.setOnCheckedChangeListener(radioListener);
        conPerrosRadioGroup.setOnCheckedChangeListener(radioListener);
        habitosCorreaRadioGroup.setOnCheckedChangeListener(radioListener);

        validationTextWatcher = InputUtils.createDebouncedTextWatcher(
            "validacion_mascota_paso3",
            DEBOUNCE_DELAY_MS,
            text -> validateInputs()
        );
        comandosConocidosEditText.addTextChangedListener(validationTextWatcher);
        miedosFobiasEditText.addTextChangedListener(validationTextWatcher);
        maniasHabitosEditText.addTextChangedListener(validationTextWatcher);
    }

    private void validateInputs() {
        boolean allFilled = nivelEnergiaRadioGroup.getCheckedRadioButtonId() != -1
                && conPersonasRadioGroup.getCheckedRadioButtonId() != -1
                && conAnimalesRadioGroup.getCheckedRadioButtonId() != -1
                && conPerrosRadioGroup.getCheckedRadioButtonId() != -1
                && habitosCorreaRadioGroup.getCheckedRadioButtonId() != -1
                && !comandosConocidosEditText.getText().toString().trim().isEmpty()
                && !miedosFobiasEditText.getText().toString().trim().isEmpty()
                && !maniasHabitosEditText.getText().toString().trim().isEmpty();
        siguienteButton.setEnabled(allFilled);
    }

    private void collectDataAndProceed() {
        try {
            Intent intent = new Intent(this, MascotaRegistroPaso4Activity.class);
            intent.putExtras(getIntent().getExtras());

            // Sanitizar inputs de texto
            String comandosSanitizados = InputUtils.sanitizeInput(comandosConocidosEditText.getText().toString().trim());
            String miedosSanitizados = InputUtils.sanitizeInput(miedosFobiasEditText.getText().toString().trim());
            String maniasSanitizadas = InputUtils.sanitizeInput(maniasHabitosEditText.getText().toString().trim());

            intent.putExtra("nivel_energia", getSelectedRadioButtonText(nivelEnergiaRadioGroup));
            intent.putExtra("con_personas", getSelectedRadioButtonText(conPersonasRadioGroup));
            intent.putExtra("con_otros_animales", getSelectedRadioButtonText(conAnimalesRadioGroup));
            intent.putExtra("con_otros_perros", getSelectedRadioButtonText(conPerrosRadioGroup));
            intent.putExtra("habitos_correa", getSelectedRadioButtonText(habitosCorreaRadioGroup));
            intent.putExtra("comandos_conocidos", comandosSanitizados);
            intent.putExtra("miedos_fobias", miedosSanitizados);
            intent.putExtra("manias_habitos", maniasSanitizadas);

            Log.d(TAG, "Datos del paso 3 recolectados correctamente");
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error al recolectar datos", e);
            Toast.makeText(this, "Error al procesar los datos. Intenta nuevamente.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getSelectedRadioButtonText(RadioGroup radioGroup) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadioButton = findViewById(selectedId);
            return selectedRadioButton.getText().toString();
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (validationTextWatcher != null) {
            if (comandosConocidosEditText != null) comandosConocidosEditText.removeTextChangedListener(validationTextWatcher);
            if (miedosFobiasEditText != null) miedosFobiasEditText.removeTextChangedListener(validationTextWatcher);
            if (maniasHabitosEditText != null) maniasHabitosEditText.removeTextChangedListener(validationTextWatcher);
        }

        // Cancelar debounces pendientes
        InputUtils.cancelDebounce("validacion_mascota_paso3");

        Log.d(TAG, "Activity destruida y recursos limpiados");
    }
}