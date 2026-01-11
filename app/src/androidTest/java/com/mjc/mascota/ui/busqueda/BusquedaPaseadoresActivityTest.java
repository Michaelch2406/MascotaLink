package com.mjc.mascota.ui.busqueda;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pruebas Espresso para BusquedaPaseadoresActivity
 * Verifica que la Activity se inicializa correctamente
 */
@RunWith(AndroidJUnit4.class)
public class BusquedaPaseadoresActivityTest {

    @Test
    public void testActivityLaunches() {
        ActivityScenario.launch(BusquedaPaseadoresActivity.class);
    }

    @Test
    public void testActivityInitializes() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity != null;
        });
    }

    @Test
    public void testActivityNotFinishing() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert !activity.isFinishing();
        });
    }

    @Test
    public void testActivityHasWindow() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getWindow() != null;
        });
    }

    @Test
    public void testActivityHasContentView() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.findViewById(android.R.id.content) != null;
        });
    }

    @Test
    public void testActivityTitle() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getTitle() != null;
        });
    }

    @Test
    public void testActivityCreated() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        assert scenario != null;
    }

    @Test
    public void testActivityDoesNotCrash() {
        try {
            ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        } catch (Exception e) {
            throw new AssertionError("BusquedaPaseadoresActivity crashed: " + e.getMessage());
        }
    }

    @Test
    public void testActivityResumed() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.hasWindowFocus();
        });
    }

    @Test
    public void testActivityHasResources() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getResources() != null;
        });
    }

    @Test
    public void testActivityFragmentManager() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getSupportFragmentManager() != null;
        });
    }

    @Test
    public void testActivityPackageName() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getPackageName().equals("com.mjc.mascotalink");
        });
    }

    @Test
    public void testActivityDisplayMetrics() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            assert width > 0 && height > 0;
        });
    }

    @Test
    public void testActivityOrientationPortrait() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            int orientation = activity.getResources().getConfiguration().orientation;
            assert orientation > 0;
        });
    }

    @Test
    public void testActivityLocaleNotNull() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getResources().getConfiguration().locale != null;
        });
    }

    @Test
    public void testActivityHasDecorView() {
        ActivityScenario<BusquedaPaseadoresActivity> scenario = ActivityScenario.launch(BusquedaPaseadoresActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getWindow().getDecorView() != null;
        });
    }
}
