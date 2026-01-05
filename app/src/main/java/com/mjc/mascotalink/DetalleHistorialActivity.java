package com.mjc.mascotalink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.utils.PdfGenerator;
import com.mjc.mascotalink.MyApplication;

import de.hdodenhof.circleimageview.CircleImageView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class DetalleHistorialActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private Paseo paseo;
    private String userRole;

    // Para manejar reservas agrupadas
    private boolean esGrupo = false;
    private int cantidadDias = 1;
    private double costoTotalGrupo = 0.0;
    private String fechaInicioGrupo = "";
    private String fechaFinGrupo = "";
    private String tipoReserva = "PUNTUAL";

    // Views
    private CircleImageView ivFotoPrincipal, ivFotoMascota;
    private com.mjc.mascotalink.views.OverlappingAvatarsView overlappingAvatars, overlappingAvatarsPrincipal;
    private TextView tvNombrePrincipal, tvRolPrincipal, tvEstadoPaseo;
    private TextView tvNombreMascota, tvFechaHora, tvDuracionReal;
    private TextView tvCostoTotal, tvMetodoPago;
    private RatingBar ratingBar;
    private TextView tvComentario;
    private MaterialButton btnDescargar, btnSoporte, btnCalificar;
    private View cardCalificacion;
    private View btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_historial);

        initViews();

        if (getIntent().hasExtra("rol_usuario")) {
            userRole = getIntent().getStringExtra("rol_usuario");
        }

        if (getIntent().hasExtra("id_reserva")) {
            cargarPaseo(getIntent().getStringExtra("id_reserva"));
        } else if (getIntent().hasExtra("paseo_obj")) {
            paseo = (Paseo) getIntent().getSerializableExtra("paseo_obj");
            llenarDatos();
        } else {
            Toast.makeText(this, "Error al cargar detalles", Toast.LENGTH_SHORT).show();
            finish();
        }

        setupListeners();
    }

    private void initViews() {
        ivFotoPrincipal = findViewById(R.id.iv_foto_principal);
        ivFotoMascota = findViewById(R.id.iv_foto_mascota);
        overlappingAvatars = findViewById(R.id.overlapping_avatars);
        overlappingAvatarsPrincipal = findViewById(R.id.overlapping_avatars_principal);
        tvNombrePrincipal = findViewById(R.id.tv_nombre_principal);
        tvRolPrincipal = findViewById(R.id.tv_rol_principal);
        tvEstadoPaseo = findViewById(R.id.tv_estado_paseo);
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        tvFechaHora = findViewById(R.id.tv_fecha_hora);
        tvDuracionReal = findViewById(R.id.tv_duracion_real);
        tvCostoTotal = findViewById(R.id.tv_costo_total);
        tvMetodoPago = findViewById(R.id.tv_metodo_pago);
        ratingBar = findViewById(R.id.rating_bar);
        tvComentario = findViewById(R.id.tv_comentario);
        btnDescargar = findViewById(R.id.btn_descargar_comprobante);
        btnSoporte = findViewById(R.id.btn_contactar_soporte);
        btnCalificar = findViewById(R.id.btn_calificar);
        cardCalificacion = findViewById(R.id.card_calificacion);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        
        if (btnCalificar != null) {
            btnCalificar.setOnClickListener(v -> {
                if (paseo != null) {
                    Intent intent = new Intent(this, ResumenPaseoActivity.class);
                    intent.putExtra("id_reserva", paseo.getReservaId());
                    startActivity(intent);
                }
            });
        }

        btnDescargar.setOnClickListener(v -> {
            if (paseo == null) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            } else {
                Uri uri = PdfGenerator.generarComprobante(this, paseo, esGrupo, cantidadDias, costoTotalGrupo, fechaInicioGrupo, fechaFinGrupo, tipoReserva);
                if (uri != null) abrirPdf(uri);
            }
        });

        btnSoporte.setOnClickListener(v -> Toast.makeText(this, "Contactando soporte...", Toast.LENGTH_SHORT).show());
    }
    
    private void abrirPdf(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Abrir con"));
        } catch (Exception e) {
            Toast.makeText(this, "No hay lector de PDF instalado", Toast.LENGTH_SHORT).show();
        }
    }

    private void cargarPaseo(String idReserva) {
        FirebaseFirestore.getInstance().collection("reservas").document(idReserva).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    paseo = doc.toObject(Paseo.class);
                    if (paseo != null) {
                        paseo.setReservaId(doc.getId());
                        if (doc.contains("id_mascota")) paseo.setIdMascota(doc.getString("id_mascota"));
                        if (doc.contains("mascotas_nombres")) paseo.setMascotasNombres((List<String>) doc.get("mascotas_nombres"));
                        if (doc.contains("mascotas_fotos")) paseo.setMascotasFotos((List<String>) doc.get("mascotas_fotos"));
                        
                        tipoReserva = doc.getString("tipo_reserva");
                        if (tipoReserva == null) tipoReserva = "PUNTUAL";
                        Boolean esG = doc.getBoolean("es_grupo");
                        String gId = doc.getString("grupo_reserva_id");
                        if (esG != null && esG && gId != null && !gId.isEmpty()) {
                            esGrupo = true;
                            cargarGrupoPaseos(gId, doc);
                        } else {
                            esGrupo = false;
                            costoTotalGrupo = paseo.getCosto_total();
                            cargarDatosRelacionados(doc);
                        }
                    }
                }
            });
    }

    private void cargarGrupoPaseos(String gId, DocumentSnapshot primera) {
        FirebaseFirestore.getInstance().collection("reservas").whereEqualTo("grupo_reserva_id", gId).get()
            .addOnSuccessListener(snap -> {
                if (snap.isEmpty()) { cargarDatosRelacionados(primera); return; }
                List<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
                docs.sort((r1, r2) -> {
                    Date d1 = r1.getDate("fecha");
                    Date d2 = r2.getDate("fecha");
                    return (d1 != null && d2 != null) ? d1.compareTo(d2) : 0;
                });
                cantidadDias = docs.size();
                costoTotalGrupo = 0;
                for (DocumentSnapshot d : docs) {
                    Double c = d.getDouble("costo_total");
                    if (c != null) costoTotalGrupo += c;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMM", new Locale("es", "ES"));
                if (!docs.isEmpty()) {
                    Date f1 = docs.get(0).getDate("fecha");
                    Date f2 = docs.get(docs.size()-1).getDate("fecha");
                    if (f1 != null && f2 != null) {
                        fechaInicioGrupo = sdf.format(f1);
                        fechaFinGrupo = sdf.format(f2);
                    }
                }
                cargarDatosRelacionados(primera);
            });
    }

    private void cargarDatosRelacionados(DocumentSnapshot doc) {
        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tareas = new ArrayList<>();
        DocumentReference pRef = doc.getDocumentReference("id_paseador");
        DocumentReference dRef = doc.getDocumentReference("id_dueno");
        
        tareas.add(pRef != null ? pRef.get() : com.google.android.gms.tasks.Tasks.forResult(null));
        tareas.add(dRef != null ? dRef.get() : com.google.android.gms.tasks.Tasks.forResult(null));

        List<String> mNombres = (List<String>) doc.get("mascotas_nombres");
        List<String> mFotos = (List<String>) doc.get("mascotas_fotos");
        if (mFotos != null) paseo.setMascotasFotos(mFotos);

        if (mNombres != null && !mNombres.isEmpty()) {
            paseo.setMascotaNombre(String.join(", ", mNombres));
            tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
        } else if (dRef != null && paseo.getIdMascota() != null) {
            tareas.add(FirebaseFirestore.getInstance().collection("duenos").document(dRef.getId()).collection("mascotas").document(paseo.getIdMascota()).get());
        } else {
            tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
            DocumentSnapshot pDoc = (DocumentSnapshot) results.get(0);
            if (pDoc != null && pDoc.exists()) {
                paseo.setPaseadorNombre(pDoc.getString("nombre_display"));
                paseo.setPaseadorFoto(pDoc.getString("foto_perfil"));
            }
            DocumentSnapshot dDoc = (DocumentSnapshot) results.get(1);
            if (dDoc != null && dDoc.exists()) {
                paseo.setDuenoNombre(dDoc.getString("nombre_display"));
                paseo.setDuenoFoto(dDoc.getString("foto_perfil"));
            }
            if (mNombres == null || mNombres.isEmpty()) {
                DocumentSnapshot mDoc = (DocumentSnapshot) results.get(2);
                if (mDoc != null && mDoc.exists()) {
                    paseo.setMascotaNombre(mDoc.getString("nombre"));
                    paseo.setMascotaFoto(mDoc.getString("foto_principal_url"));
                }
            }
            llenarDatos();
        });
    }

    private void llenarDatos() {
        if (paseo == null) return;
        overlappingAvatars.setVisibility(View.GONE);
        overlappingAvatarsPrincipal.setVisibility(View.GONE);
        ivFotoPrincipal.setVisibility(View.VISIBLE);
        ivFotoMascota.setVisibility(View.VISIBLE);

        // 1. HUMANO ARRIBA
        if ("PASEADOR".equalsIgnoreCase(userRole)) {
            tvNombrePrincipal.setText(paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "Dueño");
            tvRolPrincipal.setText("Dueño");
            cargarImagen(paseo.getDuenoFoto(), ivFotoPrincipal);
        } else {
            tvNombrePrincipal.setText(paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Paseador");
            tvRolPrincipal.setText("Paseador");
            cargarImagen(paseo.getPaseadorFoto(), ivFotoPrincipal);
        }

        // 2. MASCOTAS ABAJO
        tvNombreMascota.setText(paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota");
        List<String> mFotos = paseo.getMascotasFotos();
        if (mFotos != null && mFotos.size() > 1) {
            overlappingAvatars.setVisibility(View.VISIBLE);
            ivFotoMascota.setVisibility(View.GONE);
            overlappingAvatars.setImageUrls(mFotos);
        } else {
            cargarImagen(paseo.getMascotaFoto(), ivFotoMascota);
        }

        // 3. ESTADOS
        tvEstadoPaseo.setText(paseo.getEstado() != null ? paseo.getEstado() : "");
        if (esGrupo && cantidadDias > 1) {
            tvFechaHora.setText(cantidadDias + " días (" + fechaInicioGrupo + " - " + fechaFinGrupo + ")\n" + paseo.getHoraFormateada());
        } else {
            tvFechaHora.setText(paseo.getFechaFormateada() + " - " + paseo.getHoraFormateada());
        }
        tvDuracionReal.setText("Duración: " + paseo.getDuracion_minutos() + " min" + (esGrupo ? "/día" : ""));
        tvCostoTotal.setText(String.format(Locale.US, "$%.2f", esGrupo ? costoTotalGrupo : paseo.getCosto_total()));
        tvMetodoPago.setText(paseo.getMetodo_pago() != null ? paseo.getMetodo_pago() : "No especificado");
        
        int color = ContextCompat.getColor(this, R.color.grey_600);
        if ("COMPLETADO".equalsIgnoreCase(paseo.getEstado())) color = ContextCompat.getColor(this, R.color.green_700);
        else if ("CANCELADO".equalsIgnoreCase(paseo.getEstado())) color = ContextCompat.getColor(this, R.color.red_error);
        tvEstadoPaseo.setTextColor(color);
        buscarCalificacion();
    }

    private void buscarCalificacion() {
        String coll = "PASEADOR".equalsIgnoreCase(userRole) ? "resenas_duenos" : "resenas_paseadores";
        FirebaseFirestore.getInstance().collection(coll).whereEqualTo("reservaId", paseo.getReservaId()).limit(1).get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    DocumentSnapshot d = snap.getDocuments().get(0);
                    Double c = d.getDouble("calificacion");
                    if (c != null) {
                        ratingBar.setRating(c.floatValue());
                        tvComentario.setText(d.getString("comentario"));
                        cardCalificacion.setVisibility(View.VISIBLE);
                        if (btnCalificar != null) btnCalificar.setVisibility(View.GONE);
                    }
                } else {
                    cardCalificacion.setVisibility(View.GONE);
                    if (btnCalificar != null) btnCalificar.setVisibility(View.VISIBLE);
                }
            }).addOnFailureListener(e -> {
                cardCalificacion.setVisibility(View.GONE);
                if (btnCalificar != null) btnCalificar.setVisibility(View.VISIBLE);
            });
    }

    private void cargarImagen(String url, CircleImageView iv) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(MyApplication.getFixedUrl(url)).placeholder(R.drawable.ic_user_placeholder).into(iv);
        } else {
            iv.setImageResource(R.drawable.ic_user_placeholder);
        }
    }
}