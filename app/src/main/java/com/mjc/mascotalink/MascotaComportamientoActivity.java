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

public class MascotaComportamientoActivity extends AppCompatActivity {

    private static final String TAG = "MascotaComportamiento";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView ivBack;
    private TextView tvNivelEnergia, tvConPersonas, tvConOtrosAnimales, tvConOtrosPerros;
    private TextView tvHabitosCorrea, tvComandosConocidos, tvMiedosFobias, tvManiasHabitos;

    private String duenoId;
    private String mascotaId;
    private ListenerRegistration mascotaListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_comportamiento);

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
        cargarDatosComportamiento();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        tvNivelEnergia = findViewById(R.id.tv_nivel_energia);
        tvConPersonas = findViewById(R.id.tv_con_personas);
        tvConOtrosAnimales = findViewById(R.id.tv_con_otros_animales);
        tvConOtrosPerros = findViewById(R.id.tv_con_otros_perros);
        tvHabitosCorrea = findViewById(R.id.tv_habitos_correa);
        tvComandosConocidos = findViewById(R.id.tv_comandos_conocidos);
        tvMiedosFobias = findViewById(R.id.tv_miedos_fobias);
        tvManiasHabitos = findViewById(R.id.tv_manias_habitos);

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
    private void cargarDatosComportamiento() {
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
                        // Obtener el mapa de comportamiento
                        Map<String, Object> comportamiento = (Map<String, Object>) document.get("comportamiento");
                        if (comportamiento != null) {
                            // Nivel de energía
                            String nivelEnergia = (String) comportamiento.get("nivel_energia");
                            tvNivelEnergia.setText(nivelEnergia != null && !nivelEnergia.isEmpty() ? nivelEnergia : "No especificado");

                            // Con personas
                            String conPersonas = (String) comportamiento.get("con_personas");
                            tvConPersonas.setText(conPersonas != null && !conPersonas.isEmpty() ? conPersonas : "No especificado");

                            // Con otros animales
                            String conOtrosAnimales = (String) comportamiento.get("con_otros_animales");
                            tvConOtrosAnimales.setText(conOtrosAnimales != null && !conOtrosAnimales.isEmpty() ? conOtrosAnimales : "No especificado");

                            // Con otros perros
                            String conOtrosPerros = (String) comportamiento.get("con_otros_perros");
                            tvConOtrosPerros.setText(conOtrosPerros != null && !conOtrosPerros.isEmpty() ? conOtrosPerros : "No especificado");

                            // Hábitos de correa
                            String habitosCorrea = (String) comportamiento.get("habitos_correa");
                            tvHabitosCorrea.setText(habitosCorrea != null && !habitosCorrea.isEmpty() ? habitosCorrea : "No especificado");

                            // Comandos conocidos
                            String comandosConocidos = (String) comportamiento.get("comandos_conocidos");
                            tvComandosConocidos.setText(comandosConocidos != null && !comandosConocidos.isEmpty() ? comandosConocidos : "No especificado");

                            // Miedos/fobias
                            String miedosFobias = (String) comportamiento.get("miedos_fobias");
                            tvMiedosFobias.setText(miedosFobias != null && !miedosFobias.isEmpty() ? miedosFobias : "Ninguno");

                            // Manías/hábitos
                            String maniasHabitos = (String) comportamiento.get("manias_habitos");
                            tvManiasHabitos.setText(maniasHabitos != null && !maniasHabitos.isEmpty() ? maniasHabitos : "Ninguna");
                        } else {
                            // Si no hay datos de comportamiento
                            tvNivelEnergia.setText("No especificado");
                            tvConPersonas.setText("No especificado");
                            tvConOtrosAnimales.setText("No especificado");
                            tvConOtrosPerros.setText("No especificado");
                            tvHabitosCorrea.setText("No especificado");
                            tvComandosConocidos.setText("No especificado");
                            tvMiedosFobias.setText("Ninguno");
                            tvManiasHabitos.setText("Ninguna");
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
