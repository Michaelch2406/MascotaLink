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
import com.mjc.mascotalink.util.BottomNavManager;

import java.util.Map;

public class MascotaInstruccionesActivity extends AppCompatActivity {

    private static final String TAG = "MascotaInstrucciones";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView ivBack;
    private TextView tvRutinaPaseo, tvTipoCorreaArnes, tvRecompensas;
    private TextView tvInstruccionesEmergencia, tvNotasAdicionales;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "DUEÑO";
    private int bottomNavSelectedItem = R.id.menu_perfil;

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

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        tvRutinaPaseo = findViewById(R.id.tv_rutina_paseo);
        tvTipoCorreaArnes = findViewById(R.id.tv_tipo_correa_arnes);
        tvRecompensas = findViewById(R.id.tv_recompensas);
        tvInstruccionesEmergencia = findViewById(R.id.tv_instrucciones_emergencia);
        tvNotasAdicionales = findViewById(R.id.tv_notas_adicionales);
        bottomNav = findViewById(R.id.bottom_nav);
        setupBottomNavigation();
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        String currentUserUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        boolean isOwner = currentUserUid != null && currentUserUid.equals(duenoId);
        if (isOwner) {
            bottomNav.setVisibility(View.VISIBLE);
            BottomNavManager.setupBottomNav(this, bottomNav, bottomNavRole, bottomNavSelectedItem);
        } else {
            bottomNav.setVisibility(View.GONE);
        }
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
