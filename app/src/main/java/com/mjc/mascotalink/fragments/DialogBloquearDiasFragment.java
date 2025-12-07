package com.mjc.mascotalink.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.CalendarioAdapter;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.modelo.Bloqueo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Diálogo para bloquear días específicos
 */
public class DialogBloquearDiasFragment extends DialogFragment {

    private GridView gridCalendario;
    private CalendarioAdapter calendarioAdapter;
    private TextView tvMesActual;
    private Button btnMesAnterior, btnMesSiguiente;
    private EditText etRazon;
    private RadioGroup rgTipoBloqueo;
    private RadioButton rbDiaCompleto, rbManana, rbTarde;
    private CheckBox cbRepetir;
    private RadioGroup rgFrecuencia;
    private Button btnBloquear, btnCancelar;

    private Calendar mesActual;
    private Set<Date> fechasSeleccionadas = new HashSet<>();

    private FirebaseFirestore db;
    private String paseadorId;

    public interface OnDiasBloqueadosListener {
        void onDiasBloqueados(int cantidad);
    }

    private OnDiasBloqueadosListener listener;

    public static DialogBloquearDiasFragment newInstance(String paseadorId) {
        DialogBloquearDiasFragment fragment = new DialogBloquearDiasFragment();
        Bundle args = new Bundle();
        args.putString("paseadorId", paseadorId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDiasBloqueadosListener(OnDiasBloqueadosListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        mesActual = Calendar.getInstance();

        if (getArguments() != null) {
            paseadorId = getArguments().getString("paseadorId");
        }

        if (paseadorId == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            paseadorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_bloquear_dias, container, false);

        // Referencias
        gridCalendario = view.findViewById(R.id.gridCalendario);
        tvMesActual = view.findViewById(R.id.tvMesActual);
        btnMesAnterior = view.findViewById(R.id.btnMesAnterior);
        btnMesSiguiente = view.findViewById(R.id.btnMesSiguiente);
        etRazon = view.findViewById(R.id.etRazon);
        rgTipoBloqueo = view.findViewById(R.id.rgTipoBloqueo);
        rbDiaCompleto = view.findViewById(R.id.rbDiaCompleto);
        rbManana = view.findViewById(R.id.rbManana);
        rbTarde = view.findViewById(R.id.rbTarde);
        cbRepetir = view.findViewById(R.id.cbRepetir);
        rgFrecuencia = view.findViewById(R.id.rgFrecuencia);
        btnBloquear = view.findViewById(R.id.btnBloquear);
        btnCancelar = view.findViewById(R.id.btnCancelar);

        // Configurar por defecto
        rbDiaCompleto.setChecked(true);
        rgFrecuencia.setVisibility(View.GONE);

        setupListeners();
        actualizarCalendario();

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
        btnMesAnterior.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, -1);
            actualizarCalendario();
        });

        btnMesSiguiente.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, 1);
            actualizarCalendario();
        });

        cbRepetir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rgFrecuencia.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnBloquear.setOnClickListener(v -> bloquearFechas());
        btnCancelar.setOnClickListener(v -> dismiss());
    }

    private void actualizarCalendario() {
        // Actualizar título del mes
        String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                         "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        tvMesActual.setText(meses[mesActual.get(Calendar.MONTH)] + " " + mesActual.get(Calendar.YEAR));

        // Generar lista de fechas para el calendario
        List<Date> fechasDelMes = generarFechasDelMes(mesActual);

        // Configurar adapter del calendario
        calendarioAdapter = new CalendarioAdapter(requireContext(), fechasDelMes, mesActual, (date, position) -> {
            // Callback cuando se selecciona una fecha
            if (date != null && !esFechaPasada(date)) {
                Date fechaNormalizada = normalizarFecha(date);
                if (fechasSeleccionadas.contains(fechaNormalizada)) {
                    fechasSeleccionadas.remove(fechaNormalizada);
                } else {
                    fechasSeleccionadas.add(fechaNormalizada);
                }
                calendarioAdapter.setFechasSeleccionadas(fechasSeleccionadas);
            }
        });
        calendarioAdapter.setSeleccionMultiple(true);
        calendarioAdapter.setFechasSeleccionadas(fechasSeleccionadas);

        gridCalendario.setAdapter(calendarioAdapter);
    }

    private List<Date> generarFechasDelMes(Calendar mes) {
        List<Date> fechas = new ArrayList<>();
        Calendar cal = (Calendar) mes.clone();

        // Primer día del mes
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int primerDiaSemana = cal.get(Calendar.DAY_OF_WEEK); // 1=Dom, 2=Lun, ...

        // Calcular días vacíos al inicio (para alinear con día de la semana)
        int diasVacios = primerDiaSemana - 1; // 0=Dom, 1=Lun, ...
        for (int i = 0; i < diasVacios; i++) {
            fechas.add(null); // Días vacíos
        }

        // Agregar todos los días del mes
        int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int dia = 1; dia <= diasDelMes; dia++) {
            cal.set(Calendar.DAY_OF_MONTH, dia);
            fechas.add(cal.getTime());
        }

        return fechas;
    }

    private Date normalizarFecha(Date fecha) {
        if (fecha == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private boolean esFechaPasada(Date fecha) {
        Calendar hoy = Calendar.getInstance();
        hoy.set(Calendar.HOUR_OF_DAY, 0);
        hoy.set(Calendar.MINUTE, 0);
        hoy.set(Calendar.SECOND, 0);
        hoy.set(Calendar.MILLISECOND, 0);

        return fecha.before(hoy.getTime());
    }

    private void bloquearFechas() {
        if (fechasSeleccionadas.isEmpty()) {
            Toast.makeText(requireContext(), "Selecciona al menos una fecha", Toast.LENGTH_SHORT).show();
            return;
        }

        String razon = etRazon.getText().toString().trim();
        String tipo;

        if (rbDiaCompleto.isChecked()) {
            tipo = Bloqueo.TIPO_DIA_COMPLETO;
        } else if (rbManana.isChecked()) {
            tipo = Bloqueo.TIPO_MANANA;
        } else {
            tipo = Bloqueo.TIPO_TARDE;
        }

        boolean repetir = cbRepetir.isChecked();
        String frecuencia = null;

        if (repetir) {
            int selectedId = rgFrecuencia.getCheckedRadioButtonId();
            if (selectedId == R.id.rbSemanal) {
                frecuencia = Bloqueo.FRECUENCIA_SEMANAL;
            } else if (selectedId == R.id.rbMensual) {
                frecuencia = Bloqueo.FRECUENCIA_MENSUAL;
            }
        }

        // Guardar cada fecha seleccionada
        int contador = 0;
        for (Date fecha : fechasSeleccionadas) {
            Bloqueo bloqueo = new Bloqueo();
            bloqueo.setFecha(new Timestamp(fecha));
            bloqueo.setTipo(tipo);
            bloqueo.setRazon(razon.isEmpty() ? "Día bloqueado" : razon);
            bloqueo.setRepetir(repetir);
            bloqueo.setFrecuencia_repeticion(frecuencia);
            bloqueo.setActivo(true);

            final String finalFrecuencia = frecuencia;
            db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .add(bloqueo)
                .addOnSuccessListener(documentReference -> {
                    // Éxito
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error al bloquear: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });

            contador++;
        }

        final int totalBloqueados = contador;
        Toast.makeText(requireContext(),
            getString(R.string.bloqueo_confirmacion, totalBloqueados),
            Toast.LENGTH_SHORT).show();

        if (listener != null) {
            listener.onDiasBloqueados(totalBloqueados);
        }

        dismiss();
    }
}
