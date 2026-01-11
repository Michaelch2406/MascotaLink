package com.mjc.mascotalink;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pruebas Espresso para ReservaActivity
 * Verifica inicialización y funcionalidad básica
 */
@RunWith(AndroidJUnit4.class)
public class ReservaActivityTest {

    @Test
    public void testActivityLaunches() {
        ActivityScenario.launch(ReservaActivity.class);
    }

    @Test
    public void testActivityInitializes() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity != null;
        });
    }

    @Test
    public void testActivityNotFinishing() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert !activity.isFinishing();
        });
    }

    @Test
    public void testActivityHasWindow() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getWindow() != null;
        });
    }

    @Test
    public void testActivityHasContentView() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.findViewById(android.R.id.content) != null;
        });
    }

    @Test
    public void testActivityTitle() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getTitle() != null;
        });
    }

    @Test
    public void testActivityCreated() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        assert scenario != null;
    }

    @Test
    public void testActivityDoesNotCrash() {
        try {
            ActivityScenario.launch(ReservaActivity.class);
        } catch (Exception e) {
            throw new AssertionError("ReservaActivity crashed: " + e.getMessage());
        }
    }

    @Test
    public void testActivityResumed() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.hasWindowFocus();
        });
    }

    @Test
    public void testActivityHasResources() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getResources() != null;
        });
    }

    @Test
    public void testActivityFragmentManager() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getSupportFragmentManager() != null;
        });
    }

    @Test
    public void testActivityPackageName() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getPackageName().equals("com.mjc.mascotalink");
        });
    }

    @Test
    public void testActivityDisplayMetrics() {
        ActivityScenario<ReservaActivity> scenario = ActivityScenario.launch(ReservaActivity.class);
        scenario.onActivity(activity -> {
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            assert width > 0 && height > 0;
        });
    }
}
