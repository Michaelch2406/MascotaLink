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
                Snackbar.make(view, error, Snackbar.LENGTH_LONG)
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

    private void updateDuenoCard(View view, Map<String, Object> reservation) {
        MaterialCardView card = view.findViewById(R.id.card_estado_paseo);
        LinearLayout header = view.findViewById(R.id.header_estado);
        TextView titulo = view.findViewById(R.id.tv_estado_titulo);
        TextView desc = view.findViewById(R.id.tv_estado_descripcion);
        com.google.android.material.button.MaterialButton btn = view.findViewById(R.id.btn_accion_principal);
        ImageView icon = view.findViewById(R.id.iv_estado_icon);

        if (reservation != null) {
            // Active Walk Found!
            header.setBackgroundResource(R.drawable.bg_gradient_green_card);
            titulo.setText(R.string.home_dueno_paseo_activo_titulo);
            desc.setText(R.string.home_dueno_paseo_activo_desc);
            btn.setText(R.string.home_dueno_paseo_activo_btn);
            icon.setImageResource(R.drawable.ic_walk);

            String resId = (String) reservation.get("id_documento");
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PaseoEnCursoDuenoActivity.class);
                intent.putExtra("id_reserva", resId);
                startActivity(intent);
            });
        } else {
            // No active walk
            header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
            titulo.setText(R.string.home_dueno_sin_paseo_titulo);
            desc.setText(R.string.home_dueno_sin_paseo_desc);
            btn.setText(R.string.home_dueno_sin_paseo_btn);
            icon.setImageResource(R.drawable.ic_search);

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

        if (reservation != null) {
            header.setBackgroundResource(R.drawable.bg_gradient_green_card);
            titulo.setText(R.string.home_paseador_paseo_activo_titulo);
            desc.setText(R.string.home_paseador_paseo_activo_desc);
            btn.setText(R.string.home_paseador_paseo_activo_btn);

            String resId = (String) reservation.get("id_documento");
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PaseoEnCursoActivity.class);
                intent.putExtra("id_reserva", resId);
                startActivity(intent);
            });
        } else {
            header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
            titulo.setText(R.string.home_paseador_disponible_titulo);
            desc.setText(R.string.home_paseador_disponible_desc);
            btn.setText(R.string.home_paseador_disponible_btn);

            btn.setOnClickListener(v -> startActivity(new Intent(getContext(), SolicitudesActivity.class)));
        }
    }
}