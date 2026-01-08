package com.mjc.mascotalink.fragments;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Diálogo para configurar el horario por defecto (recurrente) del paseador
 */
public class DialogHorarioDefaultFragment extends DialogFragment {

    private static final String TAG = "DialogHorarioDefault";

    private CheckBox cbLunes, cbMartes, cbMiercoles, cbJueves, cbViernes, cbSabado, cbDomingo;
    private TextView tvHoraInicio, tvHoraFin;
    private Button btnGuardar, btnCancelar;
    private TextView btnSeleccionarTodos, btnDeseleccionarTodos;

    private String horaInicio = "09:00";
    private String horaFin = "18:00";

    private FirebaseFirestore db;
    private String paseadorId;
    private boolean isRegistrationMode = false;
    private SharedPreferences encryptedPrefs;

    public interface OnHorarioGuardadoListener {
        void onHorarioGuardado();
    }

    private OnHorarioGuardadoListener listener;

    public static DialogHorarioDefaultFragment newInstance(String paseadorId) {
        return newInstance(paseadorId, false);
    }

    public static DialogHorarioDefaultFragment newInstance(String paseadorId, boolean isRegistrationMode) {
        DialogHorarioDefaultFragment fragment = new DialogHorarioDefaultFragment();
        Bundle args = new Bundle();
        args.putString("paseadorId", paseadorId);
        args.putBoolean("isRegistrationMode", isRegistrationMode);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnHorarioGuardadoListener(OnHorarioGuardadoListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            paseadorId = getArguments().getString("paseadorId");
            isRegistrationMode = getArguments().getBoolean("isRegistrationMode", false);
        }

        if (paseadorId == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            paseadorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        // Inicializar EncryptedPreferences
        if (isRegistrationMode) {
            try {
                encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                    "WizardPaseador",
                    androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
                    requireContext(),
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Log.e(TAG, "Error al crear EncryptedPreferences", e);
                encryptedPrefs = requireContext().getSharedPreferences("WizardPaseador", android.content.Context.MODE_PRIVATE);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_horario_default, container, false);

        // Referencias a vistas
        cbLunes = view.findViewById(R.id.cbLunes);
        cbMartes = view.findViewById(R.id.cbMartes);
        cbMiercoles = view.findViewById(R.id.cbMiercoles);
        cbJueves = view.findViewById(R.id.cbJueves);
        cbViernes = view.findViewById(R.id.cbViernes);
        cbSabado = view.findViewById(R.id.cbSabado);
        cbDomingo = view.findViewById(R.id.cbDomingo);

        tvHoraInicio = view.findViewById(R.id.tvHoraInicio);
        tvHoraFin = view.findViewById(R.id.tvHoraFin);

        btnSeleccionarTodos = view.findViewById(R.id.btnSeleccionarTodos);
        btnDeseleccionarTodos = view.findViewById(R.id.btnDeseleccionarTodos);
        btnGuardar = view.findViewById(R.id.btnGuardar);
        btnCancelar = view.findViewById(R.id.btnCancelar);

        // Configurar valores iniciales
        tvHoraInicio.setText(horaInicio);
        tvHoraFin.setText(horaFin);

        // Por defecto, seleccionar Lun-Vie
        cbLunes.setChecked(true);
        cbMartes.setChecked(true);
        cbMiercoles.setChecked(true);
        cbJueves.setChecked(true);
        cbViernes.setChecked(true);

        setupListeners();
        cargarHorarioExistente();

        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCancelable(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getDialog().getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

            // Agregar animación suave de entrada
            android.view.animation.Animation slideIn = android.view.animation.AnimationUtils.loadAnimation(
                requireContext(),
                android.R.anim.slide_in_left
            );
            slideIn.setDuration(300);
            getView().startAnimation(slideIn);
        }
    }

    private void setupListeners() {
        // Seleccionar hora de inicio
        tvHoraInicio.setOnClickListener(v -> mostrarTimePickerInicio());

        // Seleccionar hora de fin
        tvHoraFin.setOnClickListener(v -> mostrarTimePickerFin());

        // Seleccionar todos
        btnSeleccionarTodos.setOnClickListener(v -> {
            cbLunes.setChecked(true);
            cbMartes.setChecked(true);
            cbMiercoles.setChecked(true);
            cbJueves.setChecked(true);
            cbViernes.setChecked(true);
            cbSabado.setChecked(true);
            cbDomingo.setChecked(true);
        });

        // Deseleccionar todos
        btnDeseleccionarTodos.setOnClickListener(v -> {
            cbLunes.setChecked(false);
            cbMartes.setChecked(false);
            cbMiercoles.setChecked(false);
            cbJueves.setChecked(false);
            cbViernes.setChecked(false);
            cbSabado.setChecked(false);
            cbDomingo.setChecked(false);
        });

        // Guardar
        btnGuardar.setOnClickListener(v -> guardarHorarioDefault());

        // Cancelar
        btnCancelar.setOnClickListener(v -> dismiss());
    }

    private void mostrarTimePickerInicio() {
        Calendar calendar = Calendar.getInstance();
        String[] partes = horaInicio.split(":");
        int hora = Integer.parseInt(partes[0]);
        int minuto = Integer.parseInt(partes[1]);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
            requireContext(),
            (view, hourOfDay, minute) -> {
                horaInicio = String.format("%02d:%02d", hourOfDay, minute);
                tvHoraInicio.setText(horaInicio);
            },
            hora,
            minuto,
            true // Formato 24 horas
        );
        timePickerDialog.show();
    }

    private void mostrarTimePickerFin() {
        Calendar calendar = Calendar.getInstance();
        String[] partes = horaFin.split(":");
        int hora = Integer.parseInt(partes[0]);
        int minuto = Integer.parseInt(partes[1]);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
            requireContext(),
            (view, hourOfDay, minute) -> {
                horaFin = String.format("%02d:%02d", hourOfDay, minute);
                tvHoraFin.setText(horaFin);
            },
            hora,
            minuto,
            true
        );
        timePickerDialog.show();
    }

