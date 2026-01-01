package com.mjc.mascotalink.ui.home;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.mjc.mascotalink.ConfirmarPagoActivity;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.PaseoEnCursoDuenoActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class ReservationCardHelper {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private final Context context;
    private final RoleType roleType;
    private final Handler timerHandler;
    private Runnable timerRunnable;
    private boolean isFragmentAdded;

    public enum RoleType {
        DUENO, PASEADOR
    }

    public ReservationCardHelper(@NonNull Context context, RoleType roleType, Handler timerHandler) {
        this.context = context;
        this.roleType = roleType;
        this.timerHandler = timerHandler;
        this.isFragmentAdded = true;
    }

    public void setFragmentAdded(boolean added) {
        this.isFragmentAdded = added;
    }

    public void updateCard(View view, Map<String, Object> reservation) {
        ViewHolder viewHolder = new ViewHolder(view, roleType);
        stopTimer();

        if (reservation != null) {
            String estado = (String) reservation.get("estado");
            String resId = (String) reservation.get("id_documento");

            if (estado == null) estado = "";

            switch (estado) {
                case "EN_CURSO":
                    setupActiveWalkState(viewHolder, reservation, resId);
                    break;
                case "LISTO_PARA_INICIAR":
                    setupReadyToStartState(viewHolder, reservation, resId);
                    break;
                case "CONFIRMADO":
                    setupConfirmedState(viewHolder, reservation, resId);
                    break;
                case "ACEPTADO":
                    setupAcceptedState(viewHolder, reservation, resId);
                    break;
                case "PENDIENTE":
                case "PENDIENTE_ACEPTACION":
                    setupPendingState(viewHolder, reservation, resId);
                    break;
                default:
                    setupDefaultState(viewHolder, reservation, resId);
                    break;
            }
        } else {
            setupEmptyState(viewHolder);
        }
    }

    private void setupActiveWalkState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.header.setBackgroundResource(R.drawable.bg_gradient_green_card);
        vh.titulo.setText(R.string.walk_in_progress_title);
        vh.titulo.setTextColor(context.getColor(R.color.white));

        vh.layoutStats.setVisibility(View.VISIBLE);
        vh.desc.setVisibility(View.GONE);

        updateDistance(vh, reservation);
        startTimer(vh, reservation);

        vh.icon.setImageResource(R.drawable.ic_walk);
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (roleType == RoleType.DUENO) {
            vh.btn.setText(R.string.walk_track_now);
            vh.btn.setTextColor(context.getColor(R.color.green_success));

            if (vh.badgeLive != null) {
                vh.badgeLive.setVisibility(View.VISIBLE);
                startPulseAnimation(vh.badgeLive);
            }

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoDuenoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        } else {
            vh.btn.setText(R.string.walk_continue);
            vh.btn.setTextColor(context.getColor(R.color.green_success));

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        }
    }

    private void setupReadyToStartState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.header.setBackgroundResource(R.drawable.bg_gradient_orange_card);
        vh.titulo.setText(R.string.walk_ready_to_start_title);
        vh.titulo.setTextColor(context.getColor(R.color.white));

        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        if (roleType == RoleType.DUENO) {
            String paseadorNombre = (String) reservation.get("paseador_nombre");
            if (paseadorNombre != null && !paseadorNombre.isEmpty()) {
                vh.desc.setText(context.getString(R.string.walk_ready_desc_dueno_with_name, paseadorNombre));
            } else {
                vh.desc.setText(R.string.walk_ready_desc_dueno);
            }

            vh.btn.setText(R.string.walk_track);
            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoDuenoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        } else {
            String mascotaNombre = (String) reservation.get("mascota_nombre");
            String duenoNombre = (String) reservation.get("dueno_nombre");

            if (mascotaNombre != null && duenoNombre != null) {
                vh.desc.setText(context.getString(R.string.walk_ready_desc_paseador_full, mascotaNombre, duenoNombre));
            } else if (mascotaNombre != null) {
                vh.desc.setText(context.getString(R.string.walk_ready_desc_paseador_pet, mascotaNombre));
            } else {
                vh.desc.setText(R.string.walk_ready_desc_paseador);
            }

            vh.btn.setText(R.string.walk_start);
            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        }

        vh.desc.setTextColor(context.getColor(R.color.white));
        vh.btn.setTextColor(context.getColor(R.color.orange_primary));
        vh.icon.setImageResource(roleType == RoleType.DUENO ? R.drawable.ic_location_on : R.drawable.ic_walk);
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }
    }

    private void setupConfirmedState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
        vh.titulo.setTextColor(context.getColor(R.color.white));

        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        Timestamp horaInicio = (Timestamp) reservation.get("hora_inicio");

        if (roleType == RoleType.DUENO) {
            vh.titulo.setText(R.string.walk_scheduled_title);

            String paseadorNombre = (String) reservation.get("paseador_nombre");
            if (horaInicio != null) {
                String fechaStr = DATE_FORMAT.format(horaInicio.toDate());
                String horaStr = TIME_FORMAT.format(horaInicio.toDate());
                String walkerInfo = paseadorNombre != null ? context.getString(R.string.walk_walker_label, paseadorNombre) : "";
                vh.desc.setText(context.getString(R.string.walk_scheduled_desc_dueno, fechaStr, horaStr, walkerInfo));
            } else {
                vh.desc.setText(R.string.walk_confirmed_desc_fallback);
            }

            vh.btn.setText(R.string.walk_view_details);
            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoDuenoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        } else {
            vh.titulo.setText(R.string.walk_next_walk_title);

            String mascotaNombre = (String) reservation.get("mascota_nombre");
            Object costoObj = reservation.get("costo_total");

            if (horaInicio != null) {
                String fechaStr = DATE_FORMAT.format(horaInicio.toDate());
                String horaStr = TIME_FORMAT.format(horaInicio.toDate());
                String costoStr = costoObj != null ? context.getString(R.string.price_format, ((Number) costoObj).doubleValue()) : "";
                String petInfo = mascotaNombre != null ? context.getString(R.string.walk_pet_label, mascotaNombre) : "";
                String costInfo = !costoStr.isEmpty() ? " · " + costoStr : "";
                vh.desc.setText(context.getString(R.string.walk_scheduled_desc_paseador, fechaStr, horaStr, petInfo, costInfo));
            } else {
                String petInfo = mascotaNombre != null ? context.getString(R.string.walk_scheduled_with_pet, mascotaNombre) : "";
                vh.desc.setText(context.getString(R.string.walk_scheduled_fallback, petInfo));
            }

            vh.btn.setText(R.string.walk_view_details);
            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        }

        vh.desc.setTextColor(context.getColor(R.color.white));
        vh.btn.setTextColor(context.getColor(R.color.blue_primary));
        vh.icon.setImageResource(R.drawable.ic_calendar);
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }
    }

    private void setupAcceptedState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
        vh.titulo.setText(R.string.walk_accepted_title);
        vh.titulo.setTextColor(context.getColor(R.color.white));

        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        Timestamp horaInicio = (Timestamp) reservation.get("hora_inicio");

        if (roleType == RoleType.DUENO) {
            String paseadorNombre = (String) reservation.get("paseador_nombre");

            if (horaInicio != null) {
                String fechaStr = DATE_FORMAT.format(horaInicio.toDate());
                String horaStr = TIME_FORMAT.format(horaInicio.toDate());
                String walkerInfo = paseadorNombre != null ? context.getString(R.string.walk_walker_label, paseadorNombre) : "";
                vh.desc.setText(context.getString(R.string.walk_accepted_desc_dueno, fechaStr, horaStr, walkerInfo));
            } else if (paseadorNombre != null) {
                vh.desc.setText(context.getString(R.string.walk_accepted_desc_dueno_name, paseadorNombre));
            } else {
                vh.desc.setText(R.string.walk_accepted_desc_dueno_fallback);
            }

            vh.btn.setText(R.string.walk_confirm_payment);
        } else {
            String mascotaNombre = (String) reservation.get("mascota_nombre");

            if (horaInicio != null && mascotaNombre != null) {
                String fechaStr = DATE_FORMAT.format(horaInicio.toDate());
                String horaStr = TIME_FORMAT.format(horaInicio.toDate());
                vh.desc.setText(context.getString(R.string.walk_accepted_desc_paseador, fechaStr, horaStr, mascotaNombre));
            } else if (mascotaNombre != null) {
                vh.desc.setText(context.getString(R.string.walk_accepted_desc_paseador_name, mascotaNombre));
            } else {
                vh.desc.setText(R.string.walk_accepted_desc_paseador_fallback);
            }

            vh.btn.setText(R.string.walk_view_details);
        }

        vh.desc.setTextColor(context.getColor(R.color.white));
        vh.btn.setTextColor(context.getColor(R.color.blue_primary));
        vh.icon.setImageResource(R.drawable.ic_check_circle);
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }

        vh.btn.setOnClickListener(v -> {
            if (context != null) {
                Intent intent;
                if (roleType == RoleType.DUENO) {
                    // Para el dueño en estado ACEPTADO, abrir pantalla de confirmar pago
                    intent = new Intent(context, ConfirmarPagoActivity.class);
                } else {
                    // Para el paseador, abrir detalles del paseo
                    intent = new Intent(context, PaseoEnCursoActivity.class);
                }
                intent.putExtra("id_reserva", resId);
                context.startActivity(intent);
            }
        });
    }

    private void setupPendingState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        if (roleType == RoleType.DUENO) {
            vh.header.setBackgroundResource(R.drawable.bg_gradient_yellow_card);
            vh.titulo.setText(R.string.walk_pending_title_dueno);

            Timestamp fechaCreacion = (Timestamp) reservation.get("fecha_creacion");
            String paseadorNombre = (String) reservation.get("paseador_nombre");

            if (fechaCreacion != null) {
                long tiempoTranscurrido = System.currentTimeMillis() - fechaCreacion.toDate().getTime();
                String tiempoStr = formatElapsedTime(tiempoTranscurrido);
                String walkerInfo = paseadorNombre != null
                    ? context.getString(R.string.walk_pending_waiting_walker, paseadorNombre)
                    : context.getString(R.string.walk_pending_waiting);
                vh.desc.setText(context.getString(R.string.walk_pending_desc_dueno, tiempoStr, walkerInfo));
            } else {
                vh.desc.setText(R.string.walk_pending_waiting);
            }

            vh.btn.setText(R.string.walk_view_status);
            vh.btn.setTextColor(context.getColor(R.color.amber_dark));
            vh.icon.setImageResource(R.drawable.ic_access_time);

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    Intent intent = new Intent(context, PaseoEnCursoDuenoActivity.class);
                    intent.putExtra("id_reserva", resId);
                    context.startActivity(intent);
                }
            });
        } else {
            vh.header.setBackgroundResource(R.drawable.bg_gradient_orange_card);
            vh.titulo.setText(R.string.walk_pending_title_paseador);

            String mascotaNombre = (String) reservation.get("mascota_nombre");
            String duenoNombre = (String) reservation.get("dueno_nombre");
            Object costoObj = reservation.get("costo_total");
            Timestamp fechaSol = (Timestamp) reservation.get("fecha_creacion");

            StringBuilder descBuilder = new StringBuilder();

            if (mascotaNombre != null && duenoNombre != null) {
                descBuilder.append(context.getString(R.string.walk_pending_request_full, duenoNombre, mascotaNombre));
            } else if (mascotaNombre != null) {
                descBuilder.append(context.getString(R.string.walk_pending_request_pet, mascotaNombre));
            } else {
                descBuilder.append(context.getString(R.string.walk_pending_request));
            }

            if (costoObj != null) {
                descBuilder.append(context.getString(R.string.walk_pending_earnings, ((Number) costoObj).doubleValue()));
            }

            if (fechaSol != null) {
                long tiempoTranscurrido = System.currentTimeMillis() - fechaSol.toDate().getTime();
                String tiempoStr = formatElapsedTime(tiempoTranscurrido);
                descBuilder.append(" · ").append(context.getString(R.string.walk_pending_time_ago, tiempoStr));
            }

            vh.desc.setText(descBuilder.toString());
            vh.btn.setText(R.string.walk_review_request);
            vh.btn.setTextColor(context.getColor(R.color.orange_primary));
            vh.icon.setImageResource(R.drawable.ic_notifications);

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    context.startActivity(new Intent(context, SolicitudesActivity.class));
                }
            });
        }

        vh.titulo.setTextColor(context.getColor(R.color.white));
        vh.desc.setTextColor(context.getColor(R.color.white));
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }
    }

    private void setupDefaultState(ViewHolder vh, Map<String, Object> reservation, String resId) {
        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        vh.header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
        vh.titulo.setText(roleType == RoleType.DUENO ? R.string.walk_active : R.string.walk_mission_active);
        vh.titulo.setTextColor(context.getColor(R.color.white));

        String estado = (String) reservation.get("estado");
        vh.desc.setText(context.getString(R.string.walk_state_label, estado != null ? estado : ""));
        vh.desc.setTextColor(context.getColor(R.color.white));

        vh.btn.setText(roleType == RoleType.DUENO ? R.string.walk_view_details : R.string.walk_manage);
        vh.btn.setTextColor(context.getColor(R.color.blue_primary));
        vh.icon.setImageResource(R.drawable.ic_info);
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }

        vh.btn.setOnClickListener(v -> {
            if (context != null) {
                Intent intent = roleType == RoleType.DUENO
                    ? new Intent(context, PaseoEnCursoDuenoActivity.class)
                    : new Intent(context, PaseoEnCursoActivity.class);
                intent.putExtra("id_reserva", resId);
                context.startActivity(intent);
            }
        });
    }

    private void setupEmptyState(ViewHolder vh) {
        vh.layoutStats.setVisibility(View.GONE);
        vh.desc.setVisibility(View.VISIBLE);

        vh.header.setBackgroundResource(R.drawable.bg_gradient_blue_card);
        vh.titulo.setTextColor(context.getColor(R.color.white));
        vh.desc.setTextColor(context.getColor(R.color.white));
        vh.btn.setTextColor(context.getColor(R.color.blue_primary));
        vh.icon.setColorFilter(context.getColor(R.color.white));

        if (roleType == RoleType.DUENO) {
            vh.titulo.setText(R.string.home_dueno_sin_paseo_titulo);
            vh.desc.setText(R.string.home_dueno_sin_paseo_desc);
            vh.btn.setText(R.string.home_dueno_sin_paseo_btn);
            vh.icon.setImageResource(R.drawable.ic_search);

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    context.startActivity(new Intent(context, BusquedaPaseadoresActivity.class));
                }
            });
        } else {
            vh.titulo.setText(R.string.home_paseador_disponible_titulo);
            vh.desc.setText(R.string.home_paseador_disponible_desc);
            vh.btn.setText(R.string.home_paseador_disponible_btn);
            vh.icon.setImageResource(R.drawable.ic_check_circle);

            vh.btn.setOnClickListener(v -> {
                if (context != null) {
                    context.startActivity(new Intent(context, SolicitudesActivity.class));
                }
            });
        }

        if (vh.badgeLive != null) {
            vh.badgeLive.setVisibility(View.GONE);
            vh.badgeLive.clearAnimation();
        }
    }

    private void updateDistance(ViewHolder vh, Map<String, Object> reservation) {
        Object distObj = reservation.get("distancia_acumulada_metros");
        double distancia = 0.0;
        if (distObj instanceof Number) {
            distancia = ((Number) distObj).doubleValue();
        }
        vh.tvDistancia.setText(context.getString(R.string.walk_distance_format, distancia / 1000.0));
    }

    private void startTimer(ViewHolder vh, Map<String, Object> reservation) {
        Timestamp inicioTs = (Timestamp) reservation.get("fecha_inicio_paseo");
        if (inicioTs == null) {
            inicioTs = (Timestamp) reservation.get("hora_inicio");
        }

        if (inicioTs != null) {
            long startTime = inicioTs.toDate().getTime();
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isFragmentAdded) return;

                    long millis = System.currentTimeMillis() - startTime;
                    int seconds = (int) (millis / 1000);
                    int minutes = seconds / 60;
                    int hours = minutes / 60;
                    seconds = seconds % 60;
                    minutes = minutes % 60;

                    vh.tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
                    timerHandler.postDelayed(this, 1000);
                }
            };
            timerHandler.post(timerRunnable);
        }
    }

    private void startPulseAnimation(TextView badge) {
        android.view.animation.Animation anim = new android.view.animation.AlphaAnimation(0.5f, 1.0f);
        anim.setDuration(1000);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        anim.setRepeatCount(android.view.animation.Animation.INFINITE);
        badge.startAnimation(anim);
    }

    private String formatElapsedTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return context.getString(R.string.time_days_hours, days, hours % 24);
        } else if (hours > 0) {
            return context.getString(R.string.time_hours_minutes, hours, minutes % 60);
        } else if (minutes > 0) {
            return context.getString(R.string.time_minutes, minutes);
        } else {
            return context.getString(R.string.time_seconds);
        }
    }

    public void stopTimer() {
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    private static class ViewHolder {
        final LinearLayout header;
        final TextView titulo;
        final TextView desc;
        final MaterialButton btn;
        final ImageView icon;
        final LinearLayout layoutStats;
        final TextView tvTimer;
        final TextView tvDistancia;
        final TextView badgeLive;

        ViewHolder(View view, RoleType roleType) {
            if (roleType == RoleType.DUENO) {
                header = view.findViewById(R.id.header_estado);
                titulo = view.findViewById(R.id.tv_estado_titulo);
                desc = view.findViewById(R.id.tv_estado_descripcion);
                btn = view.findViewById(R.id.btn_accion_principal);
                icon = view.findViewById(R.id.iv_estado_icon);
                layoutStats = view.findViewById(R.id.layout_stats_live);
                tvTimer = view.findViewById(R.id.tv_live_timer);
                tvDistancia = view.findViewById(R.id.tv_live_distancia);
                badgeLive = view.findViewById(R.id.badge_live);
            } else {
                header = view.findViewById(R.id.header_mision);
                titulo = view.findViewById(R.id.tv_mision_titulo);
                desc = view.findViewById(R.id.tv_mision_descripcion);
                btn = view.findViewById(R.id.btn_accion_paseador);
                icon = view.findViewById(R.id.iv_mision_icon);
                layoutStats = view.findViewById(R.id.layout_stats_live_paseador);
                tvTimer = view.findViewById(R.id.tv_live_timer_paseador);
                tvDistancia = view.findViewById(R.id.tv_live_distancia_paseador);
                badgeLive = null;
            }
        }
    }
}
