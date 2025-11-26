package com.mjc.mascotalink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
        String filename = "paseo_" + UUID.randomUUID().toString() + ".jpg";
        
        // Obtener carpeta base del usuario para mantener orden (opcional, pero recomendado)
        // Como no tenemos acceso fácil al nombre/cedula aquí, usaremos el ID directo
        StorageReference ref = storage.getReference().child("galeria_paseos/" + paseadorId + "/" + filename);

        ref.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUrl -> {
                    String url = downloadUrl.toString();
                    // Actualizar Firestore usando FieldValue.arrayUnion
                    DocumentReference docRef = db.collection("paseadores").document(paseadorId);
                    docRef.update("perfil_profesional.galeria_paseos_urls", FieldValue.arrayUnion(url))
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                imageUrls.add(url);
                                adapter.notifyDataSetChanged();
                                actualizarContador();
                                Toast.makeText(this, "Foto subida correctamente", Toast.LENGTH_SHORT).show();
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