    private void cargarHorarioExistente() {
        if (paseadorId == null) return;

        if (isRegistrationMode) {
            cargarHorarioDesdePrefs();
        } else {
            db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horario_default")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && getView() != null) {
                        // Cargar configuración existente
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null) {
                            cargarDiaDesdeFirestore(data, "lunes", cbLunes);
                            cargarDiaDesdeFirestore(data, "martes", cbMartes);
                            cargarDiaDesdeFirestore(data, "miercoles", cbMiercoles);
                            cargarDiaDesdeFirestore(data, "jueves", cbJueves);
                            cargarDiaDesdeFirestore(data, "viernes", cbViernes);
                            cargarDiaDesdeFirestore(data, "sabado", cbSabado);
                            cargarDiaDesdeFirestore(data, "domingo", cbDomingo);

                            // Cargar horas del primer día disponible
                            for (String dia : new String[]{"lunes", "martes", "miercoles", "jueves", "viernes"}) {
                                Map<String, Object> diaData = (Map<String, Object>) data.get(dia);
                                if (diaData != null && Boolean.TRUE.equals(diaData.get("disponible"))) {
                                    String inicio = (String) diaData.get("hora_inicio");
                                    String fin = (String) diaData.get("hora_fin");
                                    if (inicio != null && fin != null) {
                                        horaInicio = inicio;
                                        horaFin = fin;
                                        tvHoraInicio.setText(horaInicio);
                                        tvHoraFin.setText(horaFin);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
        }
    }

    private void cargarHorarioDesdePrefs() {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String horarioJson = encryptedPrefs.getString("disponibilidad_horario_default", null);

            if (horarioJson != null && getView() != null) {
                Map<String, Object> data = gson.fromJson(horarioJson, Map.class);
                if (data != null) {
                    cargarDiaDesdeFirestore(data, "lunes", cbLunes);
                    cargarDiaDesdeFirestore(data, "martes", cbMartes);
                    cargarDiaDesdeFirestore(data, "miercoles", cbMiercoles);
                    cargarDiaDesdeFirestore(data, "jueves", cbJueves);
                    cargarDiaDesdeFirestore(data, "viernes", cbViernes);
                    cargarDiaDesdeFirestore(data, "sabado", cbSabado);
                    cargarDiaDesdeFirestore(data, "domingo", cbDomingo);

                    // Cargar horas
                    for (String dia : new String[]{"lunes", "martes", "miercoles", "jueves", "viernes"}) {
                        Map<String, Object> diaData = (Map<String, Object>) data.get(dia);
                        if (diaData != null && Boolean.TRUE.equals(diaData.get("disponible"))) {
                            String inicio = (String) diaData.get("hora_inicio");
                            String fin = (String) diaData.get("hora_fin");
                            if (inicio != null && fin != null) {
                                horaInicio = inicio;
                                horaFin = fin;
                                tvHoraInicio.setText(horaInicio);
                                tvHoraFin.setText(horaFin);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al cargar horario desde prefs", e);
        }
    }

    private void cargarDiaDesdeFirestore(Map<String, Object> data, String dia, CheckBox checkBox) {
        Map<String, Object> diaData = (Map<String, Object>) data.get(dia);
        if (diaData != null) {
            Boolean disponible = (Boolean) diaData.get("disponible");
            checkBox.setChecked(disponible != null && disponible);
        }
    }

    private void guardarHorarioDefault() {
        // Validaciones
        if (!validarDatos()) {
            return;
        }

        // Crear mapa de horarios
        Map<String, Object> horarioDefault = new HashMap<>();

        horarioDefault.put("lunes", crearMapaDia(cbLunes.isChecked()));
        horarioDefault.put("martes", crearMapaDia(cbMartes.isChecked()));
        horarioDefault.put("miercoles", crearMapaDia(cbMiercoles.isChecked()));
        horarioDefault.put("jueves", crearMapaDia(cbJueves.isChecked()));
        horarioDefault.put("viernes", crearMapaDia(cbViernes.isChecked()));
        horarioDefault.put("sabado", crearMapaDia(cbSabado.isChecked()));
        horarioDefault.put("domingo", crearMapaDia(cbDomingo.isChecked()));

        horarioDefault.put("fecha_creacion", com.google.firebase.firestore.FieldValue.serverTimestamp());
        horarioDefault.put("ultima_actualizacion", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (isRegistrationMode) {
            // Modo registro: guardar a EncryptedPreferences
            guardarHorarioEnPrefs(horarioDefault);
        } else {
            // Modo edición: guardar a Firestore
            db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horario_default")
                .set(horarioDefault)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "✓ Horario guardado exitosamente", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onHorarioGuardado();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al guardar horario en Firestore", e);
                    Toast.makeText(requireContext(), "❌ No se pudo guardar el horario. Verifique su conexión e intente nuevamente.", Toast.LENGTH_LONG).show();
                });
        }
    }

    private void guardarHorarioEnPrefs(Map<String, Object> horarioDefault) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String horarioJson = gson.toJson(horarioDefault);

            SharedPreferences.Editor editor = encryptedPrefs.edit();
            editor.putString("disponibilidad_horario_default", horarioJson);
            editor.putBoolean("disponibilidad_completa", true);
            editor.apply();

            Toast.makeText(requireContext(), "✓ Horario guardado exitosamente", Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onHorarioGuardado();
            }
            dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar horario en prefs", e);
            Toast.makeText(requireContext(), "❌ No se pudo guardar el horario. Por favor, intente nuevamente.", Toast.LENGTH_LONG).show();
        }
    }

    private Map<String, Object> crearMapaDia(boolean disponible) {
        Map<String, Object> dia = new HashMap<>();
        dia.put("disponible", disponible);
        dia.put("hora_inicio", disponible ? horaInicio : null);
        dia.put("hora_fin", disponible ? horaFin : null);
        return dia;
    }

    private boolean validarDatos() {
        // Al menos un día debe estar seleccionado
        if (!cbLunes.isChecked() && !cbMartes.isChecked() && !cbMiercoles.isChecked() &&
            !cbJueves.isChecked() && !cbViernes.isChecked() && !cbSabado.isChecked() &&
            !cbDomingo.isChecked()) {
            Toast.makeText(requireContext(), "⚠️ Debe seleccionar al menos un día disponible", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar que hora fin > hora inicio
        if (horaInicio.compareTo(horaFin) >= 0) {
            Toast.makeText(requireContext(), "⚠️ La hora de fin debe ser posterior a la hora de inicio", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar diferencia mínima de 1 hora
        String[] inicioPartes = horaInicio.split(":");
        String[] finPartes = horaFin.split(":");
        int minutosInicio = Integer.parseInt(inicioPartes[0]) * 60 + Integer.parseInt(inicioPartes[1]);
        int minutosFin = Integer.parseInt(finPartes[0]) * 60 + Integer.parseInt(finPartes[1]);
        int diferencia = minutosFin - minutosInicio;

        if (diferencia < 60) {
            Toast.makeText(requireContext(), "⚠️ El horario debe tener al menos 1 hora de duración", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar que no sea después de 11 PM (23:00)
        if (horaFin.compareTo("23:00") > 0) {
            Toast.makeText(requireContext(), "⚠️ La hora de fin no puede ser después de las 11:00 PM", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }
}
