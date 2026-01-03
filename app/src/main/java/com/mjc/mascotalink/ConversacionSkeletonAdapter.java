package com.mjc.mascotalink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ConversacionSkeletonAdapter extends RecyclerView.Adapter<ConversacionSkeletonAdapter.SkeletonViewHolder> {

    private final int itemCount;

    public ConversacionSkeletonAdapter(int itemCount) {
        this.itemCount = itemCount;
    }

    @NonNull
    @Override
    public SkeletonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversacion_skeleton, parent, false);
        return new SkeletonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SkeletonViewHolder holder, int position) {
        // Aplicar animación shimmer a cada vista skeleton
        holder.applyShimmerAnimation();
    }

    @Override
    public int getItemCount() {
        return itemCount;
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        void applyShimmerAnimation() {
            applyShimmerToView(itemView);
        }

        private void applyShimmerToView(View view) {
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    applyShimmerToView(viewGroup.getChildAt(i));
                }
            } else {
                if (view.getId() != View.NO_ID) {
                    try {
                        String resourceName = view.getResources().getResourceEntryName(view.getId());
                        if (resourceName != null && resourceName.startsWith("skeleton_")) {
                            android.view.animation.Animation shimmer = android.view.animation.AnimationUtils.loadAnimation(
                                view.getContext(), R.anim.shimmer_animation);
                            view.startAnimation(shimmer);
                        }
                    } catch (Exception e) {
                        // Ignore if resource name cannot be retrieved
                    }
                }
            }
        }
    }
}
