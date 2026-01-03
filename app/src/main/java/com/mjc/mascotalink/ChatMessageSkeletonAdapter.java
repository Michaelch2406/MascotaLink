package com.mjc.mascotalink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adapter para mostrar skeleton de mensajes en el chat.
 * Muestra una mezcla de mensajes enviados y recibidos (6-8 items)
 * para llenar la pantalla visible de forma natural.
 */
public class ChatMessageSkeletonAdapter extends RecyclerView.Adapter<ChatMessageSkeletonAdapter.SkeletonViewHolder> {

    private final int itemCount;

    public ChatMessageSkeletonAdapter(int itemCount) {
        this.itemCount = itemCount;
    }

    @Override
    public int getItemViewType(int position) {
        // Alternar entre mensajes enviados (1) y recibidos (0)
        // Patrón: recibido, recibido, enviado, recibido, enviado, enviado, recibido, enviado
        if (position == 0 || position == 1 || position == 3 || position == 6) {
            return 0; // Recibido
        } else {
            return 1; // Enviado
        }
    }

    @NonNull
    @Override
    public SkeletonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == 0)
            ? R.layout.item_mensaje_recibido_skeleton
            : R.layout.item_mensaje_enviado_skeleton;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new SkeletonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SkeletonViewHolder holder, int position) {
        holder.applyShimmerAnimation();
    }

    @Override
    public int getItemCount() {
        return itemCount;
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {

        public SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void applyShimmerAnimation() {
            applyShimmerToView(itemView);
        }

        private void applyShimmerToView(View view) {
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    applyShimmerToView(viewGroup.getChildAt(i));
                }
            } else {
                if (view.getId() != View.NO_ID) {
                    try {
                        String resourceName = view.getContext().getResources().getResourceEntryName(view.getId());
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
