package com.mjc.mascotalink;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class MascotaSaludActivity extends AppCompatActivity {

    private static final String TAG = "MascotaSaludActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView ivBack;
    private TextView tvVacunasEstado, tvDesparasitacionEstado, tvUltimaVisita;
    private TextView tvCondicionesMedicas, tvMedicamentos;
    private TextView tvVeterinarioNombre, tvVeterinarioTelefono;

    private String duenoId;
    private String mascotaId;
    private ListenerRegistration mascotaListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_salud);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        duenoId = getIntent().getStringExtra("dueno_id");
        mascotaId = getIntent().getStringExtra("mascota_id");

        if (duenoId == null && mAuth.getCurrentUser() != null) {
            duenoId = mAuth.getCurrentUser().getUid();
        }

        if (mascotaId == null) {
            Toast.makeText(this, "Error: No se pudo cargar los datos", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        cargarDatosSalud();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        tvVacunasEstado = findViewById(R.id.tv_vacunas_estado);
        tvDesparasitacionEstado = findViewById(R.id.tv_desparasitacion_estado);
        tvUltimaVisita = findViewById(R.id.tv_ultima_visita);
        tvCondicionesMedicas = findViewById(R.id.tv_condiciones_medicas);
        tvMedicamentos = findViewById(R.id.tv_medicamentos);
        tvVeterinarioNombre = findViewById(R.id.tv_veterinario_nombre);
        tvVeterinarioTelefono = findViewById(R.id.tv_veterinario_telefono);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                showToast("Próximamente: Inicio");
                return true;
            } else if (itemId == R.id.menu_search) {
                showToast("Próximamente: Buscar");
                return true;
            } else if (itemId == R.id.menu_walks) {
                showToast("Próximamente: Paseos");
                return true;
            } else if (itemId == R.id.menu_messages) {
                showToast("Próximamente: Mensajes");
                return true;
            } else if (itemId == R.id.menu_perfil) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
    }

    @SuppressWarnings("unchecked")
    private void cargarDatosSalud() {
        if (mascotaListener != null) mascotaListener.remove();
        mascotaListener = db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId)
                .addSnapshotListener((document, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        Toast.makeText(this, "Error al cargar los datos.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (document != null && document.exists()) {
                        // Obtener el mapa de salud
                        Map<String, Object> salud = (Map<String, Object>) document.get("salud");
                        if (salud != null) {
                            // Vacunas al día
                            Boolean vacunasAlDia = (Boolean) salud.get("vacunas_al_dia");
                            tvVacunasEstado.setText(vacunasAlDia != null && vacunasAlDia ? "Vacunas al día" : "Vacunas pendientes");

                            // Desparasitación al día
                            Boolean desparasitacionAlDia = (Boolean) salud.get("desparasitacion_aldia");
                            tvDesparasitacionEstado.setText(desparasitacionAlDia != null && desparasitacionAlDia ? "Desparasitación al día" : "Desparasitación pendiente");

                            // Última visita al veterinario
                            Timestamp ultimaVisita = (Timestamp) salud.get("ultima_visita_vet");
                            if (ultimaVisita != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
                                tvUltimaVisita.setText(sdf.format(ultimaVisita.toDate()));
                            } else {
                                tvUltimaVisita.setText("No registrada");
                            }

                            // Condiciones médicas
                            String condiciones = (String) salud.get("condiciones_medicas");
                            tvCondicionesMedicas.setText(condiciones != null && !condiciones.isEmpty() ? condiciones : "Ninguna");

                            // Medicamentos actuales
                            String medicamentos = (String) salud.get("medicamentos_actuales");
                            tvMedicamentos.setText(medicamentos != null && !medicamentos.isEmpty() ? medicamentos : "Ninguno");

                            // Veterinario nombre
                            String vetNombre = (String) salud.get("veterinario_nombre");
                            tvVeterinarioNombre.setText(vetNombre != null && !vetNombre.isEmpty() ? vetNombre : "No especificado");

                            // Veterinario teléfono
                            String vetTelefono = (String) salud.get("veterinario_telefono");
                            tvVeterinarioTelefono.setText(vetTelefono != null && !vetTelefono.isEmpty() ? vetTelefono : "No especificado");
                        } else {
                            // Si no hay datos de salud
                            tvVacunasEstado.setText("No especificado");
                            tvDesparasitacionEstado.setText("No especificado");
                            tvUltimaVisita.setText("No registrada");
                            tvCondicionesMedicas.setText("Ninguna");
                            tvMedicamentos.setText("Ninguno");
                            tvVeterinarioNombre.setText("No especificado");
                            tvVeterinarioTelefono.setText("No especificado");
                        }
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mascotaListener != null) {
            mascotaListener.remove();
        }
    }
}
