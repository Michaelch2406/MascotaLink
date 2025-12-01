package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.mjc.mascotalink.network.NetworkConfigManager;
import com.mjc.mascotalink.network.NetworkDetector;

/**
 * Activity para configurar la detección de red manualmente
 * Útil para debugging y configuración de IPs personalizadas
 */
public class NetworkConfigActivity extends AppCompatActivity {

    private TextView tvNetworkInfo;
    private TextView tvTailscaleStatus;
    private TextInputEditText etTailscaleIp;
    private TextInputEditText etManualIp;
    private SwitchMaterial switchAutoDetect;
    private SwitchMaterial switchPreferTailscale;
    private NetworkConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_config);

        configManager = NetworkDetector.getConfigManager(this);

        initViews();
        loadCurrentConfig();
        setupListeners();
        refreshNetworkInfo();
    }

    private void initViews() {
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo);
        tvTailscaleStatus = findViewById(R.id.tvTailscaleStatus);
        etTailscaleIp = findViewById(R.id.etTailscaleIp);
        etManualIp = findViewById(R.id.etManualIp);
        switchAutoDetect = findViewById(R.id.switchAutoDetect);
        switchPreferTailscale = findViewById(R.id.switchPreferTailscale);
    }

    private void loadCurrentConfig() {
        // Cargar configuración actual
        etTailscaleIp.setText(configManager.getTailscaleServerIp());

        String manualIp = configManager.getManualIp();
        if (manualIp != null) {
            etManualIp.setText(manualIp);
        }

        switchAutoDetect.setChecked(configManager.isAutoDetectEnabled());
        switchPreferTailscale.setChecked(configManager.shouldPreferTailscale());

        // Actualizar estado de Tailscale
        updateTailscaleStatus();
    }

    private void setupListeners() {
        // Botón refrescar información
        findViewById(R.id.btnRefreshInfo).setOnClickListener(v -> refreshNetworkInfo());

        // Guardar IP Tailscale
        findViewById(R.id.btnSaveTailscale).setOnClickListener(v -> {
            String ip = etTailscaleIp.getText().toString().trim();
            if (!ip.isEmpty()) {
                NetworkDetector.setTailscaleServerIp(this, ip);
                Toast.makeText(this, "IP de Tailscale guardada: " + ip, Toast.LENGTH_SHORT).show();
                refreshNetworkInfo();
            } else {
                Toast.makeText(this, "Ingresa una IP válida", Toast.LENGTH_SHORT).show();
            }
        });

        // Guardar IP manual
        findViewById(R.id.btnSaveManual).setOnClickListener(v -> {
            String ip = etManualIp.getText().toString().trim();
            if (!ip.isEmpty()) {
                NetworkDetector.setManualIp(this, ip);
                Toast.makeText(this, "IP manual guardada: " + ip, Toast.LENGTH_SHORT).show();
                refreshNetworkInfo();
            } else {
                Toast.makeText(this, "Ingresa una IP válida", Toast.LENGTH_SHORT).show();
            }
        });

        // Limpiar IP manual
        findViewById(R.id.btnClearManual).setOnClickListener(v -> {
            configManager.clearManualIp();
            etManualIp.setText("");
            Toast.makeText(this, "IP manual eliminada", Toast.LENGTH_SHORT).show();
            refreshNetworkInfo();
        });

        // Switch auto-detección
        switchAutoDetect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setAutoDetectEnabled(isChecked);
            Toast.makeText(this, "Auto-detección: " + (isChecked ? "Activada" : "Desactivada"),
                          Toast.LENGTH_SHORT).show();
            refreshNetworkInfo();
        });

        // Switch preferir Tailscale
        switchPreferTailscale.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setPreferTailscale(isChecked);
            Toast.makeText(this, "Preferir Tailscale: " + (isChecked ? "Sí" : "No"),
                          Toast.LENGTH_SHORT).show();
            refreshNetworkInfo();
        });

        // Resetear configuración
        findViewById(R.id.btnResetConfig).setOnClickListener(v -> {
            NetworkDetector.resetToAutoDetect(this);
            Toast.makeText(this, "Configuración reseteada a valores por defecto",
                          Toast.LENGTH_LONG).show();
            loadCurrentConfig();
            refreshNetworkInfo();
        });

        // Probar conexión
        findViewById(R.id.btnTestConnection).setOnClickListener(v -> {
            Toast.makeText(this, "Probando conexión...", Toast.LENGTH_SHORT).show();
            testConnection();
        });
    }

    private void refreshNetworkInfo() {
        String info = NetworkDetector.getNetworkInfo(this);
        tvNetworkInfo.setText(info);
        updateTailscaleStatus();
    }

    private void updateTailscaleStatus() {
        boolean isActive = NetworkDetector.isTailscaleActive(this);
        if (isActive) {
            tvTailscaleStatus.setText("Estado: ✅ Activo");
            tvTailscaleStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvTailscaleStatus.setText("Estado: ❌ Inactivo");
            tvTailscaleStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private void testConnection() {
        String currentHost = NetworkDetector.detectCurrentHost(this);

        // Mostrar resultado
        String message = "Host detectado: " + currentHost + "\n" +
                        "Tailscale: " + (NetworkDetector.isTailscaleActive(this) ? "Activo" : "Inactivo");

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Actualizar info
        refreshNetworkInfo();
    }
}
