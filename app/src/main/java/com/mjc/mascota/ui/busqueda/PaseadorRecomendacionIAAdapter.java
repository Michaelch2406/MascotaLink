package com.mjc.mascota.ui.busqueda;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;
import com.mjc.mascota.modelo.PaseadorRecomendacionIA;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter para mostrar paseadores recomendados por IA usando item_paseador_recomendacion_ia.xml
 */
public class PaseadorRecomendacionIAAdapter extends ListAdapter<PaseadorRecomendacionIA, PaseadorRecomendacionIAAdapter.RecomendacionViewHolder> {

    private OnActionListener listener;

    public interface OnActionListener {
        void onVerPerfilClick(PaseadorRecomendacionIA paseador);
        void onFavoritoClick(PaseadorRecomendacionIA paseador);
        void onCompartirClick(PaseadorRecomendacionIA paseador);
    }

    public PaseadorRecomendacionIAAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<PaseadorRecomendacionIA> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaseadorRecomendacionIA>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaseadorRecomendacionIA oldItem, @NonNull PaseadorRecomendacionIA newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaseadorRecomendacionIA oldItem, @NonNull PaseadorRecomendacionIA newItem) {
            return oldItem.getNombre().equals(newItem.getNombre())
                    && Objects.equals(oldItem.getFotoUrl(), newItem.getFotoUrl())
                    && oldItem.getMatchScore() == newItem.getMatchScore()
                    && oldItem.getCalificacion() == newItem.getCalificacion();
        }
    };

    @NonNull
    @Override
    public RecomendacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paseador_recomendacion_ia, parent, false);
        return new RecomendacionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecomendacionViewHolder holder, int position) {
        PaseadorRecomendacionIA paseador = getItem(position);
        holder.bind(paseador, listener);
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    static class RecomendacionViewHolder extends RecyclerView.ViewHolder {

        // Referencias a todos los elementos del layout
        private final TextView tvWalkerName;
        private final TextView tvLocation;
        private final TextView tvExperience;
        private final TextView tvSpecialty;
        private final TextView tvRating;
        private final TextView tvReviewCount;
        private final TextView tvPrice;
        private final TextView tvMatchScore;
        private final ImageView ivProfilePhoto;
        private final View vAvailabilityDot;
        private final ChipGroup chipGroupReasons;
        private final MaterialButton btnViewProfile;
        private final MaterialButton btnFavorite;
        private final MaterialButton btnShare;

        public RecomendacionViewHolder(@NonNull View itemView) {
            super(itemView);

            // Inicializar todas las vistas
            tvWalkerName = itemView.findViewById(R.id.tvWalkerName);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvExperience = itemView.findViewById(R.id.tvExperience);
            tvSpecialty = itemView.findViewById(R.id.tvSpecialty);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvReviewCount = itemView.findViewById(R.id.tvReviewCount);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvMatchScore = itemView.findViewById(R.id.tvMatchScore);
            ivProfilePhoto = itemView.findViewById(R.id.ivProfilePhoto);
            vAvailabilityDot = itemView.findViewById(R.id.vAvailabilityDot);
            chipGroupReasons = itemView.findViewById(R.id.chipGroupReasons);
            btnViewProfile = itemView.findViewById(R.id.btnViewProfile);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnShare = itemView.findViewById(R.id.btnShare);
        }

        public void bind(final PaseadorRecomendacionIA paseador, final OnActionListener listener) {
            // Información básica
            tvWalkerName.setText(paseador.getNombre());
            tvLocation.setText(paseador.getZonaPrincipal() != null ? paseador.getZonaPrincipal() : "Sin ubicación");
            tvExperience.setText(String.format(Locale.getDefault(), "%d años exp.", paseador.getAnosExperiencia()));

            // Especialidad
            if (paseador.getEspecialidad() != null && !paseador.getEspecialidad().isEmpty()) {
                tvSpecialty.setText(paseador.getEspecialidad());
                tvSpecialty.setVisibility(View.VISIBLE);
            } else {
                tvSpecialty.setVisibility(View.GONE);
            }

            // Métricas
            tvRating.setText(String.format(Locale.getDefault(), "%.1f", paseador.getCalificacion()));
            tvReviewCount.setText(String.format(Locale.getDefault(), "%d Reseñas", paseador.getTotalResenas()));
            tvPrice.setText(String.format(Locale.getDefault(), "$%.0f", paseador.getTarifaPorHora()));

            // Match Score
            tvMatchScore.setText(String.format(Locale.getDefault(), "%d Match", paseador.getMatchScore()));

            // Foto de perfil
            Glide.with(itemView.getContext())
                    .load(MyApplication.getFixedUrl(paseador.getFotoUrl()))
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivProfilePhoto);

            // Indicador de disponibilidad
            vAvailabilityDot.setVisibility(paseador.isDisponibleAhora() ? View.VISIBLE : View.GONE);

            // Razones de la IA (ChipGroup)
            chipGroupReasons.removeAllViews();
            List<String> razones = paseador.getRazones();
            if (razones != null && !razones.isEmpty()) {
                for (String razon : razones) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(razon);
                    chip.setClickable(false);
                    chip.setCheckable(false);
                    chipGroupReasons.addView(chip);
                }
            } else {
                // Si no hay razones específicas, usar la razón principal de la IA
                if (paseador.getRazonIA() != null && !paseador.getRazonIA().isEmpty()) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(paseador.getRazonIA());
                    chip.setClickable(false);
                    chip.setCheckable(false);
                    chipGroupReasons.addView(chip);
                }
            }

            // Listeners de botones
            btnViewProfile.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVerPerfilClick(paseador);
                }
            });

            btnFavorite.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoritoClick(paseador);
                }
            });

            btnShare.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCompartirClick(paseador);
                }
            });

            // Click en toda la tarjeta también abre el perfil
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVerPerfilClick(paseador);
                }
            });
        }
    }
}
