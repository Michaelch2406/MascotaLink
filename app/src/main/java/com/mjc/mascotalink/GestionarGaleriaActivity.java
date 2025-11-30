package com.mjc.mascotalink;

import android.content.Intent;
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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GestionarGaleriaActivity extends AppCompatActivity implements GestionGaleriaAdapter.OnItemClickListener {

    private static final String TAG = "GestionarGaleria";
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String paseadorId;
    private List<String> imageUrls = new ArrayList<>();
    private GestionGaleriaAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvContador;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::subirImagen
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gestionar_galeria);

        paseadorId = FirebaseAuth.getInstance().getUid();
        if (paseadorId == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        initViews();
        cargarGaleria();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        tvContador = findViewById(R.id.tv_contador_fotos);
        RecyclerView recyclerView = findViewById(R.id.rv_galeria_gestion);
        
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new GestionGaleriaAdapter(this, imageUrls, this);
        recyclerView.setAdapter(adapter);
    }

    private void cargarGaleria() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("paseadores").document(paseadorId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        List<String> urls = (List<String>) documentSnapshot.get("perfil_profesional.galeria_paseos_urls");
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
                    Toast.makeText(this, "Error al cargar galería", Toast.LENGTH_SHORT).show();
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

        // 1. Obtener datos del usuario para construir la carpeta exacta
        db.collection("usuarios").document(paseadorId).get().addOnSuccessListener(userDoc -> {
            if (!userDoc.exists()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show();
                return;
            }

            String cedula = userDoc.getString("cedula");
            String nombre = userDoc.getString("nombre");
            String apellido = userDoc.getString("apellido");

            if (cedula == null) cedula = "";
            if (nombre == null) nombre = "";
            if (apellido == null) apellido = "";

            // Limpieza básica de strings para coincidir con el formato de registro
            nombre = nombre.replaceAll("\\s", "");
            apellido = apellido.replaceAll("\\s", "");

            // Construir nombre de carpeta: UID_CEDULA_NOMBRE_APELLIDO
            String folderName = paseadorId + "_" + cedula + "_" + nombre + "_" + apellido;
            
            // 2. Calcular el siguiente número de secuencia
            int maxIndex = 0;
            for (String url : imageUrls) {
                // Decodificar URL para obtener el nombre del archivo real
                String decodedUrl = Uri.decode(url);
                // Buscamos patrones tipo "paseo_123.jpg"
                // La URL típica termina en .../paseo_123.jpg?alt=...
                try {
                    int lastSlash = decodedUrl.lastIndexOf('/');
                    int questionMark = decodedUrl.indexOf('?', lastSlash);
                    String filename;
                    if (questionMark > lastSlash) {
                        filename = decodedUrl.substring(lastSlash + 1, questionMark);
                    } else {
                        filename = decodedUrl.substring(lastSlash + 1);
                    }

                    if (filename.startsWith("paseo_") && (filename.endsWith(".jpg") || filename.endsWith(".png") || filename.endsWith(".jpeg"))) {
                        String numberPart = filename.replace("paseo_", "").replace(".jpg", "").replace(".png", "").replace(".jpeg", "");
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
            String filename = "paseo_" + nextIndex + ".jpg";
            
            // 3. Subir a la ruta específica
            StorageReference ref = storage.getReference().child("galeria_paseos/" + folderName + "/" + filename);

            ref.putFile(imageUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUrl -> {
                        String url = downloadUrl.toString();
                        // Actualizar Firestore
                        DocumentReference docRef = db.collection("paseadores").document(paseadorId);
                        docRef.update("perfil_profesional.galeria_paseos_urls", FieldValue.arrayUnion(url))
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    imageUrls.add(url);
                                    adapter.notifyDataSetChanged();
                                    actualizarContador();
                                    Toast.makeText(this, "Foto guardada como " + filename, Toast.LENGTH_SHORT).show();
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

        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error al obtener datos de usuario", Toast.LENGTH_SHORT).show();
        });
    }

    private void eliminarFoto(int position, String imageUrl) {
        progressBar.setVisibility(View.VISIBLE);

        // 1. Eliminar de Firestore
        DocumentReference docRef = db.collection("paseadores").document(paseadorId);
        docRef.update("perfil_profesional.galeria_paseos_urls", FieldValue.arrayRemove(imageUrl))
                .addOnSuccessListener(aVoid -> {
                    // 2. Eliminar de la lista local y UI
                    imageUrls.remove(position);
                    adapter.notifyDataSetChanged();
                    actualizarContador();
                    progressBar.setVisibility(View.GONE);
                    
                    // 3. Intentar eliminar de Storage (Opcional pero recomendado para ahorrar espacio)
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