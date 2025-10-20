package com.mjc.mascotalink;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.mjc.mascotalink.modelo.PaseadorFavorito;
import java.util.List;

public class FavoritosViewModel extends ViewModel {

    private final FavoritosRepository repository;
    private final LiveData<List<PaseadorFavorito>> favoritos;

    public FavoritosViewModel() {
        this.repository = new FavoritosRepository();
        this.favoritos = repository.getFavoritos();
    }

    public LiveData<List<PaseadorFavorito>> getFavoritos() {
        return favoritos;
    }

    public void eliminarFavorito(String paseadorId) {
        repository.eliminarFavorito(paseadorId);
    }
}
