package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class MascotaRegistroPaso3Activity extends AppCompatActivity {

    private ImageView arrowBack;
    private RadioGroup nivelEnergiaRadioGroup, conPersonasRadioGroup, conAnimalesRadioGroup, conPerrosRadioGroup, habitosCorreaRadioGroup;
    private TextInputEditText comandosConocidosEditText, miedosFobiasEditText, maniasHabitosEditText;
    private Button siguienteButton;

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
        siguienteButton.setOnClickListener(v -> collectDataAndProceed());

        RadioGroup.OnCheckedChangeListener radioListener = (group, checkedId) -> validateInputs();
        nivelEnergiaRadioGroup.setOnCheckedChangeListener(radioListener);
        conPersonasRadioGroup.setOnCheckedChangeListener(radioListener);
        conAnimalesRadioGroup.setOnCheckedChangeListener(radioListener);
        conPerrosRadioGroup.setOnCheckedChangeListener(radioListener);
        habitosCorreaRadioGroup.setOnCheckedChangeListener(radioListener);

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
        comandosConocidosEditText.addTextChangedListener(textWatcher);
        miedosFobiasEditText.addTextChangedListener(textWatcher);
        maniasHabitosEditText.addTextChangedListener(textWatcher);
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
        Intent intent = new Intent(this, MascotaRegistroPaso4Activity.class);
        intent.putExtras(getIntent().getExtras()); // Carry over all previous data

        // Add data from Paso 3
        intent.putExtra("nivel_energia", getSelectedRadioButtonText(nivelEnergiaRadioGroup));
        intent.putExtra("con_personas", getSelectedRadioButtonText(conPersonasRadioGroup));
        intent.putExtra("con_otros_animales", getSelectedRadioButtonText(conAnimalesRadioGroup));
        intent.putExtra("con_otros_perros", getSelectedRadioButtonText(conPerrosRadioGroup));
        intent.putExtra("habitos_correa", getSelectedRadioButtonText(habitosCorreaRadioGroup));
        intent.putExtra("comandos_conocidos", comandosConocidosEditText.getText().toString().trim());
        intent.putExtra("miedos_fobias", miedosFobiasEditText.getText().toString().trim());
        intent.putExtra("manias_habitos", maniasHabitosEditText.getText().toString().trim());

        startActivity(intent);
    }

    private String getSelectedRadioButtonText(RadioGroup radioGroup) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadioButton = findViewById(selectedId);
            return selectedRadioButton.getText().toString();
        }
        return "";
    }
}