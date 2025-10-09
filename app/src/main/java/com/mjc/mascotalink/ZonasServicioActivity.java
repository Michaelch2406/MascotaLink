package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
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
    private Button btnAgregarZona, btnGuardarZonas;
    private Slider sliderRadio;
    private TextView tvRadio, tvValidationMessages;
    private ImageView ivMyLocation;

    private Marker currentMarker;
    private Circle currentCircle;
    private List<ZonaServicio> zonasSeleccionadas = new ArrayList<>();
    private String selectedAddressName;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zonas_servicio);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        setupPlacesAutocomplete();
        setupLocationServices();
        setupMap();
        loadSavedZones();
    }

    private void initViews() {
        btnAgregarZona = findViewById(R.id.btn_agregar_zona);
        btnGuardarZonas = findViewById(R.id.btn_guardar_zonas);
        sliderRadio = findViewById(R.id.slider_radio);
        tvRadio = findViewById(R.id.tv_radio);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        ivMyLocation = findViewById(R.id.iv_my_location);

        updateRadioText();

        sliderRadio.addOnChangeListener((slider, value, fromUser) -> {
            updateRadioText();
            updateCurrentCircle();
        });

        btnAgregarZona.setOnClickListener(v -> agregarZona());
        btnGuardarZonas.setOnClickListener(v -> guardarZonas());
        ivMyLocation.setOnClickListener(v -> fetchCurrentLocation());
    }

    private void setupPlacesAutocomplete() {
        // Initialization is now done in MyApplication.java
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment_zonas);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));
            autocompleteFragment.setHint("Buscar dirección o barrio...");
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    selectedAddressName = place.getAddress();
                    if (place.getLatLng() != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15f));
                        updateTemporaryMarker(place.getLatLng());
                    }
                    Log.i(TAG, "Place Selected: " + selectedAddressName);
                }

                @Override
                public void onError(@NonNull Status status) {
                    Log.e(TAG, "An error occurred during place selection: " + status);
                    Toast.makeText(ZonasServicioActivity.this, "Error al buscar: " + status.getStatusMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                getCurrentLocationAndMoveCamera();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndMoveCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocationAndMoveCamera() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                onMapClick(currentLocation);
            } else {
                Toast.makeText(this, "No se pudo obtener la ubicación actual.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateRadioText() {
        float radio = sliderRadio.getValue();
        tvRadio.setText(String.format(Locale.getDefault(), "Radio: %.1f km", radio));
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
            Object tag = marker.getTag();
            if (tag instanceof ZonaServicio) {
                eliminarZona((ZonaServicio) tag);
            }
            return true;
        });
        displaySavedZones();
    }

    private void onMapClick(LatLng latLng) {
        updateTemporaryMarker(latLng);
        // Intentar obtener nombre de la calle para el texto de búsqueda
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                selectedAddressName = addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            selectedAddressName = "Ubicación seleccionada";
        }
    }

    private void updateTemporaryMarker(LatLng latLng) {
        if (currentMarker != null) {
            currentMarker.remove();
        }
        if (currentCircle != null) {
            currentCircle.remove();
        }
        currentMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Nueva zona de servicio")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        updateCurrentCircle();
        btnAgregarZona.setEnabled(true);
    }

    private void updateCurrentCircle() {
        if (currentMarker == null) return;
        if (currentCircle != null) {
            currentCircle.remove();
        }
        float radio = sliderRadio.getValue() * 1000;
        currentCircle = mMap.addCircle(new CircleOptions()
                .center(currentMarker.getPosition())
                .radius(radio)
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF)
                .strokeWidth(2));
    }

    private void agregarZona() {
        if (currentMarker == null) {
            Toast.makeText(this, "Selecciona una ubicación en el mapa o búscala", Toast.LENGTH_SHORT).show();
            return;
        }

        String direccion = (selectedAddressName != null && !selectedAddressName.isEmpty()) ? selectedAddressName : "Zona de servicio";
        float radio = sliderRadio.getValue();
        LatLng posicion = currentMarker.getPosition();
        ZonaServicio zona = new ZonaServicio(posicion.latitude, posicion.longitude, radio, direccion);

        if (verificarSolapamiento(zona)) {
            Toast.makeText(this, "La nueva zona se superpone con una existente", Toast.LENGTH_SHORT).show();
            return;
        }

        zonasSeleccionadas.add(zona);
        currentMarker.setTag(zona); // Asociar el objeto zona con el marcador
        currentMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        currentMarker.setTitle(direccion);

        // Hacer el círculo permanente
        mMap.addCircle(new CircleOptions()
                .center(posicion)
                .radius(radio * 1000)
                .strokeColor(Color.GREEN)
                .fillColor(0x2200FF00)
                .strokeWidth(2));

        // Limpiar estado temporal
        currentMarker = null;
        if (currentCircle != null) {
            currentCircle.remove();
            currentCircle = null;
        }
        selectedAddressName = "";
        btnAgregarZona.setEnabled(false);
        Toast.makeText(this, "Zona agregada", Toast.LENGTH_SHORT).show();
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private boolean verificarSolapamiento(ZonaServicio nuevaZona) {
        for (ZonaServicio zonaExistente : zonasSeleccionadas) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    nuevaZona.latitud, nuevaZona.longitud,
                    zonaExistente.latitud, zonaExistente.longitud,
                    results);
            float distancia = results[0];
            if (distancia < (nuevaZona.radio + zonaExistente.radio) * 1000) {
                return true; // Hay solapamiento
            }
        }
        return false;
    }

    private void eliminarZona(ZonaServicio zona) {
        zonasSeleccionadas.remove(zona);
        displaySavedZones(); // Redibujar todo
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
        Toast.makeText(this, "Zona eliminada", Toast.LENGTH_SHORT).show();
    }

    private void guardarZonas() {
        if (zonasSeleccionadas.isEmpty()) {
            tvValidationMessages.setText("Debes agregar al menos una zona de servicio");
            tvValidationMessages.setVisibility(View.VISIBLE);
            return;
        }
        tvValidationMessages.setVisibility(View.GONE);
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("zonas_servicio_completo", true);
        Set<String> zonasSet = new HashSet<>();
        for (ZonaServicio zona : zonasSeleccionadas) {
            zonasSet.add(zona.toString());
        }
        editor.putStringSet("zonas_servicio", zonasSet);
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
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private void displaySavedZones() {
        if (mMap == null) return;
        mMap.clear();
        for (ZonaServicio zona : zonasSeleccionadas) {
            LatLng posicion = new LatLng(zona.latitud, zona.longitud);
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(posicion)
                    .title(zona.direccion)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            if (marker != null) {
                marker.setTag(zona);
            }
            mMap.addCircle(new CircleOptions()
                    .center(posicion)
                    .radius(zona.radio * 1000)
                    .strokeColor(Color.GREEN)
                    .fillColor(0x2200FF00)
                    .strokeWidth(2));
        }
    }

    private static class ZonaServicio {
        double latitud;
        double longitud;
        float radio;
        String direccion;

        ZonaServicio(double latitud, double longitud, float radio, String direccion) {
            this.latitud = latitud;
            this.longitud = longitud;
            this.radio = radio;
            this.direccion = direccion;
        }

        @NonNull
        @Override
        public String toString() {
            return latitud + "," + longitud + "," + radio + "," + direccion;
        }

        public static ZonaServicio fromString(String str) {
            try {
                String[] parts = str.split(",", 4);
                if (parts.length >= 4) {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    float rad = Float.parseFloat(parts[2]);
                    String dir = parts[3];
                    return new ZonaServicio(lat, lon, rad, dir);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return null;
        }
    }
}