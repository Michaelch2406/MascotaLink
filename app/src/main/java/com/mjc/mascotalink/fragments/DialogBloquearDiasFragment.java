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
import com.mjc.mascotalink.utils.CalendarioUtils;

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
    private androidx.core.widget.NestedScrollView nestedScrollView;
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
        nestedScrollView = view.findViewById(R.id.nestedScrollView);
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

        // Crear el adapter
        calendarioAdapter = new CalendarioAdapter(requireContext(), fechasDelMes, mesActual, (date, position) -> {
            // El adapter ya maneja la selección internamente en modo múltiple
            // Sincronizar nuestro set local con el del adapter
            if (calendarioAdapter != null) {
                fechasSeleccionadas = new HashSet<>(calendarioAdapter.getFechasSeleccionadas());
            }
        });

        // IMPORTANTE: Configurar modo antes de setear el adapter
        calendarioAdapter.setSeleccionMultiple(true);
        calendarioAdapter.setEsVistaPaseador(true);
        calendarioAdapter.setFechasSeleccionadas(fechasSeleccionadas);

        // Evitar que el NestedScrollView intercepte los eventos touch del calendario
        gridCalendario.setOnTouchListener((v, event) -> {
            // Deshabilitar el scroll del NestedScrollView mientras se toca el calendario
            int action = event.getAction();
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Deshabilitar scroll cuando el usuario toca el calendario
                    if (nestedScrollView != null) {
                        nestedScrollView.requestDisallowInterceptTouchEvent(true);
                    }
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // Re-habilitar scroll cuando el usuario suelta
                    if (nestedScrollView != null) {
                        nestedScrollView.requestDisallowInterceptTouchEvent(false);
                    }
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        gridCalendario.setAdapter(calendarioAdapter);

        // Cargar bloqueos existentes del mes para mostrar en calendario
        cargarBloqueosDelMes();
    }

    private void cargarBloqueosDelMes() {
        if (paseadorId == null) return;

        // Calcular rango del mes
        Calendar inicioMes = (Calendar) mesActual.clone();
        inicioMes.set(Calendar.DAY_OF_MONTH, 1);
        inicioMes.set(Calendar.HOUR_OF_DAY, 0);
        inicioMes.set(Calendar.MINUTE, 0);
        inicioMes.set(Calendar.SECOND, 0);
        Timestamp inicioRango = new Timestamp(inicioMes.getTime());

        Calendar finMes = (Calendar) mesActual.clone();
        finMes.set(Calendar.DAY_OF_MONTH, mesActual.getActualMaximum(Calendar.DAY_OF_MONTH));
        finMes.set(Calendar.HOUR_OF_DAY, 23);
        finMes.set(Calendar.MINUTE, 59);
        finMes.set(Calendar.SECOND, 59);
        Timestamp finRango = new Timestamp(finMes.getTime());

        Set<Date> bloqueadosCompletos = new HashSet<>();
        Set<Date> bloqueadosParciales = new HashSet<>();

        db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioRango)
                .whereLessThanOrEqualTo("fecha", finRango)
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Bloqueo bloqueo = doc.toObject(Bloqueo.class);
                        if (bloqueo != null && bloqueo.getFecha() != null && bloqueo.getTipo() != null) {
                            Date fechaNormalizada = normalizarFecha(bloqueo.getFecha().toDate());
                            if (Bloqueo.TIPO_DIA_COMPLETO.equals(bloqueo.getTipo())) {
                                bloqueadosCompletos.add(fechaNormalizada);
                            } else {
                                bloqueadosParciales.add(fechaNormalizada);
                            }
                        }
                    }

                    // Actualizar adapter con bloqueos existentes
                    if (calendarioAdapter != null) {
                        calendarioAdapter.setDiasBloqueados(bloqueadosCompletos);
                        calendarioAdapter.setDiasParciales(bloqueadosParciales);
                        calendarioAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("DialogBloquearDias", "Error cargando bloqueos", e);
                });
    }

    private List<Date> generarFechasDelMes(Calendar mes) {
        return CalendarioUtils.generarFechasDelMes(mes);
    }

    private Date normalizarFecha(Date fecha) {
        return CalendarioUtils.normalizarFecha(fecha);
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
                    String mensajeError = "Error al bloquear";

                    // Detectar si es un problema de permisos
                    if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                        mensajeError = "Permiso denegado. Verifica las reglas de Firestore.";
                    } else if (e.getMessage() != null) {
                        mensajeError = "Error: " + e.getMessage();
                    }

                    Toast.makeText(requireContext(), mensajeError, Toast.LENGTH_LONG).show();
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
