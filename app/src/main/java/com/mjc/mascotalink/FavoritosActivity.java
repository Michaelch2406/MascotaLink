package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;

public class FavoritosActivity extends AppCompatActivity implements FavoritosAdapter.OnItemClickListener {

    private FavoritosViewModel viewModel;
    private FavoritosAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritos);

        setupToolbar();
        setupRecyclerView();
        setupBottomNavigation();

        emptyView = findViewById(R.id.empty_view_favoritos);

        viewModel = new ViewModelProvider(this).get(FavoritosViewModel.class);
        viewModel.getFavoritos().observe(this, favoritos -> {
            if (favoritos != null && !favoritos.isEmpty()) {
                adapter.submitList(favoritos);
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            } else {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupToolbar() {
        ImageView ivBack = findViewById(R.id.iv_back_favoritos);
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view_favoritos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavoritosAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onQuitarFavoritoClick(String paseadorId) {
        viewModel.eliminarFavorito(paseadorId);
        Toast.makeText(this, "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPaseadorClick(String paseadorId) {
        Intent intent = new Intent(this, PerfilPaseadorActivity.class);
        intent.putExtra("paseadorId", paseadorId);
        startActivity(intent);
    }
    
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav_favoritos);
        // Lógica para la navegación inferior si es necesario, por ejemplo, para mantener el ítem correcto seleccionado
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                // No hacer nada o ir a Home
                return true;
            } else if (itemId == R.id.menu_search) {
                Intent intent = new Intent(this, BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_perfil) {
                // Ir al perfil del dueño
                Intent intent = new Intent(this, PerfilDuenoActivity.class); // Asumiendo que tienes PerfilDuenoActivity
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            // Añadir otros casos si es necesario
            return false;
        });
    }
}
