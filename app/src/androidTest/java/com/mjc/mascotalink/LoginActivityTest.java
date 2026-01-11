package com.mjc.mascotalink;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pruebas Espresso para LoginActivity
 * Verifica que la Activity se inicializa sin crashear
 */
@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Test
    public void testActivityLaunches() {
        ActivityScenario.launch(LoginActivity.class);
    }

    @Test
    public void testActivityInitializes() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity != null;
        });
    }

    @Test
    public void testActivityNotFinishing() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert !activity.isFinishing();
        });
    }

    @Test
    public void testActivityHasWindow() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getWindow() != null;
        });
    }

    @Test
    public void testActivityHasContentView() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.findViewById(android.R.id.content) != null;
        });
    }

    @Test
    public void testActivityTitle() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getTitle() != null;
        });
    }

    @Test
    public void testActivityHasDecorView() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getWindow().getDecorView() != null;
        });
    }

    @Test
    public void testActivityCreatedSuccessfully() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        assert scenario != null;
    }

    @Test
    public void testActivityDoesNotCrash() {
        try {
            ActivityScenario.launch(LoginActivity.class);
        } catch (Exception e) {
            throw new AssertionError("LoginActivity crashed: " + e.getMessage());
        }
    }

    @Test
    public void testActivityResumed() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.hasWindowFocus();
        });
    }

    @Test
    public void testActivityConfigurationChange() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            android.content.res.Configuration config = activity.getResources().getConfiguration();
            assert config != null;
        });
    }

    @Test
    public void testActivityLocaleSet() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            assert activity.getResources().getConfiguration().locale != null;
        });
    }

    @Test
    public void testActivityScreenDensity() {
        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.onActivity(activity -> {
            float density = activity.getResources().getDisplayMetrics().density;
            assert density > 0;
        });
    }
}
