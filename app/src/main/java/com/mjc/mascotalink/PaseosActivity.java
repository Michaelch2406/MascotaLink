package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.ConfirmarPagoActivity;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaseosActivity extends AppCompatActivity {

    private static final String TAG = "PaseosActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String userRole;

    // Views
    private TextView tabAceptados, tabProgramados, tabEnProgreso, tabCompletados, tabCancelados;
    private RecyclerView rvPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmptyView;
    private Button btnReservarPaseo;
    private BottomNavigationView bottomNav;

    // Adapter
    private PaseosAdapter paseosAdapter;
    private List<Paseo> paseosList;

    // Estado actual
    private String estadoActual = ReservaEstadoValidator.ESTADO_ACEPTADO; // Mostrar primero las reservas aceptadas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseos);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = currentUser.getUid();

        initViews();
        setupSwipeRefresh();
        fetchUserRoleAndSetupUI();
    }

    private void initViews() {
        tabAceptados = findViewById(R.id.tab_aceptados);
        tabProgramados = findViewById(R.id.tab_programados);
        tabEnProgreso = findViewById(R.id.tab_en_progreso);
        tabCompletados = findViewById(R.id.tab_completados);
        tabCancelados = findViewById(R.id.tab_cancelados);
        rvPaseos = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmptyView = findViewById(R.id.ll_empty_view);
        btnReservarPaseo = findViewById(R.id.btn_reservar_paseo);
        bottomNav = findViewById(R.id.bottom_nav);

        paseosList = new ArrayList<>();
    }

    private void fetchUserRoleAndSetupUI() {
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        userRole = documentSnapshot.getString("rol");
                        if (userRole != null) {
                            setupRecyclerView(userRole);
                            setupRoleSpecificUI(userRole);
                            cargarPaseos(userRole);
                        } else {
                            handleRoleError();
                        }
                    } else {
                        handleRoleError();
                    }
                })
                .addOnFailureListener(e -> handleRoleError());
    }

    private void handleRoleError() {
        Toast.makeText(this, "No se pudo verificar el rol del usuario.", Toast.LENGTH_LONG).show();
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setupRoleSpecificUI(String role) {
        setupTabs(role);
        BottomNavManager.setupBottomNav(this, bottomNav, role, R.id.menu_walks);
        btnReservarPaseo.setVisibility("PASEADOR".equalsIgnoreCase(role) ? View.GONE : View.VISIBLE);
    }

    private void setupTabs(String role) {
        tabAceptados.setOnClickListener(v -> cambiarTab(ReservaEstadoValidator.ESTADO_ACEPTADO, tabAceptados, role));
        tabProgramados.setOnClickListener(v -> cambiarTab(ReservaEstadoValidator.ESTADO_CONFIRMADO, tabProgramados, role));
        tabEnProgreso.setOnClickListener(v -> cambiarTab("EN_CURSO", tabEnProgreso, role));
        tabCompletados.setOnClickListener(v -> cambiarTab("COMPLETADO", tabCompletados, role));
        tabCancelados.setOnClickListener(v -> cambiarTab("CANCELADO", tabCancelados, role));

        btnReservarPaseo.setOnClickListener(v -> {
            Intent intent = new Intent(PaseosActivity.this, BusquedaPaseadoresActivity.class);
            startActivity(intent);
        });

        // Seleccionar tab inicial
        cambiarTab(estadoActual, tabAceptados, role);
    }

    private void cambiarTab(String nuevoEstado, TextView tabSeleccionado, String role) {
        resetearTabs();
        tabSeleccionado.setTextColor(ContextCompat.getColor(this, R.color.blue_primary));
        tabSeleccionado.setTypeface(null, android.graphics.Typeface.BOLD);
        tabSeleccionado.setBackgroundResource(R.drawable.tab_selected_underline);
        estadoActual = nuevoEstado;
        cargarPaseos(role);
    }

    private void resetearTabs() {
        TextView[] tabs = {tabAceptados, tabProgramados, tabEnProgreso, tabCompletados, tabCancelados};
        for (TextView tab : tabs) {
            tab.setTextColor(ContextCompat.getColor(this, R.color.gray_text));
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
            tab.setBackground(null);
        }
    }

    private void setupRecyclerView(String role) {
        rvPaseos.setLayoutManager(new LinearLayoutManager(this));
        paseosAdapter = new PaseosAdapter(this, paseosList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(PaseosActivity.Paseo paseo) {
                // Abrir detalles del paseo
                Toast.makeText(PaseosActivity.this, "Abrir detalles de " + paseo.getPaseadorNombre(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerUbicacionClick(PaseosActivity.Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Ver ubicación en mapa", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onContactarClick(PaseosActivity.Paseo paseo) {
                Toast.makeText(PaseosActivity.this, "Abrir chat con paseador", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCalificarClick(PaseosActivity.Paseo paseo) {
                mostrarDialogCalificacion(paseo);
            }

            @Override
            public void onVerMotivoClick(PaseosActivity.Paseo paseo) {
                mostrarDialogMotivoCancelacion(paseo);
            }
            @Override
            public void onProcesarPagoClick(PaseosActivity.Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ConfirmarPagoActivity.class);
                intent.putExtra("reserva_id", paseo.getReservaId());
                intent.putExtra("costo_total", paseo.getCostoTotal());
                intent.putExtra("paseador_nombre", paseo.getPaseadorNombre());
                intent.putExtra("mascota_nombre", paseo.getMascotaNombre());
                intent.putExtra("fecha_reserva", paseo.getFechaFormateada());
                intent.putExtra("hora_reserva", paseo.getHoraFormateada());
                intent.putExtra("direccion_recogida", "Calle Principal 123, Ciudad");
                startActivity(intent);
            }
        }, role);
        rvPaseos.setAdapter(paseosAdapter);
    }


    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            if (userRole != null) {
                cargarPaseos(userRole);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    private void cargarPaseos(String role) {
        swipeRefresh.setRefreshing(true);
        paseosList.clear();

        String fieldToFilter = "PASEADOR".equalsIgnoreCase(role) ? "id_paseador" : "id_dueno";
        DocumentReference userRef = db.collection("usuarios").document(currentUserId);

        Query query = db.collection("reservas")
                .whereEqualTo(fieldToFilter, userRef)
                .orderBy("fecha", Query.Direction.DESCENDING);

        Query finalQuery = query.whereEqualTo("estado", estadoActual);

        finalQuery.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                finalizarCarga();
                return;
            }

            List<Paseo> paseosTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Paseo paseo = doc.toObject(Paseo.class);
                if (paseo == null) continue;
                paseo.setReservaId(doc.getId());
                paseo.setEstado(doc.getString("estado"));
                paseo.setEstadoPago(doc.getString("estado_pago"));
                paseosTemporales.add(paseo);

                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String mascotaId = doc.getString("id_mascota");

                tareas.add(paseadorRef != null ? paseadorRef.get() : Tasks.forResult(null));
                tareas.add(duenoRef != null ? duenoRef.get() : Tasks.forResult(null));
                if (duenoRef != null && mascotaId != null && !mascotaId.isEmpty()) {
                    tareas.add(db.collection("duenos").document(duenoRef.getId()).collection("mascotas").document(mascotaId).get());
                } else {
                    tareas.add(Tasks.forResult(null));
                }
            }

            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                for (int i = 0; i < paseosTemporales.size(); i++) {
                    Paseo paseo = paseosTemporales.get(i);

                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(i * 3);
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(i * 3 + 1);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(i * 3 + 2);

                    if (paseadorDoc != null && paseadorDoc.exists()) {
                        paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                        paseo.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                    }
                    if (duenoDoc != null && duenoDoc.exists()) {
                        paseo.setDuenoNombre(duenoDoc.getString("nombre_display"));
                    }
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        paseo.setMascotaFoto(mascotaDoc.getString("foto_perfil"));
                    } else {
                        paseo.setMascotaNombre("Mascota no encontrada");
                    }
                    paseosList.add(paseo);
                }
                if (paseosAdapter != null) {
                    paseosAdapter.notifyDataSetChanged();
                }
                finalizarCarga();
            }).addOnFailureListener(this::manejarError);
        }).addOnFailureListener(this::manejarError);
    }


    private void finalizarCarga() {
        swipeRefresh.setRefreshing(false);
        if (paseosList.isEmpty()) {
            rvPaseos.setVisibility(View.GONE);
            llEmptyView.setVisibility(View.VISIBLE);
        } else {
            rvPaseos.setVisibility(View.VISIBLE);
            llEmptyView.setVisibility(View.GONE);
        }
    }

    private void manejarError(Exception e) {
        swipeRefresh.setRefreshing(false);
        Log.e(TAG, "Error al cargar paseos: ", e);
        Toast.makeText(this, "Error al cargar paseos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        rvPaseos.setVisibility(View.GONE);
        llEmptyView.setVisibility(View.VISIBLE);
    }

    private void mostrarDialogCalificacion(Paseo paseo) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Calificar")
                .setMessage("Próximamente podrás calificar este paseo.")
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void mostrarDialogMotivoCancelacion(Paseo paseo) {
        if (isFinishing()) return;
        String motivo = paseo.getRazonCancelacion();
        if (motivo == null || motivo.isEmpty()) motivo = "No se especificó un motivo";
        new AlertDialog.Builder(this)
                .setTitle("Motivo de Cancelación")
                .setMessage(motivo)
                .setPositiveButton("Cerrar", null)
                .show();
    }


    public static class Paseo {
        private String reservaId;
        private String paseadorNombre, paseadorFoto, duenoNombre, mascotaNombre, mascotaFoto;
        private String idMascota;
        private Date fecha, horaInicio;
        private String estado, razonCancelacion, tipoReserva;
        private String estadoPago;
        private double costoTotal;
        private long duracionMinutos;

        public Paseo() {}

        // Getters
        public String getReservaId() { return reservaId; }
        public String getPaseadorNombre() { return paseadorNombre; }
        public String getPaseadorFoto() { return paseadorFoto; }
        public String getDuenoNombre() { return duenoNombre; }
        public String getMascotaNombre() { return mascotaNombre; }
        public String getMascotaFoto() { return mascotaFoto; }
        public String getIdMascota() { return idMascota; }
        public Date getFecha() { return fecha; }
        public Date getHoraInicio() { return horaInicio; }
        public String getEstado() { return estado; }
        public String getRazonCancelacion() { return razonCancelacion; }
        public String getTipoReserva() { return tipoReserva; }
        public double getCostoTotal() { return costoTotal; }
        public int getDuracionMinutos() { return (int) duracionMinutos; }
        public String getEstadoPago() { return estadoPago; }

        // Setters
        public void setReservaId(String reservaId) { this.reservaId = reservaId; }
        public void setPaseadorNombre(String paseadorNombre) { this.paseadorNombre = paseadorNombre; }
        public void setPaseadorFoto(String paseadorFoto) { this.paseadorFoto = paseadorFoto; }
        public void setDuenoNombre(String duenoNombre) { this.duenoNombre = duenoNombre; }
        public void setMascotaNombre(String mascotaNombre) { this.mascotaNombre = mascotaNombre; }
        public void setMascotaFoto(String mascotaFoto) { this.mascotaFoto = mascotaFoto; }
        public void setIdMascota(String idMascota) { this.idMascota = idMascota; }
        public void setFecha(Date fecha) { this.fecha = fecha; }
        public void setHoraInicio(Date horaInicio) { this.horaInicio = horaInicio; }
        public void setEstado(String estado) { this.estado = estado; }
        public void setRazonCancelacion(String razonCancelacion) { this.razonCancelacion = razonCancelacion; }
        public void setTipoReserva(String tipoReserva) { this.tipoReserva = tipoReserva; }
        public void setCostoTotal(double costoTotal) { this.costoTotal = costoTotal; }
        public void setDuracionMinutos(long duracionMinutos) { this.duracionMinutos = duracionMinutos; }
        public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }


        public String getFechaFormateada() {
            if (fecha == null) return "";
            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            Calendar paseoDate = Calendar.getInstance();
            paseoDate.setTime(fecha);
            if (today.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR)) return "Hoy";
            if (tomorrow.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR) && tomorrow.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR)) return "Mañana";
            return new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES")).format(fecha);
        }

        public String getHoraFormateada() {
            if (horaInicio == null) return "";
            return new SimpleDateFormat("h:mm a", Locale.US).format(horaInicio);
        }
    }
}
