package com.mjc.mascotalink.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.PaseoEnCursoDuenoActivity;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.FavoritosActivity;
import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascotalink.PerfilMascotaActivity; // Placeholder for My Pets

import java.util.Map;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private String userId;
    private String userRole;
    private FrameLayout container;
    private ProgressBar pbLoading;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        container = view.findViewById(R.id.home_container);
        pbLoading = view.findViewById(R.id.pb_loading_home);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Setup SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener(this::refreshData);
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                if (isLoading) {
                    pbLoading.setVisibility(View.VISIBLE);
                } else {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                }
            }
        });

        // Observe errors
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(view, error, com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG)
                    .setAction(R.string.home_retry_action, v -> refreshData())
                    .show();
                viewModel.clearError();
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
            userRole = BottomNavManager.getUserRole(requireContext());

            loadData();
            setupRoleBasedUI(userRole);
        }
    }

    private void loadData() {
        viewModel.loadUserData(userId);
        viewModel.listenToActiveReservation(userId, userRole != null ? userRole : "DUEÑO");

        if ("PASEADOR".equals(userRole)) {
            viewModel.loadWalkerStats(userId);
        }
    }

    private void refreshData() {
        if (userId != null) {
            viewModel.setLoading(true);
            loadData();
        }
    }

    private void setupRoleBasedUI(String role) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View contentView;

        if ("PASEADOR".equals(role)) {
            contentView = inflater.inflate(R.layout.layout_home_paseador, container, false);
            setupPaseadorUI(contentView);
        } else {
            // Default to DUEÑO
            contentView = inflater.inflate(R.layout.layout_home_dueno, container, false);
            setupDuenoUI(contentView);
        }

        container.addView(contentView);
    }

    private void setupDuenoUI(View view) {
        TextView tvNombre = view.findViewById(R.id.tv_nombre_usuario);
        ImageView ivPerfil = view.findViewById(R.id.iv_perfil_icon);

        // Bind Profile Data
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                String nombre = (String) data.get("nombre_display");
                String foto = (String) data.get("foto_perfil");
                if (nombre != null) tvNombre.setText(nombre);
                if (foto != null) Glide.with(this).load(MyApplication.getFixedUrl(foto)).into(ivPerfil);
            }
        });

        // Bind Active Reservation
        viewModel.getActiveReservation().observe(getViewLifecycleOwner(), reservation -> {
            updateDuenoCard(view, reservation);
        });

        // Quick Actions
        view.findViewById(R.id.btn_mis_favoritos).setOnClickListener(v -> startActivity(new Intent(getContext(), FavoritosActivity.class)));
        view.findViewById(R.id.btn_historial).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), com.mjc.mascotalink.HistorialPaseosActivity.class);
            intent.putExtra("rol_usuario", "DUEÑO");
            startActivity(intent);
        });
        // Placeholder for My Pets -> Ideally leads to a list of pets or profile
        view.findViewById(R.id.btn_mis_mascotas).setOnClickListener(v -> {
             // Assuming PerfilDuenoActivity handles pets list logic or a dedicated activity
             // For now, maybe redirect to Search as placeholder or Profile
        });
    }

    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void updateDuenoCard(View view, Map<String, Object> reservation) {
        MaterialCardView card = view.findViewById(R.id.card_estado_paseo);
        LinearLayout header = view.findViewById(R.id.header_estado);
        TextView titulo = view.findViewById(R.id.tv_estado_titulo);
        TextView desc = view.findViewById(R.id.tv_estado_descripcion);
        com.google.android.material.button.MaterialButton btn = view.findViewById(R.id.btn_accion_principal);
        ImageView icon = view.findViewById(R.id.iv_estado_icon);
        TextView badgeLive = view.findViewById(R.id.badge_live);
        
        // Vistas de estadísticas en vivo
        LinearLayout layoutStats = view.findViewById(R.id.layout_stats_live);
        TextView tvTimer = view.findViewById(R.id.tv_live_timer);
        TextView tvDistancia = view.findViewById(R.id.tv_live_distancia);

        // Detener timer previo por seguridad
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        if (reservation != null) {
            String estado = (String) reservation.get("estado");
            String resId = (String) reservation.get("id_documento");
            
            if (estado == null) estado = "";

            switch (estado) {
                case "EN_CURSO":
                    // Estado: EN VIVO (Verde)
                    header.setBackgroundResource(R.drawable.bg_gradient_green_card);
                    titulo.setText("Paseo en Curso");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    
                    // Mostrar stats y ocultar descripción estática
                    layoutStats.setVisibility(View.VISIBLE);
                    desc.setVisibility(View.GONE);
                    
                    // Actualizar Distancia
                    Object distObj = reservation.get("distancia_acumulada_metros");
                    double distancia = 0.0;
                    if (distObj instanceof Number) {
                        distancia = ((Number) distObj).doubleValue();
                    }
                    tvDistancia.setText(String.format(java.util.Locale.US, "%.2f km recorridos", distancia / 1000.0));

                    // Iniciar Timer
                    com.google.firebase.Timestamp inicioTs = (com.google.firebase.Timestamp) reservation.get("fecha_inicio_paseo");
                    if (inicioTs == null) inicioTs = (com.google.firebase.Timestamp) reservation.get("hora_inicio"); // Fallback
                    
                    if (inicioTs != null) {
                        long startTime = inicioTs.toDate().getTime();
                        timerRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                long millis = System.currentTimeMillis() - startTime;
                                int seconds = (int) (millis / 1000);
                                int minutes = seconds / 60;
                                int hours = minutes / 60;
                                seconds = seconds % 60;
                                minutes = minutes % 60;

                                tvTimer.setText(String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
                                timerHandler.postDelayed(this, 1000);
                            }
                        };
                        timerHandler.post(timerRunnable);
                    }

                    btn.setText("Rastrear Ahora");
                    btn.setTextColor(getResources().getColor(R.color.green_success));
                    icon.setImageResource(R.drawable.ic_walk);
                    icon.setColorFilter(getResources().getColor(R.color.white));
                    badgeLive.setVisibility(View.VISIBLE);
                    
                    // Animación de pulso simple
                    android.view.animation.Animation anim = new android.view.animation.AlphaAnimation(0.5f, 1.0f);
                    anim.setDuration(1000);
                    anim.setRepeatMode(android.view.animation.Animation.REVERSE);
                    anim.setRepeatCount(android.view.animation.Animation.INFINITE);
                    badgeLive.startAnimation(anim);

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;

                case "CONFIRMADO":
                case "LISTO_PARA_INICIAR":
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    // Estado: Confirmado (Morado/Azul Oscuro)
                    header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
                    titulo.setText("Paseo Confirmado");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("El paseador está en camino o listo para iniciar.");
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Ver Detalles");
                    btn.setTextColor(getResources().getColor(R.color.blue_primary));
                    icon.setImageResource(R.drawable.ic_check_circle);
                    icon.setColorFilter(getResources().getColor(R.color.white));
                    badgeLive.setVisibility(View.GONE);
                    badgeLive.clearAnimation();

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;

                case "PENDIENTE":
                case "PENDIENTE_ACEPTACION":
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    // Estado: Pendiente (Naranja)
                    header.setBackgroundResource(R.drawable.bg_gradient_orange_card);
                    titulo.setText("Solicitud Enviada");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("Esperando respuesta del paseador.");
                    desc.setTextColor(getResources().getColor(R.color.white)); 
                    
                    btn.setText("Ver Estado");
                    btn.setTextColor(getResources().getColor(R.color.orange_primary)); 
                    icon.setImageResource(R.drawable.ic_access_time);
                    icon.setColorFilter(getResources().getColor(R.color.white)); 
                    
                    badgeLive.setVisibility(View.GONE);
                    badgeLive.clearAnimation();

                    btn.setOnClickListener(v -> {
                         Intent intent = new Intent(getContext(), PaseoEnCursoDuenoActivity.class);
                         intent.putExtra("id_reserva", resId);
                         startActivity(intent);
                    });
                    break;

                default:
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    // Fallback (Azul)
                    header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
                    titulo.setText("Paseo Activo");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("Estado: " + estado);
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Ver Detalles");
                    btn.setTextColor(getResources().getColor(R.color.blue_primary));
                    icon.setImageResource(R.drawable.ic_info);
                    icon.setColorFilter(getResources().getColor(R.color.white));
                    badgeLive.setVisibility(View.GONE);
                    badgeLive.clearAnimation();

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;
            }

        } else {
            // Sin paseo (Azul Estándar)
            layoutStats.setVisibility(View.GONE);
            desc.setVisibility(View.VISIBLE);
            
            header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
            titulo.setText(R.string.home_dueno_sin_paseo_titulo);
            titulo.setTextColor(getResources().getColor(R.color.white));
            desc.setText(R.string.home_dueno_sin_paseo_desc);
            desc.setTextColor(getResources().getColor(R.color.white));
            
            btn.setText(R.string.home_dueno_sin_paseo_btn);
            btn.setTextColor(getResources().getColor(R.color.blue_primary));
            icon.setImageResource(R.drawable.ic_search);
            icon.setColorFilter(getResources().getColor(R.color.white));
            badgeLive.setVisibility(View.GONE);
            badgeLive.clearAnimation();

            btn.setOnClickListener(v -> startActivity(new Intent(getContext(), BusquedaPaseadoresActivity.class)));
        }
    }

    private void setupPaseadorUI(View view) {
        TextView tvNombre = view.findViewById(R.id.tv_nombre_paseador);
        ImageView ivPerfil = view.findViewById(R.id.iv_perfil_icon_paseador);
        TextView tvTotalPaseos = view.findViewById(R.id.tv_total_paseos);
        TextView tvCalificacion = view.findViewById(R.id.tv_calificacion);

        // Bind Profile
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                String nombre = (String) data.get("nombre_display");
                String foto = (String) data.get("foto_perfil");
                if (nombre != null) tvNombre.setText(nombre);
                if (foto != null) Glide.with(this).load(MyApplication.getFixedUrl(foto)).into(ivPerfil);
            }
        });

        // Bind Stats
        viewModel.getWalkerStats().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                Long total = (Long) data.get("num_servicios_completados");
                Double score = (Double) data.get("calificacion_promedio");

                if (total != null) tvTotalPaseos.setText(String.valueOf(total));
                if (score != null) tvCalificacion.setText(String.format("%.1f", score));
            }
        });

        // Bind Active Action
        viewModel.getActiveReservation().observe(getViewLifecycleOwner(), reservation -> {
            updatePaseadorCard(view, reservation);
        });
    }

    private void updatePaseadorCard(View view, Map<String, Object> reservation) {
        LinearLayout header = view.findViewById(R.id.header_mision);
        TextView titulo = view.findViewById(R.id.tv_mision_titulo);
        TextView desc = view.findViewById(R.id.tv_mision_descripcion);
        com.google.android.material.button.MaterialButton btn = view.findViewById(R.id.btn_accion_paseador);
        ImageView icon = view.findViewById(R.id.iv_mision_icon);
        
        // Vistas de estadísticas en vivo
        LinearLayout layoutStats = view.findViewById(R.id.layout_stats_live_paseador);
        TextView tvTimer = view.findViewById(R.id.tv_live_timer_paseador);
        TextView tvDistancia = view.findViewById(R.id.tv_live_distancia_paseador);

        // Detener timer previo
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        if (reservation != null) {
            String estado = (String) reservation.get("estado");
            String resId = (String) reservation.get("id_documento");
            
            if (estado == null) estado = "";

            switch (estado) {
                case "EN_CURSO":
                    // Estado: EN VIVO (Verde)
                    header.setBackgroundResource(R.drawable.bg_gradient_green_card);
                    titulo.setText("Paseo en Curso");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    
                    // Mostrar stats y ocultar descripción
                    layoutStats.setVisibility(View.VISIBLE);
                    desc.setVisibility(View.GONE);
                    
                    // Actualizar Distancia
                    Object distObj = reservation.get("distancia_acumulada_metros");
                    double distancia = 0.0;
                    if (distObj instanceof Number) {
                        distancia = ((Number) distObj).doubleValue();
                    }
                    tvDistancia.setText(String.format(java.util.Locale.US, "%.2f km recorridos", distancia / 1000.0));

                    // Iniciar Timer
                    com.google.firebase.Timestamp inicioTs = (com.google.firebase.Timestamp) reservation.get("fecha_inicio_paseo");
                    if (inicioTs == null) inicioTs = (com.google.firebase.Timestamp) reservation.get("hora_inicio"); // Fallback
                    
                    if (inicioTs != null) {
                        long startTime = inicioTs.toDate().getTime();
                        timerRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                long millis = System.currentTimeMillis() - startTime;
                                int seconds = (int) (millis / 1000);
                                int minutes = seconds / 60;
                                int hours = minutes / 60;
                                seconds = seconds % 60;
                                minutes = minutes % 60;

                                tvTimer.setText(String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
                                timerHandler.postDelayed(this, 1000);
                            }
                        };
                        timerHandler.post(timerRunnable);
                    }

                    desc.setText("¡Estás caminando con una mascota! Mantén la app abierta para el GPS.");
                    btn.setText("Continuar Paseo");
                    btn.setTextColor(getResources().getColor(R.color.green_success));
                    icon.setImageResource(R.drawable.ic_walk);
                    icon.setColorFilter(getResources().getColor(R.color.white));

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;

                case "LISTO_PARA_INICIAR":
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
                    titulo.setText("Listo para iniciar");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("Es hora de encontrarte con la mascota e iniciar el paseo.");
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Ir al Punto de Encuentro");
                    btn.setTextColor(getResources().getColor(R.color.blue_primary));
                    icon.setImageResource(R.drawable.ic_location_on);
                    icon.setColorFilter(getResources().getColor(R.color.white));

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;

                case "CONFIRMADO":
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
                    titulo.setText("Próximo Paseo");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("Tienes un paseo programado. ¡No lo olvides!");
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Ver Detalles");
                    btn.setTextColor(getResources().getColor(R.color.blue_primary));
                    icon.setImageResource(R.drawable.ic_calendar);
                    icon.setColorFilter(getResources().getColor(R.color.white));

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;

                case "PENDIENTE":
                case "PENDIENTE_ACEPTACION":
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    header.setBackgroundResource(R.drawable.bg_gradient_orange_card); // Naranja Fuerte
                    titulo.setText("Nueva Solicitud");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("¡Tienes una solicitud pendiente! Revísala pronto.");
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Revisar Solicitud");
                    btn.setTextColor(getResources().getColor(R.color.orange_primary));
                    icon.setImageResource(R.drawable.ic_notifications);
                    icon.setColorFilter(getResources().getColor(R.color.white));

                    btn.setOnClickListener(v -> startActivity(new Intent(getContext(), SolicitudesActivity.class)));
                    break;

                default:
                    layoutStats.setVisibility(View.GONE);
                    desc.setVisibility(View.VISIBLE);
                    
                    header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
                    titulo.setText("Misión Activa");
                    titulo.setTextColor(getResources().getColor(R.color.white));
                    desc.setText("Estado: " + estado);
                    desc.setTextColor(getResources().getColor(R.color.white));
                    
                    btn.setText("Gestionar");
                    btn.setTextColor(getResources().getColor(R.color.blue_primary));
                    icon.setImageResource(R.drawable.ic_info);
                    icon.setColorFilter(getResources().getColor(R.color.white));

                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(getContext(), PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", resId);
                        startActivity(intent);
                    });
                    break;
            }
        } else {
            // Estado Disponible (Sin reserva activa)
            layoutStats.setVisibility(View.GONE);
            desc.setVisibility(View.VISIBLE);
            
            header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
            titulo.setText("Disponible");
            titulo.setTextColor(getResources().getColor(R.color.white));
            desc.setText("No tienes paseos ahora. ¿Por qué no revisas si hay nuevas solicitudes?");
            desc.setTextColor(getResources().getColor(R.color.white));
            
            btn.setText("Ver Solicitudes");
            btn.setTextColor(getResources().getColor(R.color.blue_primary));
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setColorFilter(getResources().getColor(R.color.white));

            btn.setOnClickListener(v -> startActivity(new Intent(getContext(), SolicitudesActivity.class)));
        }
    }
}