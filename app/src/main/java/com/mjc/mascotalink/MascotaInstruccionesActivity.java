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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

public class MascotaInstruccionesActivity extends AppCompatActivity {

    private static final String TAG = "MascotaInstrucciones";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView ivBack;
    private TextView tvRutinaPaseo, tvTipoCorreaArnes, tvRecompensas;
    private TextView tvInstruccionesEmergencia, tvNotasAdicionales;

    private String duenoId;
    private String mascotaId;
    private ListenerRegistration mascotaListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_instrucciones);

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
        cargarDatosInstrucciones();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        tvRutinaPaseo = findViewById(R.id.tv_rutina_paseo);
        tvTipoCorreaArnes = findViewById(R.id.tv_tipo_correa_arnes);
        tvRecompensas = findViewById(R.id.tv_recompensas);
        tvInstruccionesEmergencia = findViewById(R.id.tv_instrucciones_emergencia);
        tvNotasAdicionales = findViewById(R.id.tv_notas_adicionales);

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
    private void cargarDatosInstrucciones() {
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
                        // Obtener el mapa de instrucciones
                        Map<String, Object> instrucciones = (Map<String, Object>) document.get("instrucciones");
                        if (instrucciones != null) {
                            // Rutina de paseo
                            String rutinaPaseo = (String) instrucciones.get("rutina_paseo");
                            tvRutinaPaseo.setText(rutinaPaseo != null && !rutinaPaseo.isEmpty() ? rutinaPaseo : "No especificado");

                            // Tipo de correa/arnés
                            String tipoCorrea = (String) instrucciones.get("tipo_correa_arnes");
                            tvTipoCorreaArnes.setText(tipoCorrea != null && !tipoCorrea.isEmpty() ? tipoCorrea : "No especificado");

                            // Recompensas
                            String recompensas = (String) instrucciones.get("recompensas");
                            tvRecompensas.setText(recompensas != null && !recompensas.isEmpty() ? recompensas : "No especificado");

                            // Instrucciones de emergencia
                            String instruccionesEmergencia = (String) instrucciones.get("instrucciones_emergencia");
                            tvInstruccionesEmergencia.setText(instruccionesEmergencia != null && !instruccionesEmergencia.isEmpty() ? instruccionesEmergencia : "No especificado");

                            // Notas adicionales
                            String notasAdicionales = (String) instrucciones.get("notas_adicionales");
                            tvNotasAdicionales.setText(notasAdicionales != null && !notasAdicionales.isEmpty() ? notasAdicionales : "Ninguna");
                        } else {
                            // Si no hay datos de instrucciones
                            tvRutinaPaseo.setText("No especificado");
                            tvTipoCorreaArnes.setText("No especificado");
                            tvRecompensas.setText("No especificado");
                            tvInstruccionesEmergencia.setText("No especificado");
                            tvNotasAdicionales.setText("Ninguna");
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
