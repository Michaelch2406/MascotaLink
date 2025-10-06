package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ZonasServicioActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String PREFS = "WizardPaseador";
    private static final LatLng ECUADOR_CENTER = new LatLng(-1.8312, -78.1834);

    private GoogleMap mMap;
    private EditText etDireccionBusqueda;
    private Button btnBuscarDireccion, btnAgregarZona, btnGuardarZonas;
    private Slider sliderRadio;
    private TextView tvRadio, tvValidationMessages;
    private ImageView ivMyLocation;

    private Marker currentMarker;
    private Circle currentCircle;
    private List<ZonaServicio> zonasSeleccionadas = new ArrayList<>();
    private Geocoder geocoder;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zonas_servicio);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        geocoder = new Geocoder(this, Locale.getDefault());

        initViews();
        setupLocationServices();
        setupMap();
        loadSavedZones();
    }

    private void initViews() {
        etDireccionBusqueda = findViewById(R.id.et_direccion_busqueda);
        btnBuscarDireccion = findViewById(R.id.btn_buscar_direccion);
        btnAgregarZona = findViewById(R.id.btn_agregar_zona);
        btnGuardarZonas = findViewById(R.id.btn_guardar_zonas);
        sliderRadio = findViewById(R.id.slider_radio);
        tvRadio = findViewById(R.id.tv_radio);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        ivMyLocation = findViewById(R.id.iv_my_location);

        sliderRadio.setValueFrom(0.5f);
        sliderRadio.setValueTo(5.0f);
        sliderRadio.setValue(2.0f);
        sliderRadio.setStepSize(0.5f);

        updateRadioText();

        sliderRadio.addOnChangeListener((slider, value, fromUser) -> {
            updateRadioText();
            updateCurrentCircle();
        });

        btnBuscarDireccion.setOnClickListener(v -> buscarDireccion());
        btnAgregarZona.setOnClickListener(v -> agregarZona());
        btnGuardarZonas.setOnClickListener(v -> guardarZonas());
        ivMyLocation.setOnClickListener(v -> fetchCurrentLocation());
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
        obtenerDireccion(latLng);
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
                .strokeColor(0x880000FF)
                .fillColor(0x220000FF)
                .strokeWidth(2));
    }

    private void obtenerDireccion(LatLng latLng) {
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String direccion = address.getAddressLine(0);
                if (direccion != null) {
                    etDireccionBusqueda.setText(direccion);
                }
            }
        } catch (IOException e) {
            // Error silencioso
        }
    }

    private void buscarDireccion() {
        String direccion = etDireccionBusqueda.getText().toString().trim();
        if (TextUtils.isEmpty(direccion)) {
            etDireccionBusqueda.setError("Ingresa una dirección");
            return;
        }
        try {
            List<Address> addresses = geocoder.getFromLocationName(direccion, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                updateTemporaryMarker(latLng);
                etDireccionBusqueda.setError(null);
            } else {
                etDireccionBusqueda.setError("Dirección no encontrada");
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error al buscar la dirección", Toast.LENGTH_SHORT).show();
        }
    }

    private void agregarZona() {
        if (currentMarker == null) {
            Toast.makeText(this, "Selecciona una ubicación en el mapa", Toast.LENGTH_SHORT).show();
            return;
        }
        String direccion = etDireccionBusqueda.getText().toString().trim();
        if (TextUtils.isEmpty(direccion)) {
            direccion = "Zona de servicio";
        }
        float radio = sliderRadio.getValue();
        LatLng posicion = currentMarker.getPosition();
        ZonaServicio zona = new ZonaServicio(posicion.latitude, posicion.longitude, radio, direccion);
        if (verificarSolapamiento(zona)) {
            Toast.makeText(this, "Ya existe una zona de servicio cerca de esta ubicación", Toast.LENGTH_SHORT).show();
            return;
        }
        zonasSeleccionadas.add(zona);
        currentMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        currentMarker.setTitle(direccion);
        currentMarker.setTag(zona);
        currentMarker = null;
        if (currentCircle != null) {
            currentCircle.remove();
            currentCircle = null;
        }
        btnAgregarZona.setEnabled(false);
        etDireccionBusqueda.setText("");
        Toast.makeText(this, "Zona agregada correctamente", Toast.LENGTH_SHORT).show();
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private boolean verificarSolapamiento(ZonaServicio nuevaZona) {
        for (ZonaServicio zona : zonasSeleccionadas) {
            double distancia = calcularDistancia(nuevaZona.latitud, nuevaZona.longitud, zona.latitud, zona.longitud);
            double sumaRadios = (nuevaZona.radio + zona.radio) * 1000;
            if (distancia < sumaRadios) {
                return true;
            }
        }
        return false;
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000;
    }

    private void eliminarZona(ZonaServicio zona) {
        zonasSeleccionadas.remove(zona);
        displaySavedZones();
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
            String zonaStr = zona.latitud + "," + zona.longitud + "," + zona.radio + "," + zona.direccion;
            zonasSet.add(zonaStr);
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
            try {
                String[] parts = zonaStr.split(",", 4);
                if (parts.length >= 4) {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    float radio = Float.parseFloat(parts[2]);
                    String direccion = parts[3];
                    zonasSeleccionadas.add(new ZonaServicio(lat, lon, radio, direccion));
                }
            } catch (NumberFormatException e) {
                // Ignorar entradas inválidas
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
                    .strokeColor(0x8800FF00)
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
    }
}
