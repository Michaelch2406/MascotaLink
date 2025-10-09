package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ZonasServicioActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ZonasServicioActivity";
    private static final String PREFS = "WizardPaseador";
    private static final LatLng ECUADOR_CENTER = new LatLng(-1.8312, -78.1834);

    private GoogleMap mMap;
    private EditText etDireccionZona;
    private ImageView ivGeolocateZona, ivMyLocation;
    private ProgressBar pbGeolocateZona;
    private Button btnAgregarZona, btnGuardarZonas;
    private Slider sliderRadio;
    private TextView tvRadio;

    private Marker currentMarker;
    private Circle currentCircle;
    private List<ZonaServicio> zonasSeleccionadas = new ArrayList<>();
    private String selectedAddressName;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zonas_servicio);

        initViews();
        setupListeners();
        setupLocationServices();
        setupAutocompleteLauncher();
        setupMap();
        loadSavedZones();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etDireccionZona = findViewById(R.id.et_direccion_zona);
        ivGeolocateZona = findViewById(R.id.iv_geolocate_zona);
        pbGeolocateZona = findViewById(R.id.pb_geolocate_zona);
        ivMyLocation = findViewById(R.id.iv_my_location);
        btnAgregarZona = findViewById(R.id.btn_agregar_zona);
        btnGuardarZonas = findViewById(R.id.btn_guardar_zonas);
        sliderRadio = findViewById(R.id.slider_radio);
        tvRadio = findViewById(R.id.tv_radio);

        updateRadioText();
    }

    private void setupListeners() {
        sliderRadio.addOnChangeListener((slider, value, fromUser) -> {
            updateRadioText();
            updateCurrentCircle();
        });

        etDireccionZona.setOnClickListener(v -> launchAutocomplete());
        ivGeolocateZona.setOnClickListener(v -> onGeolocateClick());
        ivMyLocation.setOnClickListener(v -> centerOnUserLocation());
        btnAgregarZona.setOnClickListener(v -> agregarZona());
        btnGuardarZonas.setOnClickListener(v -> guardarZonas());
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchLocationForZone();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAutocompleteLauncher() {
        autocompleteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Place place = Autocomplete.getPlaceFromIntent(result.getData());
                        selectedAddressName = place.getAddress();
                        etDireccionZona.setText(selectedAddressName);
                        if (place.getLatLng() != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15f));
                            updateTemporaryMarker(place.getLatLng());
                        }
                    } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                        Status status = Autocomplete.getStatusFromIntent(result.getData());
                        Log.e(TAG, "Autocomplete error: " + status.getStatusMessage());
                    }
                });
    }

    private void launchAutocomplete() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("EC")
                .build(this);
        autocompleteLauncher.launch(intent);
    }

    private void onGeolocateClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationForZone();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationForZone() {
        showGeolocateLoading(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                updateTemporaryMarker(latLng);
                reverseGeocode(latLng);
            }
            showGeolocateLoading(false);
        }).addOnFailureListener(e -> {
            showGeolocateLoading(false);
            Toast.makeText(this, "No se pudo obtener la ubicación.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showGeolocateLoading(boolean isLoading) {
        pbGeolocateZona.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        ivGeolocateZona.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    private void centerOnUserLocation() {
        if (mMap != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f));
                } else {
                    Toast.makeText(this, "Ubicación no disponible.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Permiso de ubicación no concedido.", Toast.LENGTH_SHORT).show();
        }
    }

    private void reverseGeocode(LatLng latLng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                selectedAddressName = addresses.get(0).getAddressLine(0);
                etDireccionZona.setText(selectedAddressName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Reverse geocoding failed", e);
        }
    }

    private void updateRadioText() {
        tvRadio.setText(String.format(Locale.getDefault(), "Radio: %.1f km", sliderRadio.getValue()));
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ECUADOR_CENTER, 7));
        mMap.setOnMapClickListener(this::onMapClick);
        mMap.setOnMarkerClickListener(marker -> {
            if (marker.getTag() instanceof ZonaServicio) {
                eliminarZona((ZonaServicio) marker.getTag());
                return true;
            }
            return false;
        });
        displaySavedZones();
    }

    private void onMapClick(LatLng latLng) {
        updateTemporaryMarker(latLng);
        reverseGeocode(latLng);
    }

    private void updateTemporaryMarker(LatLng latLng) {
        if (currentMarker != null) currentMarker.remove();
        if (currentCircle != null) currentCircle.remove();

        currentMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Nueva Zona")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        updateCurrentCircle();
        btnAgregarZona.setEnabled(true);
    }

    private void updateCurrentCircle() {
        if (currentMarker == null) return;
        if (currentCircle != null) currentCircle.remove();

        float radiusInMeters = sliderRadio.getValue() * 1000;
        currentCircle = mMap.addCircle(new CircleOptions()
                .center(currentMarker.getPosition())
                .radius(radiusInMeters)
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF));
    }

    private void agregarZona() {
        if (currentMarker == null) {
            Toast.makeText(this, "Selecciona una ubicación en el mapa o búscala", Toast.LENGTH_SHORT).show();
            return;
        }

        String direccion = (selectedAddressName != null && !selectedAddressName.isEmpty()) ? selectedAddressName : "Zona de servicio";
        ZonaServicio nuevaZona = new ZonaServicio(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude, sliderRadio.getValue(), direccion);

        if (verificarSolapamiento(nuevaZona)) {
            Toast.makeText(this, "La nueva zona se superpone con una existente", Toast.LENGTH_SHORT).show();
            return;
        }

        zonasSeleccionadas.add(nuevaZona);
        displaySavedZones(); // Redraw all zones
        clearTemporaryMarker();
        Toast.makeText(this, "Zona agregada", Toast.LENGTH_SHORT).show();
    }

    private void clearTemporaryMarker() {
        if (currentMarker != null) {
            currentMarker.remove();
            currentMarker = null;
        }
        if (currentCircle != null) {
            currentCircle.remove();
            currentCircle = null;
        }
        etDireccionZona.setText("");
        selectedAddressName = "";
        btnAgregarZona.setEnabled(false);
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private boolean verificarSolapamiento(ZonaServicio nuevaZona) {
        for (ZonaServicio zonaExistente : zonasSeleccionadas) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(nuevaZona.latitud, nuevaZona.longitud, zonaExistente.latitud, zonaExistente.longitud, results);
            if (results[0] < (nuevaZona.radio + zonaExistente.radio) * 1000) {
                return true;
            }
        }
        return false;
    }

    private void eliminarZona(ZonaServicio zona) {
        zonasSeleccionadas.remove(zona);
        displaySavedZones();
        Toast.makeText(this, "Zona eliminada", Toast.LENGTH_SHORT).show();
    }

    private void guardarZonas() {
        if (zonasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Debes agregar al menos una zona de servicio", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        Set<String> zonasSet = new HashSet<>();
        for (ZonaServicio zona : zonasSeleccionadas) {
            zonasSet.add(zona.toString());
        }
        editor.putStringSet("zonas_servicio", zonasSet);
        editor.putBoolean("zonas_servicio_completo", true);
        editor.apply();
        Toast.makeText(this, "Zonas de servicio guardadas", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void loadSavedZones() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> zonasGuardadas = prefs.getStringSet("zonas_servicio", new HashSet<>());
        zonasSeleccionadas.clear();
        for (String zonaStr : zonasGuardadas) {
            ZonaServicio zona = ZonaServicio.fromString(zonaStr);
            if (zona != null) {
                zonasSeleccionadas.add(zona);
            }
        }
    }

    private void displaySavedZones() {
        if (mMap == null) return;
        mMap.clear();
        clearTemporaryMarker();
        for (ZonaServicio zona : zonasSeleccionadas) {
            LatLng posicion = new LatLng(zona.latitud, zona.longitud);
            Marker marker = mMap.addMarker(new MarkerOptions().position(posicion).title(zona.direccion).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            marker.setTag(zona);
            mMap.addCircle(new CircleOptions().center(posicion).radius(zona.radio * 1000).strokeColor(Color.GREEN).fillColor(0x2200FF00));
        }
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private static class ZonaServicio {
        double latitud, longitud;
        float radio;
        String direccion;

        ZonaServicio(double lat, double lon, float rad, String dir) {
            this.latitud = lat;
            this.longitud = lon;
            this.radio = rad;
            this.direccion = dir;
        }

        @NonNull
        @Override
        public String toString() {
            return latitud + "," + longitud + "," + radio + "," + direccion;
        }

        static ZonaServicio fromString(String str) {
            try {
                String[] parts = str.split(",", 4);
                if (parts.length == 4) {
                    return new ZonaServicio(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Float.parseFloat(parts[2]), parts[3]);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return null;
        }
    }
}
