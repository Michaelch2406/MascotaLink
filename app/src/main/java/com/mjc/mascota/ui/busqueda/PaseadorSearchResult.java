package com.mjc.mascota.ui.busqueda;

import com.google.firebase.firestore.DocumentSnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;
import java.util.List;

public class PaseadorSearchResult {
    public final List<PaseadorResultado> resultados;
    public final DocumentSnapshot lastVisible;

    public PaseadorSearchResult(List<PaseadorResultado> resultados, DocumentSnapshot lastVisible) {
        this.resultados = resultados;
        this.lastVisible = lastVisible;
    }
}
