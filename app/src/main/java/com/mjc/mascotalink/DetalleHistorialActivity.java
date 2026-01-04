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

import java.util.Date;
import java.util.Locale;

public class DetalleHistorialActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private Paseo paseo;
    private String userRole;

    // Para manejar reservas agrupadas y SEMANAL/MENSUAL
    private boolean esGrupo = false;
    private int cantidadDias = 1;
    private double costoTotalGrupo = 0.0;
    private String fechaInicioGrupo = "";
    private String fechaFinGrupo = "";
    private String tipoReserva = "PUNTUAL"; // PUNTUAL, SEMANAL, MENSUAL

    // Views
    private CircleImageView ivFotoPrincipal, ivFotoMascota;
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
        btnCalificar = findViewById(R.id.btn_calificar);
        cardCalificacion = findViewById(R.id.card_calificacion);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        
        btnCalificar.setOnClickListener(v -> {
            if (paseo != null) {
                android.content.Intent intent = new android.content.Intent(this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", paseo.getReservaId());
                startActivity(intent);
            }
        });

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
                    pdfUri = PdfGenerator.generarComprobante(this, paseo, esGrupo, cantidadDias,
                            costoTotalGrupo, fechaInicioGrupo, fechaFinGrupo, tipoReserva);
                }
            } else {
                pdfUri = PdfGenerator.generarComprobante(this, paseo, esGrupo, cantidadDias,
                        costoTotalGrupo, fechaInicioGrupo, fechaFinGrupo, tipoReserva);
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

                            // Asegurar ID Mascota
                            if (paseo.getIdMascota() == null && doc.contains("id_mascota")) {
                                paseo.setIdMascota(doc.getString("id_mascota"));
                            }

                            // Leer tipo de reserva
                            tipoReserva = doc.getString("tipo_reserva");
                            if (tipoReserva == null) tipoReserva = "PUNTUAL";

                            // Verificar si es parte de un grupo (días específicos múltiples)
                            Boolean esGrupoFlag = doc.getBoolean("es_grupo");
                            String grupoReservaId = doc.getString("grupo_reserva_id");

                            if (esGrupoFlag != null && esGrupoFlag && grupoReservaId != null && !grupoReservaId.isEmpty()) {
                                // Es un grupo - cargar todas las reservas del grupo
                                esGrupo = true;
                                cargarGrupoPaseos(grupoReservaId, doc);
                            } else if ("SEMANAL".equals(tipoReserva)) {
                                // Reserva semanal: 7 días consecutivos
                                esGrupo = false;
                                cantidadDias = 7;
                                costoTotalGrupo = paseo.getCosto_total();
                                calcularRangoFechasSemanalMensual(paseo.getFecha(), 7);
                                cargarDatosRelacionados(doc);
                            } else if ("MENSUAL".equals(tipoReserva)) {
                                // Reserva mensual: 30 días consecutivos
                                esGrupo = false;
                                cantidadDias = 30;
                                costoTotalGrupo = paseo.getCosto_total();
                                calcularRangoFechasSemanalMensual(paseo.getFecha(), 30);
                                cargarDatosRelacionados(doc);
                            } else {
                                // Reserva puntual individual
                                esGrupo = false;
                                cantidadDias = 1;
                                costoTotalGrupo = paseo.getCosto_total();
                                cargarDatosRelacionados(doc);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error cargando paseo", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /**
     * Calcula el rango de fechas para reservas SEMANALES o MENSUALES
     */
    private void calcularRangoFechasSemanalMensual(Date fechaInicio, int dias) {
        if (fechaInicio == null) return;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(fechaInicio);

        java.util.Calendar calFin = java.util.Calendar.getInstance();
        calFin.setTime(fechaInicio);
        calFin.add(java.util.Calendar.DAY_OF_MONTH, dias - 1);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("d 'de' MMM", new java.util.Locale("es", "ES"));
        fechaInicioGrupo = sdf.format(cal.getTime());
        fechaFinGrupo = sdf.format(calFin.getTime());
    }

    /**
     * Carga todas las reservas de un grupo y calcula el costo total
     */
    private void cargarGrupoPaseos(String grupoReservaId, DocumentSnapshot primeraReserva) {
        FirebaseFirestore.getInstance().collection("reservas")
                .whereEqualTo("grupo_reserva_id", grupoReservaId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No se encontraron reservas del grupo", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Ordenar reservas por fecha
                    java.util.List<DocumentSnapshot> reservas = new java.util.ArrayList<>(querySnapshot.getDocuments());
                    reservas.sort((r1, r2) -> {
                        com.google.firebase.Timestamp t1 = r1.getTimestamp("fecha");
                        com.google.firebase.Timestamp t2 = r2.getTimestamp("fecha");
                        if (t1 == null || t2 == null) return 0;
                        return t1.compareTo(t2);
                    });

                    cantidadDias = reservas.size();

                    // Calcular costo total del grupo
                    costoTotalGrupo = 0.0;
                    for (DocumentSnapshot doc : reservas) {
                        Double costo = doc.getDouble("costo_total");
                        if (costo != null) {
                            costoTotalGrupo += costo;
                        }
                    }

                    // Obtener fechas de inicio y fin
                    if (reservas.size() > 0) {
                        com.google.firebase.Timestamp fechaInicio = reservas.get(0).getTimestamp("fecha");
                        com.google.firebase.Timestamp fechaFin = reservas.get(cantidadDias - 1).getTimestamp("fecha");

                        if (fechaInicio != null && fechaFin != null) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("d 'de' MMM", new java.util.Locale("es", "ES"));
                            fechaInicioGrupo = sdf.format(fechaInicio.toDate());
                            fechaFinGrupo = sdf.format(fechaFin.toDate());
                        }
                    }

                    // Cargar datos relacionados (paseador, dueño, mascota)
                    cargarDatosRelacionados(primeraReserva);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar grupo de reservas", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /**
     * Carga los datos relacionados (nombres y fotos) del paseador, dueño y mascota
     */
    private void cargarDatosRelacionados(DocumentSnapshot reservaDoc) {
        if (paseo == null) return;

        java.util.List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tareas = new java.util.ArrayList<>();

        // Obtener referencias
        DocumentReference paseadorRef = reservaDoc.getDocumentReference("id_paseador");
        DocumentReference duenoRef = reservaDoc.getDocumentReference("id_dueno");

        // Agregar tareas para cargar documentos
        tareas.add(paseadorRef != null ? paseadorRef.get() : com.google.android.gms.tasks.Tasks.forResult(null));
        tareas.add(duenoRef != null ? duenoRef.get() : com.google.android.gms.tasks.Tasks.forResult(null));

        // Soportar ambos formatos: nuevo (mascotas array) y antiguo (id_mascota string)
        @SuppressWarnings("unchecked")
        java.util.List<String> mascotasNombres = (java.util.List<String>) reservaDoc.get("mascotas_nombres");

        if (mascotasNombres != null && !mascotasNombres.isEmpty()) {
            // Formato nuevo: múltiples mascotas con nombres precargados
            tareas.add(com.google.android.gms.tasks.Tasks.forResult(null)); // No necesitamos cargar desde Firestore
        } else if (duenoRef != null && paseo.getIdMascota() != null) {
            // Formato antiguo: cargar una sola mascota desde Firestore
            tareas.add(FirebaseFirestore.getInstance()
                    .collection("duenos").document(duenoRef.getId())
                    .collection("mascotas").document(paseo.getIdMascota())
                    .get());
        } else {
            tareas.add(com.google.android.gms.tasks.Tasks.forResult(null));
        }

        // Ejecutar todas las tareas en paralelo
        com.google.android.gms.tasks.Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;

                // Resultado 0: Paseador
                DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(0);
                if (paseadorDoc != null && paseadorDoc.exists()) {
                    paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                    paseo.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                }

                // Resultado 1: Dueño
                DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(1);
                if (duenoDoc != null && duenoDoc.exists()) {
                    paseo.setDuenoNombre(duenoDoc.getString("nombre_display"));
                }

                // Resultado 2: Mascota
                // Verificar si hay múltiples mascotas (formato nuevo)
                if (mascotasNombres != null && !mascotasNombres.isEmpty()) {
                    // Formato nuevo: usar nombres precargados
                    String nombresConcatenados = String.join(", ", mascotasNombres);
                    paseo.setMascotaNombre(nombresConcatenados);
                    // No hay foto única para múltiples mascotas
                } else {
                    // Formato antiguo: una sola mascota
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(2);
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        paseo.setMascotaFoto(mascotaDoc.getString("foto_principal_url"));
                    }
                }

                // Ahora sí llenar la UI con todos los datos
                llenarDatos();
            });
        }).addOnFailureListener(e -> {
            android.util.Log.e("DetallePaseo", "Error cargando datos relacionados", e);
            // Mostrar UI aunque falten algunos datos
            llenarDatos();
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

        // Mostrar fecha: si es grupo, mostrar rango; si es individual, mostrar fecha única
        if (esGrupo && cantidadDias > 1) {
            tvFechaHora.setText(cantidadDias + " días (" + fechaInicioGrupo + " - " + fechaFinGrupo + ")\n" +
                    paseo.getHoraFormateada());
        } else {
            tvFechaHora.setText(paseo.getFechaFormateada() + " - " + paseo.getHoraFormateada());
        }

        tvDuracionReal.setText("Duración: " + paseo.getDuracion_minutos() + " min" + (esGrupo ? "/día" : ""));

        // Mostrar costo: si es grupo, mostrar costo total del grupo; si es individual, mostrar costo único
        tvCostoTotal.setText(String.format(Locale.US, "$%.2f", esGrupo ? costoTotalGrupo : paseo.getCosto_total()));
        
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
                            btnCalificar.setVisibility(View.GONE);
                        } else {
                            cardCalificacion.setVisibility(View.GONE);
                            btnCalificar.setVisibility(View.VISIBLE);
                        }
                    } else {
                        cardCalificacion.setVisibility(View.GONE);
                        btnCalificar.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    cardCalificacion.setVisibility(View.GONE);
                    btnCalificar.setVisibility(View.VISIBLE);
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
                Uri uri = PdfGenerator.generarComprobante(this, paseo, esGrupo, cantidadDias,
                        costoTotalGrupo, fechaInicioGrupo, fechaFinGrupo, tipoReserva);
                if (uri != null) {
                    abrirPdf(uri);
                }
            } else {
                Toast.makeText(this, "Permiso necesario para guardar el PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }
}