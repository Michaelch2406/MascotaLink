package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mjc.mascotalink.ui.home.HomeFragment;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.UnreadBadgeManager;

import javax.inject.Inject;
import com.mjc.mascotalink.network.SocketManager; // Ensure this import exists

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private String currentUserId;
    private com.mjc.mascotalink.network.NetworkMonitorHelper networkMonitor;
    private android.widget.LinearLayout connectionBanner;

    @Inject
    FirebaseAuth auth;

    @Inject
    SocketManager socketManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = current.getUid();

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        connectionBanner = findViewById(R.id.connection_banner);

        // Configurar NetworkMonitor
        setupNetworkMonitor();

        String role = BottomNavManager.getUserRole(this);
        BottomNavManager.setupBottomNav(this, bottomNav, role, R.id.menu_home);
        UnreadBadgeManager.start(currentUserId);
        UnreadBadgeManager.registerNav(bottomNav, this);

        // Handle notification deep link for Chat
        if (getIntent() != null && getIntent().hasExtra("chat_id") && getIntent().hasExtra("id_otro_usuario")) {
            String chatId = getIntent().getStringExtra("chat_id");
            String otherUserId = getIntent().getStringExtra("id_otro_usuario");

            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("chat_id", chatId);
            chatIntent.putExtra("id_otro_usuario", otherUserId);
            startActivity(chatIntent);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.menu_home);
        }
        UnreadBadgeManager.registerNav(bottomNav, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
    }

    private void setupNetworkMonitor() {
        // socketManager ya está inyectado por Hilt

        networkMonitor = new com.mjc.mascotalink.network.NetworkMonitorHelper(
            this, socketManager,
            new com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkCallback() {

                @Override
                public void onNetworkLost() {
                    runOnUiThread(() -> {
                        if (connectionBanner != null) {
                            connectionBanner.setVisibility(android.view.View.VISIBLE);
                            android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                            if (bannerText != null) {
                                bannerText.setText(" Sin conexión. Intentando reconectar...");
                            }
                        }
                    });
                }

                @Override
                public void onNetworkAvailable() {
                    runOnUiThread(() -> {
                        if (connectionBanner != null) {
                            android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                            if (bannerText != null) {
                                bannerText.setText("🔄 Conectando...");
                            }
                        }
                    });
                }

                @Override
                public void onReconnected() {
                    runOnUiThread(() -> {
                        if (connectionBanner != null) {
                            // Mostrar mensaje de éxito brevemente antes de ocultar
                            android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                            if (bannerText != null) {
                                bannerText.setText(" Conexión restaurada");
                                connectionBanner.setBackgroundColor(0xFFD1FAE5); // Verde claro
                            }

                            // Ocultar el banner después de 2 segundos
                            connectionBanner.postDelayed(() -> {
                                connectionBanner.setVisibility(android.view.View.GONE);
                                // Restaurar color amarillo para próxima vez
                                connectionBanner.setBackgroundColor(0xFFFEF3C7);
                            }, 2000);
                        }
                    });
                }

                @Override
                public void onRetrying(int attempt, long delayMs) {
                    runOnUiThread(() -> {
                        if (connectionBanner != null) {
                            android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                            if (bannerText != null) {
                                bannerText.setText("Reintento " + attempt + "/5 en " + (delayMs/1000) + "s...");
                            }
                        }
                    });
                }

                @Override
                public void onReconnectionFailed(int attempts) {
                    runOnUiThread(() -> {
                        if (connectionBanner != null) {
                            android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                            if (bannerText != null) {
                                bannerText.setText(" Sin conexión. Toca 'Reintentar' para conectar.");
                            }
                        }
                    });
                }

                @Override
                public void onNetworkTypeChanged(com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkType type) {
                    // No mostrar cambios de tipo de red en el banner principal
                }

                @Override
                public void onNetworkQualityChanged(com.mjc.mascotalink.network.NetworkMonitorHelper.NetworkQuality quality) {
                    // No mostrar cambios de calidad en el banner principal
                }
            });

        networkMonitor.register();

        // Configurar botón de reintentar en el banner
        android.widget.TextView retryAction = findViewById(R.id.connection_banner_action);
        if (retryAction != null) {
            retryAction.setOnClickListener(v -> {
                if (networkMonitor != null) {
                    networkMonitor.forceReconnect();
                    android.widget.TextView bannerText = connectionBanner.findViewById(R.id.connection_banner_text);
                    if (bannerText != null) {
                        bannerText.setText("🔄 Reconectando...");
                    }
                }
            });
        }
    }
}
