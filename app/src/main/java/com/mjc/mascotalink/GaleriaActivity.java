package com.mjc.mascotalink;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mjc.mascota.ui.perfil.GaleriaPerfilAdapter;

import java.util.ArrayList;

public class GaleriaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galeria);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        ViewPager2 viewPager = findViewById(R.id.view_pager_galeria_fullscreen);
        TabLayout tabLayout = findViewById(R.id.tab_layout_dots_fullscreen);

        ArrayList<String> imageUrls = getIntent().getStringArrayListExtra("imageUrls");

        if (imageUrls != null && !imageUrls.isEmpty()) {
            GaleriaPerfilAdapter adapter = new GaleriaPerfilAdapter(this, imageUrls);
            viewPager.setAdapter(adapter);

            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                // No need to do anything here, the selector drawable handles the dot state
            }).attach();

            tabLayout.setVisibility(imageUrls.size() > 1 ? View.VISIBLE : View.GONE);
        } else {
            finish(); // No images to show
        }
    }
}
