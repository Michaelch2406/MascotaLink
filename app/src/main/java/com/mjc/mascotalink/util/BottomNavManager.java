package com.mjc.mascotalink.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.MainActivity;
import com.mjc.mascotalink.MensajesActivity;
import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.PerfilDuenoActivity;
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascotalink.util.UnreadBadgeManager;
import com.google.firebase.auth.FirebaseAuth;

public class BottomNavManager {

    private static final String PREFS_NAME = "MascotaLinkPrefs";
    private static final String KEY_USER_ROLE = "user_role";

    public static void saveUserRole(Context context, String role) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public static String getUserRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ROLE, null);
    }

    public static void clearUserRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_USER_ROLE).apply();
    }

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
        
        // Ensure menu is inflated programmatically since it's removed from XML to prevent flicker.
        if (navView.getMenu().size() == 0) {
            navView.inflateMenu(R.menu.menu_bottom_nav_new);
        }

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
                intent = new Intent(activity, MainActivity.class);
            } else if (itemId == R.id.menu_search) { // Reused ID
                intent = new Intent(activity, isPaseador ? SolicitudesActivity.class : BusquedaPaseadoresActivity.class);
            } else if (itemId == R.id.menu_walks) {
                intent = new Intent(activity, PaseosActivity.class);
            } else if (itemId == R.id.menu_messages) {
                intent = new Intent(activity, MensajesActivity.class);
            } else if (itemId == R.id.menu_perfil) {
                intent = new Intent(activity, isPaseador ? PerfilPaseadorActivity.class : PerfilDuenoActivity.class);
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });

        // Iniciar/registrar badge de no leídos globalmente desde cualquier pantalla con bottom nav
        try {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;
            if (uid != null) {
                UnreadBadgeManager.start(uid);
                UnreadBadgeManager.registerNav(navView, activity);
            }
        } catch (Exception ignored) {
            // Silenciar para no romper flujo si auth/firestore no están listos
        }

        UnreadBadgeManager.registerNav(navView, activity);
    }
}
