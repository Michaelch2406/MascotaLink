package com.mjc.mascotalink;

import android.Manifest;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.utils.PdfGenerator;
import com.mjc.mascotalink.MyApplication;

import de.hdodenhof.circleimageview.CircleImageView;

import java.util.Locale;

public class DetallePaseoActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private Paseo paseo;
    private String userRole;

    // Views
    private CircleImageView ivFotoPrincipal, ivFotoMascota;
    private TextView tvNombrePrincipal, tvRolPrincipal, tvEstadoPaseo;
    private TextView tvNombreMascota, tvFechaHora, tvDuracionReal;
    private TextView tvCostoTotal, tvMetodoPago;
    private RatingBar ratingBar;
    private TextView tvComentario;
    private MaterialButton btnDescargar, btnSoporte;
    private View cardCalificacion;
    private View btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_paseo);

        initViews();

        if (getIntent().hasExtra("rol_usuario")) {
            userRole = getIntent().getStringExtra("rol_usuario");
        }

        if (getIntent().hasExtra("paseo_obj")) {
            paseo = (Paseo) getIntent().getSerializableExtra("paseo_obj");
            llenarDatos();
        } else if (getIntent().hasExtra("id_reserva")) {
            cargarPaseo(getIntent().getStringExtra("id_reserva"));
        } else {
            Toast.makeText(this, "Error al cargar detalles", Toast.LENGTH_SHORT).show();
            finish();
        }

        setupListeners();
    }

    private void initViews() {
        ivFotoPrincipal = findViewById(R.id.iv_foto_principal);
        ivFotoMascota = findViewById(R.id.iv_foto_mascota);
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
        cardCalificacion = findViewById(R.id.card_calificacion);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        btnDescargar.setOnClickListener(v -> {
            if (paseo == null) return;
            Uri pdfUri = null;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                } else {
                    pdfUri = PdfGenerator.generarComprobante(this, paseo);
                }
            } else {
                pdfUri = PdfGenerator.generarComprobante(this, paseo);
            }
            
            if (pdfUri != null) {
                abrirPdf(pdfUri);
            }
        });

        btnSoporte.setOnClickListener(v -> {
            // TODO: Implementar soporte (Email o Chat)
            Toast.makeText(this, "Contactando soporte...", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void abrirPdf(Uri uri) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            startActivity(android.content.Intent.createChooser(intent, "Abrir comprobante con"));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No hay aplicación para abrir PDF instalada.", Toast.LENGTH_LONG).show();
        }
    }

    private void cargarPaseo(String idReserva) {
        FirebaseFirestore.getInstance().collection("reservas").document(idReserva)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        paseo = doc.toObject(Paseo.class);
                        if (paseo != null) {
                            paseo.setReservaId(doc.getId());
                            // Aquí idealmente deberíamos cargar los nombres de dueño/paseador 
                            // si no vienen en el objeto Paseo serializado (lo cual pasa al cargar de DB)
                            // Por brevedad, asumimos que los campos básicos están o que se actualizaron
                            // en la lista. Si faltan, se verán vacíos.
                            llenarDatos();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error cargando paseo", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void llenarDatos() {
        if (paseo == null) return;

        if ("PASEADOR".equalsIgnoreCase(userRole)) {
            tvNombrePrincipal.setText(paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota");
            tvRolPrincipal.setText("Mascota");
            tvNombreMascota.setText(paseo.getDuenoNombre() != null ? "Dueño: " + paseo.getDuenoNombre() : "Dueño");
            cargarImagen(paseo.getMascotaFoto(), ivFotoPrincipal);
            // Foto secundaria podría ser del dueño si tuviéramos la URL
            ivFotoMascota.setVisibility(View.GONE); 
        } else {
            tvNombrePrincipal.setText(paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Paseador");
            tvRolPrincipal.setText("Paseador");
            tvNombreMascota.setText(paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota");
            cargarImagen(paseo.getPaseadorFoto(), ivFotoPrincipal);
            cargarImagen(paseo.getMascotaFoto(), ivFotoMascota);
        }

        tvEstadoPaseo.setText(paseo.getEstado() != null ? paseo.getEstado() : "");
        tvFechaHora.setText(paseo.getFechaFormateada() + " - " + paseo.getHoraFormateada());
        tvDuracionReal.setText("Duración: " + paseo.getDuracion_minutos() + " min");
        tvCostoTotal.setText(String.format(Locale.US, "$%.2f", paseo.getCosto_total()));
        
        if (paseo.getMetodo_pago() != null) {
            tvMetodoPago.setText(paseo.getMetodo_pago());
        } else {
            tvMetodoPago.setText("No especificado");
        }

        // Estado Visual
        int color = ContextCompat.getColor(this, R.color.grey_600);
        if ("COMPLETADO".equalsIgnoreCase(paseo.getEstado())) color = ContextCompat.getColor(this, R.color.green_700);
        else if ("CANCELADO".equalsIgnoreCase(paseo.getEstado())) color = ContextCompat.getColor(this, R.color.red_error);
        
        tvEstadoPaseo.setTextColor(color);

        // Buscar calificación
        buscarCalificacion();
    }

    private void buscarCalificacion() {
        String collection = "PASEADOR".equalsIgnoreCase(userRole) ? "resenas_duenos" : "resenas_paseadores";
        String fieldToMatch = "reservaId"; // Asumiendo que la reseña guarda el ID de la reserva

        // Si no existe el campo reservaId en las reseñas antiguas, habría que buscar por timestamp aproximado o ids de usuarios
        // Pero lo ideal es que al crear la reseña se guarde el reservaId.
        // Asumimos que se guarda como 'reservaId'.
        
        FirebaseFirestore.getInstance().collection(collection)
                .whereEqualTo("reservaId", paseo.getReservaId())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Double calificacion = doc.getDouble("calificacion");
                        String comentario = doc.getString("comentario");
                        
                        if (calificacion != null) {
                            ratingBar.setRating(calificacion.floatValue());
                            tvComentario.setText(comentario != null ? comentario : "");
                            cardCalificacion.setVisibility(View.VISIBLE);
                        } else {
                            cardCalificacion.setVisibility(View.GONE);
                        }
                    } else {
                        cardCalificacion.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    cardCalificacion.setVisibility(View.GONE);
                });
    }

    private void cargarImagen(String url, CircleImageView iv) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(MyApplication.getFixedUrl(url)).placeholder(R.drawable.ic_pet_placeholder).into(iv);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Uri uri = PdfGenerator.generarComprobante(this, paseo);
                if (uri != null) {
                    abrirPdf(uri);
                }
            } else {
                Toast.makeText(this, "Permiso necesario para guardar el PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
