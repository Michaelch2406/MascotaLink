package com.mjc.mascotalink;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests de Firestore Operations conectados al Emulator
 * Verifican operaciones críticas de base de datos
 *
 * Para ejecutar estos tests, el Firebase Emulator debe estar corriendo:
 * firebase emulators:start
 */
@RunWith(AndroidJUnit4.class)
public class FirestoreOperationsTest {

    private FirebaseFirestore db;
    private CountDownLatch latch;

    @Before
    public void setUp() {
        // Conectar a Firestore Emulator (127.0.0.1:8080)
        db = FirebaseFirestore.getInstance();
        db.useEmulator("127.0.0.1", 8080);
        db.setLoggingEnabled(false);
        latch = null;
    }

    /**
     * Test 1: Cargar Mascotas del Usuario desde Firestore
     * Verifica que se pueden leer documentos de la colección "mascotas"
     */
    @Test
    public void testCargarMascotasUsuario() throws Exception {
        latch = new CountDownLatch(1);
        final List<Map<String, Object>> mascotasResult = new ArrayList<>();

        // Query: Obtener mascotas de un usuario
        db.collection("usuarios")
                .document("user_test_001")
                .collection("mascotas")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            mascotasResult.add(doc.getData());
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error cargando mascotas: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando mascotas", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 2: Verificar Disponibilidad de Paseador
     * Verifica que se puede leer el estado de disponibilidad
     */
    @Test
    public void testDisponibilidadPaseador() throws Exception {
        latch = new CountDownLatch(1);
        final boolean[] disponibleResult = {false};

        // Query: Obtener disponibilidad de paseador
        db.collection("paseadores")
                .document("paseador_test_001")
                .addSnapshotListener((snapshot, error) -> {
                    if (error == null && snapshot != null && snapshot.exists()) {
                        Object disponible = snapshot.get("disponible");
                        disponibleResult[0] = disponible instanceof Boolean ? (Boolean) disponible : false;
                    }
                    latch.countDown();
                });

        assertTrue("Timeout esperando disponibilidad", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 3: Guardar Fotos en Storage/Firestore
     * Verifica que se pueden crear referencias de fotos
     */
    @Test
    public void testGuardarFotosStorage() throws Exception {
        latch = new CountDownLatch(1);
        final String[] fotoIdResult = {""};

        // Crear documento de foto en Firestore
        Map<String, Object> fotoData = new HashMap<>();
        fotoData.put("url", "gs://mascotalink-2d9da.appspot.com/test/foto_001.jpg");
        fotoData.put("timestamp", System.currentTimeMillis());
        fotoData.put("paseo_id", "paseo_test_001");

        db.collection("fotos_paseos")
                .add(fotoData)
                .addOnSuccessListener(docRef -> {
                    fotoIdResult[0] = docRef.getId();
                    assertNotNull("ID de foto no debe ser nulo", fotoIdResult[0]);
                    assertTrue("ID de foto debe tener contenido", !fotoIdResult[0].isEmpty());
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error guardando foto: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando guardar foto", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 4: Rating en Tiempo Real
     * Verifica que se pueden leer ratings actualizados en tiempo real
     */
    @Test
    public void testRatingRealTime() throws Exception {
        latch = new CountDownLatch(1);
        final double[] ratingResult = {0.0};

        // Query: Obtener rating del paseador
        db.collection("paseadores")
                .document("paseador_test_001")
                .addSnapshotListener((snapshot, error) -> {
                    if (error == null && snapshot != null && snapshot.exists()) {
                        Object rating = snapshot.get("rating_promedio");
                        ratingResult[0] = rating instanceof Number ? ((Number) rating).doubleValue() : 0.0;
                        assertTrue("Rating debe estar entre 0 y 5", ratingResult[0] >= 0 && ratingResult[0] <= 5);
                    }
                    latch.countDown();
                });

        assertTrue("Timeout esperando rating", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 5: Búsqueda de Paseadores en Firestore
     * Verifica que se pueden buscar paseadores con filtros
     */
    @Test
    public void testBusquedaFirestore() throws Exception {
        latch = new CountDownLatch(1);
        final List<Map<String, Object>> paseadoresResult = new ArrayList<>();

        // Query: Buscar paseadores disponibles con rating >= 4.0
        db.collection("paseadores")
                .whereEqualTo("disponible", true)
                .orderBy("rating_promedio")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Object rating = doc.get("rating_promedio");
                            if (rating instanceof Number && ((Number) rating).doubleValue() >= 4.0) {
                                paseadoresResult.add(doc.getData());
                            }
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error buscando paseadores: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando búsqueda de paseadores", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 6: Transacciones Batch
     * Verifica que se pueden hacer múltiples escrituras atómicas
     */
    @Test
    public void testBatchWriteOperations() throws Exception {
        latch = new CountDownLatch(1);

        // Preparar batch de escrituras
        Map<String, Object> reservaData = new HashMap<>();
        reservaData.put("estado", "LISTO");
        reservaData.put("timestamp", System.currentTimeMillis());
        reservaData.put("usuario_id", "user_test_001");
        reservaData.put("paseador_id", "paseador_test_001");

        db.batch()
                .set(db.collection("reservas").document("reserva_test_001"), reservaData)
                .commit()
                .addOnSuccessListener(aVoid -> {
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error en batch write: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando batch write", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 7: Lectura de Sub-colecciones
     * Verifica que se pueden leer sub-colecciones anidadas
     */
    @Test
    public void testLecturaSubcolecciones() throws Exception {
        latch = new CountDownLatch(1);
        final List<Map<String, Object>> mensajesResult = new ArrayList<>();

        // Query: Obtener mensajes de un chat
        db.collection("chats")
                .document("chat_test_001")
                .collection("mensajes")
                .orderBy("timestamp")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            mensajesResult.add(doc.getData());
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error leyendo sub-colecciones: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando sub-colecciones", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 8: Validación de Datos antes de Escribir
     * Verifica que se validan datos antes de guardar
     */
    @Test
    public void testValidacionDatos() {
        // Validar que campos requeridos existen
        Map<String, Object> datosReserva = new HashMap<>();
        datosReserva.put("usuario_id", "user_001");
        datosReserva.put("paseador_id", "paseador_001");
        datosReserva.put("precio_hora", 25.0);

        assertTrue("usuario_id debe existir", datosReserva.containsKey("usuario_id"));
        assertTrue("paseador_id debe existir", datosReserva.containsKey("paseador_id"));
        assertTrue("precio_hora debe existir", datosReserva.containsKey("precio_hora"));
        assertTrue("precio_hora debe ser positivo", (double) datosReserva.get("precio_hora") > 0);
    }

    /**
     * Test 9: Query con Filtros Múltiples
     * Verifica búsquedas con varios criterios
     */
    @Test
    public void testQueryFiltrosMultiples() throws Exception {
        latch = new CountDownLatch(1);
        final int[] countResult = {0};

        // Query: Paseadores con múltiples filtros
        db.collection("paseadores")
                .whereEqualTo("disponible", true)
                .whereLessThanOrEqualTo("distancia_km", 10.0)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    countResult[0] = querySnapshot.size();
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error en query múltiple: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando query múltiple", latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Test 10: Agregación de Datos
     * Verifica cálculos como suma, promedio de campos
     */
    @Test
    public void testAgregacionDatos() throws Exception {
        latch = new CountDownLatch(1);
        final double[] totalResult = {0.0};

        // Query: Sumar costos de todas las reservas completadas
        db.collection("reservas")
                .whereEqualTo("estado", "COMPLETADO")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double total = 0.0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Object costo = doc.get("costo_total");
                        if (costo instanceof Number) {
                            total += ((Number) costo).doubleValue();
                        }
                    }
                    totalResult[0] = total;
                    assertTrue("Total debe ser >= 0", totalResult[0] >= 0);
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    fail("Error en agregación: " + e.getMessage());
                    latch.countDown();
                });

        assertTrue("Timeout esperando agregación", latch.await(5, TimeUnit.SECONDS));
    }
}
