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
        if (activity == null || navView == null) {
            return;
        }

        String normalizedRole = role != null ? role : "";
        Object currentTag = navView.getTag();
        if (currentTag instanceof String) {
            String currentTagStr = (String) currentTag;
            if (currentTagStr.equalsIgnoreCase(normalizedRole) && navView.getSelectedItemId() == selectedItemId) {
                return; // Already configured with the same role and selection
            }
        }

        navView.setOnItemSelectedListener(null);
        navView.getMenu().clear();
        navView.inflateMenu(R.menu.menu_bottom_nav_new);

        Menu menu = navView.getMenu();
        MenuItem secondItem = menu.findItem(R.id.menu_search);

        boolean isPaseador = "PASEADOR".equalsIgnoreCase(normalizedRole);
        if (secondItem != null && isPaseador) {
            secondItem.setTitle("Solicitudes");
            secondItem.setIcon(R.drawable.ic_request);
        }

        navView.setSelectedItemId(selectedItemId);
        navView.setTag(normalizedRole);

        final long[] lastClickTime = {0};
        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == selectedItemId) {
                return true; // Already on this screen
            }

            long now = System.currentTimeMillis();
            if (now - lastClickTime[0] < 300) {
                return false; // Debounce rapid taps
            }
            lastClickTime[0] = now;

            Intent intent = null;

            if (itemId == R.id.menu_home) {
                intent = new Intent(activity, isPaseador ? PerfilPaseadorActivity.class : PerfilDuenoActivity.class);
            } else if (itemId == R.id.menu_search) { // Reused ID
                intent = new Intent(activity, isPaseador ? SolicitudesActivity.class : BusquedaPaseadoresActivity.class);
            } else if (itemId == R.id.menu_walks) {
                intent = new Intent(activity, PaseosActivity.class);
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(activity, "Proximamente: Mensajes", Toast.LENGTH_SHORT).show();
                return false; // Do not navigate
            } else if (itemId == R.id.menu_perfil) {
                intent = new Intent(activity, isPaseador ? PerfilPaseadorActivity.class : PerfilDuenoActivity.class);
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }

            return false;
        });
    }
}
