package com.mjc.mascotalink.fragments;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.modelo.HorarioEspecial;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Diálogo para configurar horario especial para un día específico
 */
public class DialogHorarioEspecialFragment extends DialogFragment {

    private TextView tvFechaSeleccionada;
    private TextView tvHoraInicio, tvHoraFin;
    private EditText etNota;
    private CheckBox cbSoloEsteDia;
    private Button btnGuardar, btnCancelar;

    private Date fechaSeleccionada;
    private String horaInicio = "09:00";
    private String horaFin = "18:00";

    private FirebaseFirestore db;
    private String paseadorId;

    public interface OnHorarioEspecialGuardadoListener {
        void onHorarioEspecialGuardado();
    }

    private OnHorarioEspecialGuardadoListener listener;

    public static DialogHorarioEspecialFragment newInstance(String paseadorId, Date fecha) {
        DialogHorarioEspecialFragment fragment = new DialogHorarioEspecialFragment();
        Bundle args = new Bundle();
        args.putString("paseadorId", paseadorId);
        if (fecha != null) {
            args.putLong("fecha", fecha.getTime());
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnHorarioEspecialGuardadoListener(OnHorarioEspecialGuardadoListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            paseadorId = getArguments().getString("paseadorId");
            long fechaMillis = getArguments().getLong("fecha", -1);
            if (fechaMillis != -1) {
                fechaSeleccionada = new Date(fechaMillis);
            }
        }

        if (paseadorId == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            paseadorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (fechaSeleccionada == null) {
            fechaSeleccionada = new Date();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_horario_especial, container, false);

        // Referencias
        tvFechaSeleccionada = view.findViewById(R.id.tvFechaSeleccionada);
        tvHoraInicio = view.findViewById(R.id.tvHoraInicio);
        tvHoraFin = view.findViewById(R.id.tvHoraFin);
        etNota = view.findViewById(R.id.etNota);
        cbSoloEsteDia = view.findViewById(R.id.cbSoloEsteDia);
        btnGuardar = view.findViewById(R.id.btnGuardar);
        btnCancelar = view.findViewById(R.id.btnCancelar);

        // Configurar valores iniciales
        actualizarFechaTexto();
        tvHoraInicio.setText(horaInicio);
        tvHoraFin.setText(horaFin);
        cbSoloEsteDia.setChecked(true);

        setupListeners();

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
        }
    }

    private void setupListeners() {
        // Seleccionar fecha
        tvFechaSeleccionada.setOnClickListener(v -> mostrarDatePicker());

        // Seleccionar hora de inicio
        tvHoraInicio.setOnClickListener(v -> mostrarTimePickerInicio());

        // Seleccionar hora de fin
        tvHoraFin.setOnClickListener(v -> mostrarTimePickerFin());

        // Guardar
        btnGuardar.setOnClickListener(v -> guardarHorarioEspecial());

        // Cancelar
        btnCancelar.setOnClickListener(v -> dismiss());
    }

    private void mostrarDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(fechaSeleccionada);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                Calendar nuevaFecha = Calendar.getInstance();
                nuevaFecha.set(year, month, dayOfMonth);
                fechaSeleccionada = nuevaFecha.getTime();
                actualizarFechaTexto();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Solo permitir fechas futuras
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void actualizarFechaTexto() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        String fechaFormateada = sdf.format(fechaSeleccionada);
        // Capitalizar primera letra
        fechaFormateada = fechaFormateada.substring(0, 1).toUpperCase() + fechaFormateada.substring(1);
        tvFechaSeleccionada.setText(fechaFormateada);
    }

    private void mostrarTimePickerInicio() {
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
            true
        );
        timePickerDialog.show();
    }

    private void mostrarTimePickerFin() {
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

    private void guardarHorarioEspecial() {
        // Validaciones
        if (!validarDatos()) {
            return;
        }

        String nota = etNota.getText().toString().trim();

        // Crear objeto HorarioEspecial
        HorarioEspecial horarioEspecial = new HorarioEspecial();
        horarioEspecial.setFecha(new Timestamp(fechaSeleccionada));
        horarioEspecial.setHora_inicio(horaInicio);
        horarioEspecial.setHora_fin(horaFin);
        horarioEspecial.setNota(nota.isEmpty() ? "Horario especial" : nota);
        horarioEspecial.setActivo(true);

        // Guardar en Firestore
        db.collection("paseadores").document(paseadorId)
            .collection("disponibilidad").document("horarios_especiales")
            .collection("items")
            .add(horarioEspecial)
            .addOnSuccessListener(documentReference -> {
                Toast.makeText(requireContext(), "Horario especial guardado", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onHorarioEspecialGuardado();
                }
                dismiss();
            })
            .addOnFailureListener(e -> {
                String mensajeError = "Error al guardar";

                // Detectar si es un problema de permisos
                if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                    mensajeError = "Permiso denegado. Verifica las reglas de Firestore.";
                } else if (e.getMessage() != null) {
                    mensajeError = "Error: " + e.getMessage();
                }

                Toast.makeText(requireContext(), mensajeError, Toast.LENGTH_LONG).show();
            });
    }

    private boolean validarDatos() {
        // Validar que hora fin > hora inicio
        if (horaInicio.compareTo(horaFin) >= 0) {
            Toast.makeText(requireContext(), R.string.horario_default_validacion_horario, Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar diferencia mínima de 1 hora
        String[] inicioPartes = horaInicio.split(":");
        String[] finPartes = horaFin.split(":");
        int minutosInicio = Integer.parseInt(inicioPartes[0]) * 60 + Integer.parseInt(inicioPartes[1]);
        int minutosFin = Integer.parseInt(finPartes[0]) * 60 + Integer.parseInt(finPartes[1]);
        int diferencia = minutosFin - minutosInicio;

        if (diferencia < 60) {
            Toast.makeText(requireContext(), R.string.horario_default_validacion_diferencia, Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar que la fecha sea futura
        Calendar hoy = Calendar.getInstance();
        hoy.set(Calendar.HOUR_OF_DAY, 0);
        hoy.set(Calendar.MINUTE, 0);
        hoy.set(Calendar.SECOND, 0);
        hoy.set(Calendar.MILLISECOND, 0);

        if (fechaSeleccionada.before(hoy.getTime())) {
            Toast.makeText(requireContext(), "La fecha debe ser futura", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }
}
