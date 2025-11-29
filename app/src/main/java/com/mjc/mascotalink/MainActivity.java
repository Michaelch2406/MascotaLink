package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mjc.mascotalink.ui.home.HomeFragment;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.UnreadBadgeManager;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = current.getUid();

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        String role = BottomNavManager.getUserRole(this);
        BottomNavManager.setupBottomNav(this, bottomNav, role, R.id.menu_home);
        UnreadBadgeManager.start(currentUserId);
        UnreadBadgeManager.registerNav(bottomNav, this);

        // Handle notification deep link for Chat
        if (getIntent() != null && getIntent().hasExtra("chat_id") && getIntent().hasExtra("id_otro_usuario")) {
            String chatId = getIntent().getStringExtra("chat_id");
            String otherUserId = getIntent().getStringExtra("id_otro_usuario");

            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("chat_id", chatId);
            chatIntent.putExtra("id_otro_usuario", otherUserId);
            startActivity(chatIntent);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.menu_home);
        }
        UnreadBadgeManager.registerNav(bottomNav, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
