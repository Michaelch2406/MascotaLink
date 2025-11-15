package com.mjc.mascotalink.util;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.PerfilDuenoActivity;
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.SolicitudesActivity;

public class BottomNavManager {

    public static void setupBottomNav(Activity activity, BottomNavigationView navView, String role, int selectedItemId) {
        navView.getMenu().clear();
        navView.inflateMenu(R.menu.menu_bottom_nav_new);
        navView.setSelectedItemId(selectedItemId);

        Menu menu = navView.getMenu();
        MenuItem secondItem = menu.findItem(R.id.menu_search);

        if ("PASEADOR".equalsIgnoreCase(role)) {
            secondItem.setTitle("Solicitudes");
            secondItem.setIcon(R.drawable.ic_request);
        }

        navView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == selectedItemId) {
                return true; // Already on this screen
            }

            Intent intent = null;

            if (itemId == R.id.menu_home) {
                if ("PASEADOR".equalsIgnoreCase(role)) {
                    intent = new Intent(activity, PerfilPaseadorActivity.class);
                } else {
                    intent = new Intent(activity, PerfilDuenoActivity.class);
                }
            } else if (itemId == R.id.menu_search) { // Reused ID
                if ("PASEADOR".equalsIgnoreCase(role)) {
                    intent = new Intent(activity, SolicitudesActivity.class);
                } else { // DUEÑO
                    intent = new Intent(activity, BusquedaPaseadoresActivity.class);
                }
            } else if (itemId == R.id.menu_walks) {
                intent = new Intent(activity, PaseosActivity.class);
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(activity, "Próximamente: Mensajes", Toast.LENGTH_SHORT).show();
                return false; // Do not navigate
            } else if (itemId == R.id.menu_perfil) {
                if ("PASEADOR".equalsIgnoreCase(role)) {
                    intent = new Intent(activity, PerfilPaseadorActivity.class);
                } else {
                    intent = new Intent(activity, PerfilDuenoActivity.class);
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                return true;
            }

            return false;
        });
    }
}