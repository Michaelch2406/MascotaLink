package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.mjc.mascotalink.ui.home.HomeFragment;
import com.mjc.mascotalink.util.BottomNavManager;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Verificar sesión antes de cargar UI
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        
        // Setup Bottom Nav usando el Manager centralizado
        String role = BottomNavManager.getUserRole(this);
        BottomNavManager.setupBottomNav(this, bottomNav, role, R.id.menu_home);

        // Cargar HomeFragment si es la primera vez
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Asegurar que el ítem correcto esté seleccionado al volver
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.menu_home);
        }
    }
}