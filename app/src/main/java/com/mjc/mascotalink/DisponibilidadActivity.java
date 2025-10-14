package com.mjc.mascotalink;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DisponibilidadActivity extends AppCompatActivity {

    private final String[] diasSemana = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private final List<CheckBox> checkBoxes = new ArrayList<>();

    private EditText etHoraInicio, etHoraFin;
    private TextView tvValidationMessages;
    private Button btnGuardar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
        }

        setupToolbar();
        setupViews();
        setupTimePickers();
        loadDisponibilidadFromFirestore();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);

        Button btnSeleccionarTodos = findViewById(R.id.btn_seleccionar_todos);
        Button btnDeseleccionarTodos = findViewById(R.id.btn_deseleccionar_todos);
        Button btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);

        GridLayout gridLayout = findViewById(R.id.grid_dias);
        createDayCheckBoxes(gridLayout);

        btnSeleccionarTodos.setOnClickListener(v -> setAllCheckBoxes(true));
        btnDeseleccionarTodos.setOnClickListener(v -> setAllCheckBoxes(false));
        btnGuardar.setOnClickListener(v -> guardarDisponibilidad());
    }

    private void createDayCheckBoxes(GridLayout gridLayout) {
        gridLayout.removeAllViews();
        checkBoxes.clear();
        for (String dia : diasSemana) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(dia);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            checkBox.setLayoutParams(params);
            gridLayout.addView(checkBox);
            checkBoxes.add(checkBox);
        }
    }

    private void setupTimePickers() {
        etHoraInicio.setOnClickListener(v -> showTimePicker(etHoraInicio));
        etHoraFin.setOnClickListener(v -> showTimePicker(etHoraFin));
    }

    private void showTimePicker(EditText editText) {
        String currentTime = editText.getText().toString();
        int hour = 8, minute = 0;
        if (!TextUtils.isEmpty(currentTime) && currentTime.contains(":")) {
            try {
                String[] parts = currentTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* Ignored */ }
        }

        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
            editText.setText(formattedTime);
        }, hour, minute, true).show();
    }

    private void setAllCheckBoxes(boolean isChecked) {
        for (CheckBox checkBox : checkBoxes) {
            checkBox.setChecked(isChecked);
        }
    }

    private void guardarDisponibilidad() {
        List<String> diasSeleccionados = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isChecked()) {
                diasSeleccionados.add(diasSemana[i]);
            }
        }

        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();

        if (!validateInputs(diasSeleccionados, inicio, fin)) {
            return;
        }

        if (currentUserId == null) {
            Toast.makeText(this, "Error: No se pudo identificar al usuario.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create data map
        java.util.Map<String, Object> disponibilidadData = new java.util.HashMap<>();
        disponibilidadData.put("dias", diasSeleccionados);
        disponibilidadData.put("hora_inicio", inicio);
        disponibilidadData.put("hora_fin", fin);
        disponibilidadData.put("activo", true);

        com.google.firebase.firestore.CollectionReference disponibilidadRef = db.collection("paseadores").document(currentUserId).collection("disponibilidad");

        // Batch write: delete old docs and add the new one
        disponibilidadRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            com.google.firebase.firestore.WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                batch.delete(doc.getReference());
            }
            batch.set(disponibilidadRef.document(), disponibilidadData);

            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Disponibilidad guardada con éxito", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al consultar disponibilidad anterior: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void loadDisponibilidadFromFirestore() {
        if (currentUserId == null) {
            // Set default values if no user is logged in
            etHoraInicio.setText("09:00");
            etHoraFin.setText("17:00");
            return;
        }

        db.collection("paseadores").document(currentUserId).collection("disponibilidad")
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        etHoraInicio.setText(doc.getString("hora_inicio"));
                        etHoraFin.setText(doc.getString("hora_fin"));
                        List<String> diasGuardados = (List<String>) doc.get("dias");
                        if (diasGuardados != null) {
                            for (int i = 0; i < diasSemana.length; i++) {
                                if (diasGuardados.contains(diasSemana[i])) {
                                    checkBoxes.get(i).setChecked(true);
                                }
                            }
                        }
                    } else {
                        // Set default values if no availability is set in Firestore
                        etHoraInicio.setText("09:00");
                        etHoraFin.setText("17:00");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar disponibilidad", Toast.LENGTH_SHORT).show();
                    // Set default values on failure
                    etHoraInicio.setText("09:00");
                    etHoraFin.setText("17:00");
                });
    }

    private boolean validateInputs(List<String> dias, String inicio, String fin) {
        tvValidationMessages.setVisibility(View.GONE);
        List<String> errores = new ArrayList<>();

        if (dias.isEmpty()) {
            errores.add("• Debes seleccionar al menos un día.");
        }
        if (TextUtils.isEmpty(inicio)) {
            errores.add("• La hora de inicio es requerida.");
        }
        if (TextUtils.isEmpty(fin)) {
            errores.add("• La hora de fin es requerida.");
        }

        if (!TextUtils.isEmpty(inicio) && !TextUtils.isEmpty(fin)) {
            try {
                String[] inicioPartes = inicio.split(":");
                String[] finPartes = fin.split(":");
                int inicioMinutos = Integer.parseInt(inicioPartes[0]) * 60 + Integer.parseInt(inicioPartes[1]);
                int finMinutos = Integer.parseInt(finPartes[0]) * 60 + Integer.parseInt(finPartes[1]);

                if (finMinutos <= inicioMinutos) {
                    errores.add("• La hora de fin debe ser posterior a la de inicio.");
                }
            } catch (Exception e) {
                errores.add("• Formato de hora inválido.");
            }
        }

        if (!errores.isEmpty()) {
            tvValidationMessages.setText(String.join("\n", errores));
            tvValidationMessages.setVisibility(View.VISIBLE);
            return false;
        }
        return true;
    }
}