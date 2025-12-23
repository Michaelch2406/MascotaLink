package com.mjc.mascotalink;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascotalink.modelo.PaseadorFavorito;
import java.util.List;

public class FavoritosRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    public LiveData<List<PaseadorFavorito>> getFavoritos() {
        MutableLiveData<List<PaseadorFavorito>> favoritosLiveData = new MutableLiveData<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            String userId = currentUser.getUid();
            CollectionReference favoritosRef = db.collection("usuarios").document(userId).collection("favoritos");

            // Paginación: Limitar a 30 favoritos más recientes
            favoritosRef.orderBy("fecha_agregado", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        // Manejar el error, por ejemplo, logueándolo
                        favoritosLiveData.postValue(null);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<PaseadorFavorito> favoritos = queryDocumentSnapshots.toObjects(PaseadorFavorito.class);
                        favoritosLiveData.postValue(favoritos);
                    }
                });
        } else {
            favoritosLiveData.postValue(null); // No hay usuario, no hay favoritos
        }

        return favoritosLiveData;
    }
    
    public void eliminarFavorito(String paseadorId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            db.collection("usuarios").document(userId).collection("favoritos").document(paseadorId)
                .delete(); // El listener en getFavoritos se encargará de actualizar la UI
        }
    }
}
