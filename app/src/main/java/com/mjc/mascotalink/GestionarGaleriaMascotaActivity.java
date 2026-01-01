package com.mjc.mascotalink;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class GestionarGaleriaMascotaActivity extends AppCompatActivity implements GestionGaleriaAdapter.OnItemClickListener {

    private static final String TAG = "GestionarGaleriaMascota";
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String duenoId;
    private String mascotaId;
    private String nombreMascota;
    private List<String> imageUrls = new ArrayList<>();
    private GestionGaleriaAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvContador;
    private TextView tvNombreMascota;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::subirImagen
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gestionar_galeria_mascota);

        duenoId = getIntent().getStringExtra("dueno_id");
        mascotaId = getIntent().getStringExtra("mascota_id");

        if (duenoId == null || mascotaId == null) {
            Toast.makeText(this, "Error: Datos de mascota no encontrados", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        initViews();
        cargarDatosMascota();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        tvContador = findViewById(R.id.tv_contador_fotos);
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        RecyclerView recyclerView = findViewById(R.id.rv_galeria_gestion);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new GestionGaleriaAdapter(this, imageUrls, this);
        recyclerView.setAdapter(adapter);
    }

    private void cargarDatosMascota() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        // Obtener nombre de la mascota
                        nombreMascota = documentSnapshot.getString("nombre");
                        if (nombreMascota != null) {
                            tvNombreMascota.setText(nombreMascota);
                        }

                        // Cargar galería
                        List<String> urls = (List<String>) documentSnapshot.get("galeria_mascotas");
                        imageUrls.clear();
                        if (urls != null) {
                            imageUrls.addAll(urls);
                        }
                        adapter.notifyDataSetChanged();
                        actualizarContador();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
                });
    }

    private void actualizarContador() {
        tvContador.setText("Fotos: " + imageUrls.size() + "/10");
    }

    @Override
    public void onAddClick() {
        if (imageUrls.size() >= 10) {
            Toast.makeText(this, "Límite de 10 fotos alcanzado", Toast.LENGTH_SHORT).show();
            return;
        }
        pickImageLauncher.launch("image/*");
    }

    @Override
    public void onDeleteClick(int position, String imageUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar foto")
                .setMessage("¿Estás seguro de que quieres eliminar esta foto?")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarFoto(position, imageUrl))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void subirImagen(Uri imageUri) {
        if (imageUri == null) return;

        progressBar.setVisibility(View.VISIBLE);

        // Limpiar nombre de mascota para la carpeta
        String nombreLimpio = nombreMascota != null ? nombreMascota.replaceAll("\\s", "_") : "mascota";

        // Construir nombre de carpeta: duenoId_mascotaId_nombreMascota
        String folderName = duenoId + "_" + mascotaId + "_" + nombreLimpio;

        // Calcular el siguiente número de secuencia
        int maxIndex = 0;
        for (String url : imageUrls) {
            String decodedUrl = Uri.decode(url);
            try {
                int lastSlash = decodedUrl.lastIndexOf('/');
                int questionMark = decodedUrl.indexOf('?', lastSlash);
                String filename;
                if (questionMark > lastSlash) {
                    filename = decodedUrl.substring(lastSlash + 1, questionMark);
                } else {
                    filename = decodedUrl.substring(lastSlash + 1);
                }

                if (filename.startsWith("foto_") && (filename.endsWith(".jpg") || filename.endsWith(".png") || filename.endsWith(".jpeg"))) {
                    String numberPart = filename.replace("foto_", "").replace(".jpg", "").replace(".png", "").replace(".jpeg", "");
                    int number = Integer.parseInt(numberPart);
                    if (number > maxIndex) {
                        maxIndex = number;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "No se pudo parsear número de secuencia de: " + url);
            }
        }

        int nextIndex = maxIndex + 1;
        String filename = "foto_" + nextIndex + ".jpg";

        // Subir a la ruta: galeria_mascotas/{duenoId}_{mascotaId}_{nombreMascota}/foto_X.jpg
        StorageReference ref = storage.getReference().child("galeria_mascotas/" + folderName + "/" + filename);

        ref.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUrl -> {
                    String url = downloadUrl.toString();
                    // Actualizar Firestore - campo galeria_mascotas
                    DocumentReference docRef = db.collection("duenos").document(duenoId)
                            .collection("mascotas").document(mascotaId);
                    docRef.update("galeria_mascotas", FieldValue.arrayUnion(url))
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                imageUrls.add(url);
                                adapter.notifyDataSetChanged();
                                actualizarContador();
                                Toast.makeText(this, "Foto guardada", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Error al actualizar base de datos", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al subir imagen", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error upload", e);
                });
    }

    private void eliminarFoto(int position, String imageUrl) {
        progressBar.setVisibility(View.VISIBLE);

        // 1. Eliminar de Firestore
        DocumentReference docRef = db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId);
        docRef.update("galeria_mascotas", FieldValue.arrayRemove(imageUrl))
                .addOnSuccessListener(aVoid -> {
                    // 2. Eliminar de la lista local y UI
                    imageUrls.remove(position);
                    adapter.notifyDataSetChanged();
                    actualizarContador();
                    progressBar.setVisibility(View.GONE);

                    // 3. Intentar eliminar de Storage
                    try {
                        StorageReference photoRef = storage.getReferenceFromUrl(imageUrl);
                        photoRef.delete().addOnFailureListener(e -> Log.w(TAG, "No se pudo borrar el archivo físico", e));
                    } catch (Exception e) {
                        Log.w(TAG, "Error al obtener referencia de storage para borrar", e);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al eliminar foto", Toast.LENGTH_SHORT).show();
                });
    }
}
