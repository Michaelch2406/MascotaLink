package com.mjc.mascotalink.util;

import android.content.Context;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.mjc.mascotalink.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Gestiona un único listener de no leídos y aplica el badge en cualquier BottomNavigationView registrado.
 */
public final class UnreadBadgeManager {
    private static ListenerRegistration registration;
    private static String listeningUserId;
    private static int lastTotal = 0;
    private static final List<WeakReference<BottomNavigationView>> navRefs = new ArrayList<>();

    private UnreadBadgeManager() {}

    public static synchronized void start(String userId) {
        if (userId == null || userId.isEmpty()) return;
        if (userId.equals(listeningUserId) && registration != null) return;
        stop();
        listeningUserId = userId;
        registration = FirebaseFirestore.getInstance()
                .collection("chats")
                .whereArrayContains("participantes", userId)
                .orderBy("ultimo_timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    int total = 0;
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Map<String, Object> map = (Map<String, Object>) doc.get("mensajes_no_leidos");
                            if (map != null && map.get(userId) instanceof Number) {
                                total += ((Number) map.get(userId)).intValue();
                            }
                        }
                    }
                    updateTotal(total);
                });
    }

    public static synchronized void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        listeningUserId = null;
        lastTotal = 0;
        navRefs.clear();
    }

    public static synchronized void registerNav(BottomNavigationView navView, Context context) {
        if (navView == null || context == null) return;
        cleanupRefs();
        navRefs.add(new WeakReference<>(navView));
        applyBadge(navView, context, lastTotal);
    }

    private static void updateTotal(int total) {
        lastTotal = total;
        cleanupRefs();
        for (WeakReference<BottomNavigationView> ref : navRefs) {
            BottomNavigationView nav = ref.get();
            if (nav != null) {
                Context ctx = nav.getContext();
                applyBadge(nav, ctx, total);
            }
        }
    }

    private static void applyBadge(BottomNavigationView navView, Context context, int total) {
        BadgeDrawable badge = navView.getOrCreateBadge(R.id.menu_messages);
        if (total > 0) {
            badge.setVisible(true);
            badge.setNumber(total);
            badge.setMaxCharacterCount(3);
            badge.setBackgroundColor(context.getColor(R.color.error_red));
            badge.setBadgeTextColor(context.getColor(android.R.color.white));
        } else {
            badge.clearNumber();
            badge.setVisible(false);
        }
    }

    private static void cleanupRefs() {
        Iterator<WeakReference<BottomNavigationView>> it = navRefs.iterator();
        while (it.hasNext()) {
            WeakReference<BottomNavigationView> ref = it.next();
            if (ref.get() == null) {
                it.remove();
            }
        }
    }
}
