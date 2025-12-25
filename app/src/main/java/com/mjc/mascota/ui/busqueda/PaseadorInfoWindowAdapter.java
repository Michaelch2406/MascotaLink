
package com.mjc.mascota.ui.busqueda;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.imageview.ShapeableImageView;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.MyApplication;

public class PaseadorInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    private final View mWindow;
    private final Context mContext;
    private final OnInfoWindowClickListener mListener;

    public interface OnInfoWindowClickListener {
        void onVerPerfilClick(String paseadorId);
    }

    public PaseadorInfoWindowAdapter(Context context, OnInfoWindowClickListener listener) {
        mContext = context;
        mListener = listener;
        mWindow = LayoutInflater.from(context).inflate(R.layout.info_window_paseador, null);
    }

    public OnInfoWindowClickListener getListener() {
        return mListener;
    }

    private void render(Marker marker, View view) {
        String paseadorId = (String) marker.getTag();
        if (paseadorId == null) {
            return;
        }

        PaseadorMarker paseador = PaseadorMarkersCache.getInstance().getPaseadorMarker(paseadorId);

        if (paseador != null) {
            ShapeableImageView ivPaseadorPhoto = view.findViewById(R.id.iv_paseador_photo);
            TextView tvPaseadorNombre = view.findViewById(R.id.tv_paseador_nombre);
            RatingBar rbPaseadorCalificacion = view.findViewById(R.id.rb_paseador_calificacion);
            TextView tvDistancia = view.findViewById(R.id.tv_distancia);
            Button btnVerPerfil = view.findViewById(R.id.btn_ver_perfil);

            tvPaseadorNombre.setText(paseador.getNombre());
            rbPaseadorCalificacion.setRating((float) paseador.getCalificacion());
            tvDistancia.setText(String.format("%.1f km", paseador.getDistanciaKm()));

            if (paseador.getFotoUrl() != null && !paseador.getFotoUrl().isEmpty()) {
                Glide.with(mContext)
                        .load(MyApplication.getFixedUrl(paseador.getFotoUrl()))
                        .override(120, 120) // OPTIMIZACIÓN: Solo cargar tamaño necesario
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person) // Placeholder si la imagen no carga
                        .into(ivPaseadorPhoto);
            } else {
                ivPaseadorPhoto.setImageResource(R.drawable.ic_person);
            }

            btnVerPerfil.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onVerPerfilClick(paseador.getPaseadorId());
                }
            });
        }
    }

    @Override
    public View getInfoWindow(Marker marker) {
        // El contenido completo de la ventana de información
        render(marker, mWindow);
        return mWindow;
    }

    @Override
    public View getInfoContents(Marker marker) {
        // El contenido de la ventana de información, dejando el marco por defecto
        return null; // Usamos getInfoWindow para personalizar todo
    }
}
