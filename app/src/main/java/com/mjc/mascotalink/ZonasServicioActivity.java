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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private static final String PREF_ZONAS_SET = "zonas_servicio";
    private static final String PREF_ZONAS_COMPLETO = "zonas_servicio_completo";
    private static final LatLng ECUADOR_CENTER = new LatLng(-1.8312, -78.1834);

    private GoogleMap mMap;
    private EditText etDireccionZona;
    private ImageView ivGeolocateZona, ivMyLocation;
    private ProgressBar pbGeolocateZona;
    private Button btnAgregarZona, btnGuardarZonas;
    private Slider sliderRadio;
    private TextView tvRadio;
    private View loadingOverlay;

    private Marker currentMarker;
    private Circle currentCircle;
    private List<ZonaServicio> zonasSeleccionadas = new ArrayList<>();
    private List<Marker> savedMarkers = new ArrayList<>();
    private List<Circle> savedCircles = new ArrayList<>();
    private String selectedAddressName;
    private boolean isDataLoaded = false;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zonas_servicio);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
        }

        initViews();
        setupListeners();
        setupLocationServices();
        setupAutocompleteLauncher();
        setupMap();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        etDireccionZona = findViewById(R.id.et_direccion_zona);
        ivGeolocateZona = findViewById(R.id.iv_geolocate_zona);
        pbGeolocateZona = findViewById(R.id.pb_geolocate_zona);
        ivMyLocation = findViewById(R.id.iv_my_location);
        btnAgregarZona = findViewById(R.id.btn_agregar_zona);
        btnGuardarZonas = findViewById(R.id.btn_guardar_zonas);
        sliderRadio = findViewById(R.id.slider_radio);
        tvRadio = findViewById(R.id.tv_radio);
        loadingOverlay = findViewById(R.id.loading_overlay);

        btnAgregarZona.setEnabled(false);
        btnGuardarZonas.setEnabled(false);

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
        btnGuardarZonas.setOnClickListener(v -> guardarZonasSeguro());
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
        loadZonasFromFirestore();
    }

    private void loadZonasFromFirestore() {
        if (currentUserId == null) {
            loadZonasFromPrefs();
            return;
        }

        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        db.collection("paseadores").document(currentUserId).collection("zonas_servicio")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    isDataLoaded = true;
                    zonasSeleccionadas.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        com.google.firebase.firestore.GeoPoint centro = null;
                        Object centroObj = document.get("centro");
                        if (centroObj instanceof com.google.firebase.firestore.GeoPoint) {
                            centro = (com.google.firebase.firestore.GeoPoint) centroObj;
                        } else if (centroObj instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) centroObj;
                            if (map.containsKey("latitude") && map.containsKey("longitude")) {
                                centro = new com.google.firebase.firestore.GeoPoint((Double) map.get("latitude"), (Double) map.get("longitude"));
                            } else if (map.containsKey("lat") && map.containsKey("lng")) {
                                centro = new com.google.firebase.firestore.GeoPoint((Double) map.get("lat"), (Double) map.get("lng"));
                            }
                        }

                        Double radioKm = document.getDouble("radio_km");
                        String nombre = document.getString("nombre");

                        if (centro != null && radioKm != null && nombre != null) {
                            zonasSeleccionadas.add(new ZonaServicio(centro.getLatitude(), centro.getLongitude(), radioKm.floatValue(), nombre));
                        }
                    }
                    displaySavedZones();
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar zonas de servicio.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading zones from Firestore", e);
                });
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
        if (currentUserId != null && !isDataLoaded) {
            Toast.makeText(this, "Espere a que carguen las zonas guardadas.", Toast.LENGTH_SHORT).show();
            return;
        }

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

    // Nuevo flujo seguro: guarda en prefs para el registro y, si hay usuario logueado, en Firestore.
    private void guardarZonasSeguro() {
        if (currentUserId != null && !isDataLoaded) {
            Toast.makeText(this, "Espere a que se sincronicen los datos.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (zonasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Agrega al menos una zona.", Toast.LENGTH_SHORT).show();
            return;
        }

        persistZonasInPrefs();

        if (currentUserId == null) {
            Toast.makeText(this, "Zonas guardadas para el registro.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        btnGuardarZonas.setEnabled(false);

        com.google.firebase.firestore.CollectionReference zonasRef = db.collection("paseadores").document(currentUserId).collection("zonas_servicio");

        zonasRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            com.google.firebase.firestore.WriteBatch batch = db.batch();

            // Delete all old zones
            for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                batch.delete(doc.getReference());
            }

            // Add all new zones
            for (ZonaServicio zona : zonasSeleccionadas) {
                com.google.firebase.firestore.DocumentReference newZoneRef = zonasRef.document();
                java.util.Map<String, Object> zonaData = new java.util.HashMap<>();
                zonaData.put("nombre", zona.direccion);
                zonaData.put("radio_km", zona.radio);
                zonaData.put("centro", new com.google.firebase.firestore.GeoPoint(zona.latitud, zona.longitud));
                zonaData.put("activo", true);
                batch.set(newZoneRef, zonaData);
            }

            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Zonas de servicio guardadas con Éxito", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Error al guardar las zonas: " + e.getMessage(), Toast.LENGTH_LONG).show();
                btnGuardarZonas.setEnabled(true);
            });

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al consultar zonas anteriores: " + e.getMessage(), Toast.LENGTH_LONG).show();
            btnGuardarZonas.setEnabled(true);
        });
    }

    // Método anterior (no usado) se mantiene para compatibilidad, pero el click usa guardarZonasSeguro()
    private void guardarZonas() {
        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardarZonas.setEnabled(false);

        com.google.firebase.firestore.CollectionReference zonasRef = db.collection("paseadores").document(currentUserId).collection("zonas_servicio");

        zonasRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            com.google.firebase.firestore.WriteBatch batch = db.batch();

            // Delete all old zones
            for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                batch.delete(doc.getReference());
            }

            // Add all new zones
            for (ZonaServicio zona : zonasSeleccionadas) {
                com.google.firebase.firestore.DocumentReference newZoneRef = zonasRef.document();
                java.util.Map<String, Object> zonaData = new java.util.HashMap<>();
                zonaData.put("nombre", zona.direccion);
                zonaData.put("radio_km", zona.radio);
                zonaData.put("centro", new com.google.firebase.firestore.GeoPoint(zona.latitud, zona.longitud));
                zonaData.put("activo", true);
                batch.set(newZoneRef, zonaData);
            }

            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Zonas de servicio guardadas con éxito", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Error al guardar las zonas: " + e.getMessage(), Toast.LENGTH_LONG).show();
                btnGuardarZonas.setEnabled(true);
            });

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al consultar zonas anteriores: " + e.getMessage(), Toast.LENGTH_LONG).show();
            btnGuardarZonas.setEnabled(true);
        });
    }

    private void displaySavedZones() {
        if (mMap == null) return;

        // Eliminar solo los marcadores y círculos guardados anteriormente, NO el marcador temporal
        for (Marker marker : savedMarkers) {
            if (marker != null) marker.remove();
        }
        for (Circle circle : savedCircles) {
            if (circle != null) circle.remove();
        }
        savedMarkers.clear();
        savedCircles.clear();

        // Redibujar todas las zonas guardadas en verde
        for (ZonaServicio zona : zonasSeleccionadas) {
            LatLng posicion = new LatLng(zona.latitud, zona.longitud);
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(posicion)
                    .title(zona.direccion)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            if (marker != null) {
                marker.setTag(zona);
                savedMarkers.add(marker);
            }
            Circle circle = mMap.addCircle(new CircleOptions()
                    .center(posicion)
                    .radius(zona.radio * 1000)
                    .strokeColor(Color.GREEN)
                    .fillColor(0x2200FF00));
            if (circle != null) {
                savedCircles.add(circle);
            }
        }
        btnGuardarZonas.setEnabled(!zonasSeleccionadas.isEmpty());
    }

    private void persistZonasInPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> zonaStrings = new HashSet<>();
        for (ZonaServicio z : zonasSeleccionadas) {
            zonaStrings.add(z.toString());
        }
        prefs.edit()
                .putStringSet(PREF_ZONAS_SET, zonaStrings)
                .putBoolean(PREF_ZONAS_COMPLETO, !zonaStrings.isEmpty())
                .apply();
    }

    private void loadZonasFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> zonaStrings = prefs.getStringSet(PREF_ZONAS_SET, new HashSet<>());
        zonasSeleccionadas.clear();
        for (String zStr : zonaStrings) {
            ZonaServicio z = ZonaServicio.fromString(zStr);
            if (z != null) {
                zonasSeleccionadas.add(z);
            }
        }
        displaySavedZones();
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
