package com.mjc.mascotalink;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mjc.mascotalink.modelo.Notificacion;

import java.util.ArrayList;
import java.util.List;

public class NotificacionesActivity extends AppCompatActivity implements NotificacionesAdapter.OnNotificacionClickListener {

    private static final String TAG = "NotificacionesActivity";

    private RecyclerView rvNotificaciones;
    private LinearLayout layoutEmptyState;
    private ProgressBar progressBar;
    private TextView tvMarcarTodasLeidas;

    private NotificacionesAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration notificacionesListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notificaciones);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        loadNotificaciones();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        rvNotificaciones = findViewById(R.id.rv_notificaciones);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        progressBar = findViewById(R.id.progress_bar);
        tvMarcarTodasLeidas = findViewById(R.id.tv_marcar_todas_leidas);

        tvMarcarTodasLeidas.setOnClickListener(v -> marcarTodasComoLeidas());
    }

    private void setupRecyclerView() {
        adapter = new NotificacionesAdapter(this);
        rvNotificaciones.setLayoutManager(new LinearLayoutManager(this));
        rvNotificaciones.setAdapter(adapter);
    }

    private void loadNotificaciones() {
        showLoading(true);

        // Cargar notificaciones del usuario ordenadas por fecha (más recientes primero)
        notificacionesListener = db.collection("notificaciones")
                .whereEqualTo("userId", currentUserId)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(50) // Limitar a las últimas 50 notificaciones
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    showLoading(false);

                    if (error != null) {
                        Log.e(TAG, "Error cargando notificaciones", error);
                        Toast.makeText(this, "Error al cargar notificaciones", Toast.LENGTH_SHORT).show();
                        showEmptyState(true);
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        showEmptyState(true);
                        return;
                    }

                    List<Notificacion> notificaciones = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Notificacion notificacion = document.toObject(Notificacion.class);
                        notificacion.setId(document.getId());
                        notificaciones.add(notificacion);
                    }

                    adapter.setNotificaciones(notificaciones);
                    showEmptyState(false);

                    // Actualizar visibilidad del botón "Marcar todas"
                    boolean hayNoLeidas = notificaciones.stream().anyMatch(n -> !n.isLeida());
                    tvMarcarTodasLeidas.setVisibility(hayNoLeidas ? View.VISIBLE : View.GONE);
                });
    }

    private void marcarTodasComoLeidas() {
        if (currentUserId == null) return;

        progressBar.setVisibility(View.VISIBLE);

        db.collection("notificaciones")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("leida", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        return;
                    }

                    // Marcar todas como leídas
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        db.collection("notificaciones")
                                .document(document.getId())
                                .update("leida", true)
                                .addOnFailureListener(e -> Log.e(TAG, "Error marcando notificación como leída", e));
                    }

                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Todas las notificaciones marcadas como leídas", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error al marcar todas como leídas", e);
                    Toast.makeText(this, "Error al marcar notificaciones", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onNotificacionClick(Notificacion notificacion) {
        // Marcar como leída si no lo está
        if (!notificacion.isLeida()) {
            db.collection("notificaciones")
                    .document(notificacion.getId())
                    .update("leida", true)
                    .addOnFailureListener(e -> Log.e(TAG, "Error marcando notificación como leída", e));
        }

        // Aquí puedes agregar navegación según el tipo de notificación
        // Por ejemplo, si es una RESERVA, abrir la pantalla de detalles de la reserva
        /*
        if ("RESERVA".equals(notificacion.getTipo()) && notificacion.getReferenceId() != null) {
            Intent intent = new Intent(this, DetalleReservaActivity.class);
            intent.putExtra("reserva_id", notificacion.getReferenceId());
            startActivity(intent);
        }
        */
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        rvNotificaciones.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        layoutEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        rvNotificaciones.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificacionesListener != null) {
            notificacionesListener.remove();
        }
    }
}
