package com.mjc.mascotalink;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests de Almacenamiento Encriptado
 * Verifica que los datos sensibles se guardan y recuperan correctamente con encriptación AES256-GCM
 */
@RunWith(AndroidJUnit4.class)
public class EncryptedStorageTest {

    private Context context;
    private SharedPreferences encryptedPrefs;
    private MasterKey masterKey;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Crear MasterKey para encriptación AES256-GCM
        masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        // Inicializar EncryptedSharedPreferences
        encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "mascotalink_encrypted_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );

        // Limpiar preferencias antes de cada test
        encryptedPrefs.edit().clear().apply();
    }

    /**
     * Test 1: Encriptación de Token de Sesión
     * Verifica que el token se guarda encriptado y se puede recuperar correctamente
     */
    @Test
    public void testEncriptacionToken() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_payload.test_signature";

        // Guardar token encriptado
        encryptedPrefs.edit()
                .putString("session_token", token)
                .apply();

        // Verificar que se guardó
        String recoveredToken = encryptedPrefs.getString("session_token", null);
        assertNotNull("Token debe ser recuperable", recoveredToken);
        assertEquals("Token debe ser idéntico al original", token, recoveredToken);
    }

    /**
     * Test 2: Persistencia de Sesión
     * Verifica que los datos de sesión persisten después de cerrar y abrir
     */
    @Test
    public void testPersistenciaSesion() throws Exception {
        // Guardar datos de sesión
        String userId = "user_12345";
        String userEmail = "test@mascotalink.com";
        long sessionTime = System.currentTimeMillis();

        encryptedPrefs.edit()
                .putString("user_id", userId)
                .putString("user_email", userEmail)
                .putLong("session_start", sessionTime)
                .apply();

        // Simular cierre y reapertura de preferencias
        SharedPreferences newPrefs = EncryptedSharedPreferences.create(
                context,
                "mascotalink_encrypted_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );

        // Verificar que los datos persisten
        assertEquals("User ID debe persistir", userId, newPrefs.getString("user_id", null));
        assertEquals("User Email debe persistir", userEmail, newPrefs.getString("user_email", null));
        assertEquals("Session time debe persistir", sessionTime, newPrefs.getLong("session_start", 0));
    }

    /**
     * Test 3: Recuperación Automática de Sesión
     * Verifica que se puede recuperar la sesión guardada automáticamente
     */
    @Test
    public void testRecuperacionSesionAuto() {
        String userId = "user_67890";
        String sessionToken = "session_token_xyz";
        boolean isLoggedIn = true;

        // Guardar sesión actual
        encryptedPrefs.edit()
                .putString("current_user_id", userId)
                .putString("session_token", sessionToken)
                .putBoolean("is_logged_in", isLoggedIn)
                .apply();

        // Verificar recuperación de sesión
        String recoveredUserId = encryptedPrefs.getString("current_user_id", null);
        String recoveredToken = encryptedPrefs.getString("session_token", null);
        boolean isSessionActive = encryptedPrefs.getBoolean("is_logged_in", false);

        assertNotNull("User ID debe recuperarse", recoveredUserId);
        assertNotNull("Token debe recuperarse", recoveredToken);
        assertTrue("Sesión debe estar activa", isSessionActive);

        assertEquals("User ID debe ser correcto", userId, recoveredUserId);
        assertEquals("Token debe ser correcto", sessionToken, recoveredToken);
    }

    /**
     * Test 4: Almacenamiento de Credenciales
     * Verifica que las credenciales se guardan de forma segura
     */
    @Test
    public void testAlmacenimientoCredenciales() {
        String email = "usuario@mascotalink.com";
        String hashedPassword = "hashed_password_bcrypt_format";
        String role = "DUENO";

        encryptedPrefs.edit()
                .putString("stored_email", email)
                .putString("stored_password_hash", hashedPassword)
                .putString("user_role", role)
                .apply();

        // Verificar que se pueden recuperar
        String recoveredEmail = encryptedPrefs.getString("stored_email", null);
        String recoveredHash = encryptedPrefs.getString("stored_password_hash", null);
        String recoveredRole = encryptedPrefs.getString("user_role", null);

        assertEquals("Email debe ser recuperable", email, recoveredEmail);
        assertEquals("Hash debe ser recuperable", hashedPassword, recoveredHash);
        assertEquals("Role debe ser recuperable", role, recoveredRole);
    }

    /**
     * Test 5: Limpieza de Sesión en Logout
     * Verifica que se limpian todos los datos sensibles al cerrar sesión
     */
    @Test
    public void testLimpiezaSesionLogout() {
        // Guardar datos sensibles
        encryptedPrefs.edit()
                .putString("user_id", "user_001")
                .putString("session_token", "token_xyz")
                .putString("user_email", "test@test.com")
                .apply();

        // Verificar que se guardó
        assertNotNull(encryptedPrefs.getString("user_id", null));

        // Simular logout - limpiar datos
        encryptedPrefs.edit().clear().apply();

        // Verificar que está limpio
        assertNull("User ID debe ser null después de logout", encryptedPrefs.getString("user_id", null));
        assertNull("Token debe ser null después de logout", encryptedPrefs.getString("session_token", null));
        assertNull("Email debe ser null después de logout", encryptedPrefs.getString("user_email", null));
    }

    /**
     * Test 6: Validación de Datos Encriptados
     * Verifica que no se pueden leer datos directamente desde SharedPreferences (no encriptadas)
     */
    @Test
    public void testValidacionEncriptacion() {
        String secretData = "datos_sensibles_123";

        encryptedPrefs.edit()
                .putString("secret_key", secretData)
                .apply();

        // Obtener datos desde SharedPreferences normal (sin encriptación)
        SharedPreferences normalPrefs = context.getSharedPreferences(
                "mascotalink_encrypted_session",
                Context.MODE_PRIVATE
        );

        // El dato directamente no debería ser legible en formato claro
        String directAccess = normalPrefs.getString("secret_key", null);

        // Si no es null, no debería ser igual al original (debería estar encriptado)
        if (directAccess != null) {
            assertNotEquals("Datos directos no deben coincidir con plaintext", secretData, directAccess);
        }

        // Pero desde EncryptedSharedPreferences sí es recuperable
        String encryptedAccess = encryptedPrefs.getString("secret_key", null);
        assertEquals("Debe ser recuperable con EncryptedSharedPreferences", secretData, encryptedAccess);
    }

    /**
     * Test 7: Manejo de Valores Nulos
     * Verifica que se manejan correctamente valores nulos
     */
    @Test
    public void testManejoValoresNulos() {
        // Guardar valores válidos
        encryptedPrefs.edit()
                .putString("valid_key", "valid_value")
                .apply();

        // Intentar obtener keys que no existen
        String nullValue = encryptedPrefs.getString("non_existent_key", "default_value");
        assertEquals("Debe retornar valor por defecto", "default_value", nullValue);

        // Verificar que keys válidas se obtienen correctamente
        String validValue = encryptedPrefs.getString("valid_key", "default_value");
        assertEquals("Debe retornar valor guardado", "valid_value", validValue);
    }

    /**
     * Test 8: Tipos de Datos Múltiples
     * Verifica que diferentes tipos de datos se encriptan correctamente
     */
    @Test
    public void testTiposDatosMultiples() {
        String stringValue = "test_string";
        int intValue = 12345;
        long longValue = 9876543210L;
        float floatValue = 3.14f;
        boolean boolValue = true;

        encryptedPrefs.edit()
                .putString("string_key", stringValue)
                .putInt("int_key", intValue)
                .putLong("long_key", longValue)
                .putFloat("float_key", floatValue)
                .putBoolean("bool_key", boolValue)
                .apply();

        // Verificar recuperación de todos los tipos
        assertEquals("String debe ser igual", stringValue, encryptedPrefs.getString("string_key", ""));
        assertEquals("Int debe ser igual", intValue, encryptedPrefs.getInt("int_key", 0));
        assertEquals("Long debe ser igual", longValue, encryptedPrefs.getLong("long_key", 0));
        assertEquals("Float debe ser igual", floatValue, encryptedPrefs.getFloat("float_key", 0), 0.001);
        assertEquals("Boolean debe ser igual", boolValue, encryptedPrefs.getBoolean("bool_key", false));
    }

    /**
     * Test 9: Actualización de Datos Encriptados
     * Verifica que se pueden actualizar datos sin problemas
     */
    @Test
    public void testActualizacionDatos() {
        // Guardar dato inicial
        encryptedPrefs.edit()
                .putString("user_role", "PASEADOR")
                .apply();

        assertEquals("Rol inicial debe ser PASEADOR", "PASEADOR",
                encryptedPrefs.getString("user_role", ""));

        // Actualizar dato
        encryptedPrefs.edit()
                .putString("user_role", "DUENO")
                .apply();

        assertEquals("Rol actualizado debe ser DUENO", "DUENO",
                encryptedPrefs.getString("user_role", ""));
    }

    /**
     * Test 10: Integridad de Datos Largos
     * Verifica que datos extensos se almacenan correctamente
     */
    @Test
    public void testIntegridadDatosLargos() {
        // Crear un token largo (tokens JWT reales son generalmente 500-1000 chars)
        StringBuilder longToken = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longToken.append("a");
        }

        String largeData = longToken.toString();

        encryptedPrefs.edit()
                .putString("large_token", largeData)
                .apply();

        String recovered = encryptedPrefs.getString("large_token", null);

        assertNotNull("Token largo debe ser recuperable", recovered);
        assertEquals("Token largo debe tener la misma longitud", largeData.length(), recovered.length());
        assertEquals("Token largo debe ser idéntico", largeData, recovered);
    }
}
