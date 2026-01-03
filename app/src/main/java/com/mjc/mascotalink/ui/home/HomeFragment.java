package com.mjc.mascotalink.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.FavoritosActivity;
import com.mjc.mascotalink.HistorialPaseosActivity;

import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final long MIN_REFRESH_INTERVAL_MS = 2000;

    private HomeViewModel viewModel;
    private String userId;
    private String userRole;
    private FrameLayout container;
    private ProgressBar pbLoading;
    private SwipeRefreshLayout swipeRefresh;
    private ReservationCardHelper cardHelper;
    private long lastRefreshTime = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        container = view.findViewById(R.id.home_container);
        pbLoading = view.findViewById(R.id.pb_loading_home);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        swipeRefresh.setOnRefreshListener(this::refreshData);
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);

        setupObservers(view);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
            userRole = BottomNavManager.getUserRole(requireContext());
            Log.d(TAG, "User authenticated - userId: " + userId + ", role: " + userRole);

            Handler timerHandler = new Handler(Looper.getMainLooper());
            ReservationCardHelper.RoleType roleType = "PASEADOR".equals(userRole)
                ? ReservationCardHelper.RoleType.PASEADOR
                : ReservationCardHelper.RoleType.DUENO;

            cardHelper = new ReservationCardHelper(requireContext(), roleType, timerHandler);

            setupRoleBasedUI(userRole);
            loadData();
        } else {
            Log.w(TAG, "No authenticated user found");
        }
    }

    private void setupObservers(@NonNull View view) {
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

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(view, error, Snackbar.LENGTH_LONG)
                    .setAction(R.string.home_retry_action, v -> refreshData())
                    .show();
                viewModel.clearError();
            }
        });
    }

    private void loadData() {
        if (userId == null) {
            Log.w(TAG, "loadData: userId is null");
            return;
        }

        Log.d(TAG, "loadData: Loading user data for role: " + userRole);
        viewModel.loadUserData(userId);
        viewModel.listenToActiveReservation(userId, userRole != null ? userRole : "DUEÑO");

        if ("PASEADOR".equals(userRole)) {
            Log.d(TAG, "loadData: Loading walker stats");
            viewModel.loadWalkerStats(userId);
        }
    }

    private void refreshData() {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "refreshData: Fragment not added or context is null");
            swipeRefresh.setRefreshing(false);
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceLastRefresh = now - lastRefreshTime;

        if (userId != null && timeSinceLastRefresh >= MIN_REFRESH_INTERVAL_MS) {
            Log.d(TAG, "refreshData: Refreshing data");
            lastRefreshTime = now;
            viewModel.setLoading(true);
            loadData();
        } else {
            Log.d(TAG, "refreshData: Debounced - wait " + (MIN_REFRESH_INTERVAL_MS - timeSinceLastRefresh) + "ms");
            swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), R.string.error_refresh_wait, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRoleBasedUI(String role) {
        if (!isAdded() || getContext() == null) return;

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View contentView;

        if ("PASEADOR".equals(role)) {
            contentView = inflater.inflate(R.layout.layout_home_paseador, container, false);
            setupPaseadorUI(contentView);
        } else {
            contentView = inflater.inflate(R.layout.layout_home_dueno, container, false);
            setupDuenoUI(contentView);
        }

        container.addView(contentView);
    }

    private void setupDuenoUI(View view) {
        TextView tvNombre = view.findViewById(R.id.tv_nombre_usuario);
        ImageView ivPerfil = view.findViewById(R.id.iv_perfil_icon);
        TextView tvPaseosSolicitados = view.findViewById(R.id.tv_paseos_solicitados);
        TextView tvMascotasCount = view.findViewById(R.id.tv_mascotas_count);

        viewModel.getUserProfile().observe(getViewLifecycleOwner(), data -> {
            if (!isAdded() || getContext() == null) return;

            if (data != null) {
                String nombre = (String) data.get("nombre_display");
                String foto = (String) data.get("foto_perfil");

                if (nombre != null) {
                    tvNombre.setText(nombre);
                }

                if (foto != null) {
                    Glide.with(this)
                        .load(MyApplication.getFixedUrl(foto))
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(ivPerfil);
                }
            }
        });

        // Cargar estadísticas del dueño
        if (userId != null) {
            viewModel.getDb().collection("duenos").document(userId)
                .addSnapshotListener((duenoDoc, error) -> {
                    if (!isAdded() || getContext() == null || error != null) return;

                    if (duenoDoc != null && duenoDoc.exists()) {
                        // Paseos solicitados
                        Object paseosObj = duenoDoc.get("num_paseos_solicitados");
                        long paseos = 0;
                        if (paseosObj instanceof Number) {
                            paseos = ((Number) paseosObj).longValue();
                        }
                        if (tvPaseosSolicitados != null) {
                            tvPaseosSolicitados.setText(String.valueOf(paseos));
                        }
                    }
                });

            // Contar mascotas
            viewModel.getDb().collection("duenos").document(userId)
                .collection("mascotas")
                .whereEqualTo("activo", true)
                .addSnapshotListener((mascotasSnapshot, error) -> {
                    if (!isAdded() || getContext() == null || error != null) return;

                    int count = mascotasSnapshot != null ? mascotasSnapshot.size() : 0;
                    if (tvMascotasCount != null) {
                        tvMascotasCount.setText(String.valueOf(count));
                    }
                });
        }

        viewModel.getActiveReservation().observe(getViewLifecycleOwner(), reservation -> {
            if (cardHelper != null) {
                cardHelper.setFragmentAdded(isAdded());
                cardHelper.updateCard(view, reservation);
            }
        });

        View btnFavoritos = view.findViewById(R.id.btn_mis_favoritos);
        View btnHistorial = view.findViewById(R.id.btn_historial);
        View btnMisMascotas = view.findViewById(R.id.btn_mis_mascotas);

        android.util.Log.d("HomeFragment", "btnFavoritos: " + (btnFavoritos != null));
        android.util.Log.d("HomeFragment", "btnHistorial: " + (btnHistorial != null));
        android.util.Log.d("HomeFragment", "btnMisMascotas: " + (btnMisMascotas != null));

        // Favoritos
        if (btnFavoritos != null) {
            View.OnClickListener favoritosListener = v -> {
                android.util.Log.d("HomeFragment", "Click en Favoritos");
                if (isAdded() && getContext() != null) {
                    startActivity(new Intent(requireContext(), FavoritosActivity.class));
                }
            };
            btnFavoritos.setOnClickListener(favoritosListener);
            // También poner el listener en los hijos para interceptar clicks
            if (btnFavoritos instanceof android.view.ViewGroup) {
                for (int i = 0; i < ((android.view.ViewGroup) btnFavoritos).getChildCount(); i++) {
                    ((android.view.ViewGroup) btnFavoritos).getChildAt(i).setOnClickListener(favoritosListener);
                }
            }
        }

        // Historial
        if (btnHistorial != null) {
            View.OnClickListener historialListener = v -> {
                android.util.Log.d("HomeFragment", "Click en Historial");
                if (isAdded() && getContext() != null) {
                    Intent intent = new Intent(requireContext(), HistorialPaseosActivity.class);
                    intent.putExtra("rol_usuario", "DUEÑO");
                    startActivity(intent);
                }
            };
            btnHistorial.setOnClickListener(historialListener);
            // También poner el listener en los hijos para interceptar clicks
            if (btnHistorial instanceof android.view.ViewGroup) {
                for (int i = 0; i < ((android.view.ViewGroup) btnHistorial).getChildCount(); i++) {
                    ((android.view.ViewGroup) btnHistorial).getChildAt(i).setOnClickListener(historialListener);
                }
            }
        }

        // Mis Mascotas
        if (btnMisMascotas != null) {
            View.OnClickListener mascotasListener = v -> {
                android.util.Log.d("HomeFragment", "Click en Mis Mascotas");
                if (isAdded() && getContext() != null && userId != null) {
                    Intent intent = new Intent(requireContext(), com.mjc.mascotalink.MisMascotasActivity.class);
                    intent.putExtra("dueno_id", userId);
                    startActivity(intent);
                }
            };
            btnMisMascotas.setOnClickListener(mascotasListener);
            // También poner el listener en los hijos para interceptar clicks
            if (btnMisMascotas instanceof android.view.ViewGroup) {
                for (int i = 0; i < ((android.view.ViewGroup) btnMisMascotas).getChildCount(); i++) {
                    ((android.view.ViewGroup) btnMisMascotas).getChildAt(i).setOnClickListener(mascotasListener);
                }
            }
        }
    }

    private void setupPaseadorUI(View view) {
        TextView tvNombre = view.findViewById(R.id.tv_nombre_paseador);
        ImageView ivPerfil = view.findViewById(R.id.iv_perfil_icon_paseador);
        TextView tvTotalPaseos = view.findViewById(R.id.tv_total_paseos);
        TextView tvCalificacion = view.findViewById(R.id.tv_calificacion);

        viewModel.getUserProfile().observe(getViewLifecycleOwner(), data -> {
            if (!isAdded() || getContext() == null) return;

            if (data != null) {
                String nombre = (String) data.get("nombre_display");
                String foto = (String) data.get("foto_perfil");

                if (nombre != null) {
                    tvNombre.setText(nombre);
                }

                if (foto != null) {
                    Glide.with(this)
                        .load(MyApplication.getFixedUrl(foto))
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(ivPerfil);
                }
            }
        });

        viewModel.getWalkerStats().observe(getViewLifecycleOwner(), data -> {
            if (!isAdded() || getContext() == null) return;

            if (data != null) {
                Object totalObj = data.get("num_servicios_completados");
                Object scoreObj = data.get("calificacion_promedio");

                long total = totalObj instanceof Number ? ((Number) totalObj).longValue() : 0L;
                double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;

                if (tvTotalPaseos != null) {
                    tvTotalPaseos.setText(String.valueOf(total));
                }

                if (tvCalificacion != null) {
                    tvCalificacion.setText(String.format("%.1f", score));
                }
            }
        });

        viewModel.getActiveReservation().observe(getViewLifecycleOwner(), reservation -> {
            if (cardHelper != null) {
                cardHelper.setFragmentAdded(isAdded());
                cardHelper.updateCard(view, reservation);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up resources");
        if (cardHelper != null) {
            cardHelper.stopTimer();
        }
    }
}
