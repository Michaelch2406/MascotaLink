package com.mjc.mascota.ui.busqueda;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mjc.mascotalink.R;

import java.util.List;
import java.util.Map;

/**
 * Adapter para ViewPager2 que muestra múltiples recomendaciones de paseadores
 * Permite swipear entre diferentes opciones de IA
 */
public class RecomendacionIAPagerAdapter extends RecyclerView.Adapter<RecomendacionIAPagerAdapter.RecommendationViewHolder> {

    private final List<Map<String, Object>> recommendations;
    private final OnRecommendationActionListener listener;

    public interface OnRecommendationActionListener {
        void onViewProfile(String paseadorId, int matchScore);
        void onFavorite(String paseadorId, int matchScore);
        void onShare(String paseadorId, int matchScore);
        void onNotInterested(String paseadorId);
    }

    public RecomendacionIAPagerAdapter(List<Map<String, Object>> recommendations, OnRecommendationActionListener listener) {
        this.recommendations = recommendations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecommendationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paseador_recomendacion_ia, parent, false);
        return new RecommendationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecommendationViewHolder holder, int position) {
        holder.bind(recommendations.get(position), position + 1, getItemCount());
    }

    @Override
    public int getItemCount() {
        return recommendations != null ? recommendations.size() : 0;
    }

    class RecommendationViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivProfilePic;
        private final TextView tvName;
        private final TextView tvLocation;
        private final TextView tvExperience;
        private final RatingBar ratingBar;
        private final TextView tvRating;
        private final TextView tvReviews;
        private final TextView tvPrice;
        private final TextView tvMatchScore;
        private final TextView tvReasonIA;
        private final TextView tvRecommendationNumber;
        private final ChipGroup chipGroup;
        private final MaterialButton btnViewProfile;
        private final MaterialButton btnFavorite;
        private final MaterialButton btnShare;
        private final MaterialButton btnNotInterested;

        public RecommendationViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePic = itemView.findViewById(R.id.ivProfilePhoto);
            tvName = itemView.findViewById(R.id.tvWalkerName);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvExperience = itemView.findViewById(R.id.tvExperience);
            ratingBar = null;
            tvRating = itemView.findViewById(R.id.tvRating);
            tvReviews = itemView.findViewById(R.id.tvReviewCount);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvMatchScore = itemView.findViewById(R.id.tvMatchScore);
            tvReasonIA = itemView.findViewById(R.id.tvRazonIA);
            tvRecommendationNumber = itemView.findViewById(R.id.tvHeadlineSubtitle);
            chipGroup = itemView.findViewById(R.id.chipGroupReasons);
            btnViewProfile = itemView.findViewById(R.id.btnViewProfile);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnNotInterested = itemView.findViewById(R.id.btnNoMeInteresa);
        }

        public void bind(Map<String, Object> recommendation, int position, int total) {
            String paseadorId = (String) recommendation.get("id");
            String nombre = (String) recommendation.get("nombre");
            String razonIA = (String) recommendation.get("razon_ia");
            int matchScore = getIntValue(recommendation.get("match_score"));
            List<String> tags = (List<String>) recommendation.get("tags");

            // Indicador de posición (ej: "Opción 1 de 2")
            if (tvRecommendationNumber != null) {
                if (total > 1) {
                    tvRecommendationNumber.setText(String.format("Opción %d de %d", position, total));
                    tvRecommendationNumber.setVisibility(View.VISIBLE);
                } else {
                    tvRecommendationNumber.setVisibility(View.GONE);
                }
            }

            // Datos básicos
            if (tvName != null) tvName.setText(nombre);
            if (tvReasonIA != null) tvReasonIA.setText(razonIA);
            if (tvMatchScore != null) tvMatchScore.setText(matchScore + "%");

            // Tags
            if (chipGroup != null && tags != null) {
                chipGroup.removeAllViews();
                for (String tag : tags) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(tag);
                    chip.setClickable(false);
                    chipGroup.addView(chip);
                }
            }

            // Botones de acción
            if (btnViewProfile != null) {
                btnViewProfile.setOnClickListener(v -> {
                    if (listener != null) listener.onViewProfile(paseadorId, matchScore);
                });
            }

            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    if (listener != null) listener.onFavorite(paseadorId, matchScore);
                });
            }

            if (btnShare != null) {
                btnShare.setOnClickListener(v -> {
                    if (listener != null) listener.onShare(paseadorId, matchScore);
                });
            }

            if (btnNotInterested != null) {
                btnNotInterested.setOnClickListener(v -> {
                    if (listener != null) listener.onNotInterested(paseadorId);
                });
            }
        }

        private int getIntValue(Object obj) {
            if (obj == null) return 0;
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            try {
                return Integer.parseInt(obj.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
