package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.Query;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaseosActivity extends AppCompatActivity {

    private static final String TAG = "PaseosActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String userRole;

    // Views
    private TabLayout tabLayout;
    private RecyclerView rvPaseos;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout emptyView;
    private TextView tvEmptyTitle, tvEmptySubtitle;
    private Button btnReservarPaseo; // Not used in layout but kept for logic logic if needed or removed
    private BottomNavigationView bottomNav;
    private android.widget.ImageView ivBack;

    // Adapter
    private PaseosAdapter paseosAdapter;
    private List<Paseo> paseosList;

    // Estado actual
    private String estadoActual = ReservaEstadoValidator.ESTADO_ACEPTADO;
    private boolean hasCheckedActiveWalk = false; // Flag to prevent auto-nav loop

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseos);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = currentUser.getUid();

        initViews();
        setupSwipeRefresh();
        com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);

        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
            userRole = cachedRole;
            // FASE 1 - CRÍTICO: NO llamar checkActiveWalkAndRedirect() aquí (race condition)
            // Se llama después de obtener el rol REAL de Firestore en fetchUserRoleAndSetupUI()
            setupRecyclerView(userRole);
            setupTabLayout(userRole);
        }

        fetchUserRoleAndSetupUI();
    }

    private void checkActiveWalkAndRedirect(String role) {
        String fieldToFilter = "PASEADOR".equalsIgnoreCase(role) ? "id_paseador" : "id_dueno";
        DocumentReference userRef = db.collection("usuarios").document(currentUserId);

        db.collection("reservas")
                .whereEqualTo(fieldToFilter, userRef)
                .whereIn("estado", java.util.Arrays.asList("LISTO_PARA_INICIAR", "EN_CURSO"))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        // Si hay un paseo activo o listo para iniciar, priorizar la pestaña "En Curso" (Índice 2)
                        if (tabLayout != null) {
                            TabLayout.Tab tabEnCurso = tabLayout.getTabAt(2);
                            if (tabEnCurso != null && !tabEnCurso.isSelected()) {
                                tabEnCurso.select();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking active walk", e));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Recargar datos al volver a la actividad para asegurar frescura
        if (userRole != null) {
            // Prioridad: Verificar si hay paseo activo para redirigir
            checkActiveWalkAndRedirect(userRole);
            
            cargarPaseos(userRole);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detener listener para ahorrar recursos y datos
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }

    private void initViews() {
        ivBack = findViewById(R.id.btn_back);
        tabLayout = findViewById(R.id.tab_layout);
        rvPaseos = findViewById(R.id.rv_paseos);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyView = findViewById(R.id.empty_view);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);
        // btnReservarPaseo removed from new layout, handled via Empty View logic or menu
        bottomNav = findViewById(R.id.bottom_nav);

        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        paseosList = new ArrayList<>();
    }

    private void fetchUserRoleAndSetupUI() {
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fetchedRole = documentSnapshot.getString("rol");
                        if (fetchedRole != null) {
                            BottomNavManager.saveUserRole(this, fetchedRole);
                            if (!fetchedRole.equalsIgnoreCase(userRole) || paseosAdapter == null) {
                                userRole = fetchedRole;
                                setupRecyclerView(userRole);
                                setupTabLayout(userRole);
                            }

                            // FASE 1 - CRÍTICO: Verificar paseo activo DESPUÉS de obtener rol real (RACE CONDITION FIX)
                            if (!hasCheckedActiveWalk) {
                                checkActiveWalkAndRedirect(fetchedRole);
                                hasCheckedActiveWalk = true;
                            }

                            // Solo cargar si la actividad está visible
                            if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                                cargarPaseos(userRole);
                            }
                        } else {
                            handleRoleError();
                        }
                    } else {
                        handleRoleError();
                    }
                })
                .addOnFailureListener(e -> handleRoleError());
    }

    private void handleRoleError() {
        Toast.makeText(this, "No se pudo verificar el rol del usuario.", Toast.LENGTH_LONG).show();
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = userRole != null ? userRole : "DUEÑO";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_walks);
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    private void setupTabLayout(String role) {
        tabLayout.removeAllTabs();
        
        // Orden lógico de pestañas
        addTab("Aceptados", ReservaEstadoValidator.ESTADO_ACEPTADO);
        addTab("Programados", ReservaEstadoValidator.ESTADO_CONFIRMADO);
        addTab("En Curso", "EN_CURSO");
        addTab("Completados", "COMPLETADO");

        // Seleccionar tab correspondiente al estado actual
        int selectedIndex = getTabIndexForState(estadoActual);
        TabLayout.Tab tab = tabLayout.getTabAt(selectedIndex);
        if (tab != null) {
            tab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String nuevoEstado = (String) tab.getTag();
                if (nuevoEstado != null) {
                    estadoActual = nuevoEstado;
                    cargarPaseos(role);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {
                // Opcional: Recargar si se pulsa de nuevo
                cargarPaseos(role);
            }
        });
    }

    private void addTab(String title, String stateTag) {
        TabLayout.Tab tab = tabLayout.newTab();
        tab.setText(title);
        tab.setTag(stateTag);
        tabLayout.addTab(tab);
    }

    private int getTabIndexForState(String state) {
        switch (state) {
            case ReservaEstadoValidator.ESTADO_ACEPTADO: return 0;
            case ReservaEstadoValidator.ESTADO_CONFIRMADO: return 1;
            case ReservaEstadoValidator.ESTADO_LISTO_PARA_INICIAR:
            case "EN_CURSO": return 2;
            case "COMPLETADO": return 3;
            default: return 0;
        }
    }

    private void setupRecyclerView(String role) {
        rvPaseos.setLayoutManager(new LinearLayoutManager(this));
        paseosAdapter = new PaseosAdapter(this, paseosList, new PaseosAdapter.OnPaseoClickListener() {
            @Override
            public void onPaseoClick(Paseo paseo) {
                if (esPaseoEnCurso(paseo)) {
                    if ("PASEADOR".equalsIgnoreCase(userRole)) {
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    }
                } else {
                    // Expandir detalles o mostrar algo más
                }
            }

            @Override
            public void onVerUbicacionClick(Paseo paseo) {
                // Abrir PaseoEnCursoActivity para LISTO_PARA_INICIAR o EN_CURSO
                if (paseo == null) return;

                String estado = paseo.getEstado();
                if (estado == null) return;

                if (estado.equals("LISTO_PARA_INICIAR") || estado.equals("EN_CURSO")) {
                    if ("PASEADOR".equalsIgnoreCase(userRole)) {
                        // Paseador ve PaseoEnCursoActivity
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    } else {
                        // Dueño ve PaseoEnCursoDuenoActivity
                        Intent intent = new Intent(PaseosActivity.this, PaseoEnCursoDuenoActivity.class);
                        intent.putExtra("id_reserva", paseo.getReservaId());
                        startActivity(intent);
                    }
                }
            }

            @Override
            public void onContactarClick(Paseo paseo) {
                if (paseo == null) return;
                String targetUserId = null;
                if ("PASEADOR".equalsIgnoreCase(userRole) && paseo.getId_dueno() != null) {
                    targetUserId = paseo.getId_dueno().getId();
                } else if (paseo.getId_paseador() != null) {
                    targetUserId = paseo.getId_paseador().getId();
                }
                if (targetUserId == null) {
                    Toast.makeText(PaseosActivity.this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.mjc.mascotalink.util.ChatHelper.openOrCreateChat(PaseosActivity.this, db, currentUserId, targetUserId);
            }

            @Override
            public void onCalificarClick(Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", paseo.getReservaId());
                startActivity(intent);
            }

            @Override
            public void onVerMotivoClick(Paseo paseo) {
                mostrarDialogMotivoCancelacion(paseo);
            }

            @Override
            public void onProcesarPagoClick(Paseo paseo) {
                Intent intent = new Intent(PaseosActivity.this, ConfirmarPagoActivity.class);
                intent.putExtra("reserva_id", paseo.getReservaId());
                intent.putExtra("costo_total", paseo.getCosto_total());
                intent.putExtra("paseador_nombre", paseo.getPaseadorNombre());
                intent.putExtra("mascota_nombre", paseo.getMascotaNombre());
                intent.putExtra("fecha_reserva", paseo.getFechaFormateada());
                intent.putExtra("hora_reserva", paseo.getHoraFormateada());
                intent.putExtra("direccion_recogida", "Calle Principal 123, Ciudad"); // TODO: Get real address
                startActivity(intent);
            }
        }, role);
        rvPaseos.setAdapter(paseosAdapter);
    }

    private boolean esPaseoEnCurso(Paseo paseo) {
        if (paseo == null || paseo.getEstado() == null) return false;
        String estado = paseo.getEstado();
        return "LISTO_PARA_INICIAR".equalsIgnoreCase(estado) ||
               "EN_CURSO".equalsIgnoreCase(estado);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            if (userRole != null) {
                cargarPaseos(userRole);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
        swipeRefresh.setColorSchemeResources(R.color.blue_primary);
    }

    // Listener para actualizaciones en tiempo real
    private com.google.firebase.firestore.ListenerRegistration firestoreListener;

    private void cargarPaseos(String role) {
        // Detener listener anterior si existe para evitar duplicados
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }

        swipeRefresh.setRefreshing(true);
        long startTime = System.currentTimeMillis(); // FASE 1 - PERFORMANCE: Medir tiempo de query

        String fieldToFilter = "PASEADOR".equalsIgnoreCase(role) ? "id_paseador" : "id_dueno";
        DocumentReference userRef = db.collection("usuarios").document(currentUserId);

        // FASE 1 - PERFORMANCE: Query optimizada con whereIn()
        // NOTA: Para mejor performance, crear índice compuesto en Firestore:
        // Collection: reservas
        // Fields: id_dueno (Ascending), estado (Array-contains), fecha (Descending)
        // Fields: id_paseador (Ascending), estado (Array-contains), fecha (Descending)

        Query query;
        // Para "En Curso", incluir LISTO_PARA_INICIAR y EN_CURSO (optimizado con whereIn)
        if ("EN_CURSO".equals(estadoActual)) {
            query = db.collection("reservas")
                    .whereEqualTo(fieldToFilter, userRef)
                    .whereIn("estado", java.util.Arrays.asList("LISTO_PARA_INICIAR", "EN_CURSO"));
        } else {
            query = db.collection("reservas")
                    .whereEqualTo(fieldToFilter, userRef)
                    .whereEqualTo("estado", estadoActual);
        }

        firestoreListener = query.addSnapshotListener((querySnapshot, e) -> {
            // FASE 1 - PERFORMANCE: Log de tiempo de query
            long queryTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, " Query completada en " + queryTime + "ms - Estado: " + estadoActual);

            if (e != null) {
                manejarError(e);
                return;
            }

            if (querySnapshot == null) return;

            // Handle DocumentChanges to reactively remove items that changed state
            boolean hasChanges = false;
            for (com.google.firebase.firestore.DocumentChange dc : querySnapshot.getDocumentChanges()) {
                switch (dc.getType()) {
                    case REMOVED:
                        // Item no longer matches query (e.g., state changed)
                        // Find and remove from local list
                        String removedId = dc.getDocument().getId();
                        for (int i = 0; i < paseosList.size(); i++) {
                            if (paseosList.get(i).getReservaId().equals(removedId)) {
                                paseosList.remove(i);
                                paseosAdapter.notifyItemRemoved(i);
                                hasChanges = true;
                                
                                // If status changed to next logical step, try to redirect
                                String newState = dc.getDocument().getString("estado");
                                if (newState != null && !newState.equals(estadoActual)) {
                                    // Simple logic: If we were in ACEPTADO/CONFIRMADO and it moved forward
                                    // Just check active walk again or reload appropriate tab could be complex.
                                    // For now, just removing it is correct visual feedback.
                                    // If it became "LISTO_PARA_INICIAR" or "EN_CURSO", switch to "En Curso" tab
                                    if ("LISTO_PARA_INICIAR".equals(newState) || "EN_CURSO".equals(newState)) {
                                        if (tabLayout != null) {
                                            TabLayout.Tab tabEnCurso = tabLayout.getTabAt(2);
                                            if (tabEnCurso != null && !tabEnCurso.isSelected()) {
                                                tabEnCurso.select();
                                            }
                                        }
                                    } else if (ReservaEstadoValidator.ESTADO_CONFIRMADO.equals(newState) 
                                            && ReservaEstadoValidator.ESTADO_ACEPTADO.equals(estadoActual)) {
                                         // Move to "Programados" tab automatically
                                         TabLayout.Tab tabProgramados = tabLayout.getTabAt(1);
                                         if (tabProgramados != null && !tabProgramados.isSelected()) {
                                             tabProgramados.select();
                                         }
                                    }
                                }
                                break;
                            }
                        }
                        break;
                    // ADDED/MODIFIED handled by full reload below for simplicity with joins
                }
            }
            
            if (hasChanges && querySnapshot.isEmpty()) {
                 finalizarCarga();
                 return;
            }

            if (querySnapshot.isEmpty()) {
                paseosList.clear();
                if (paseosAdapter != null) {
                    paseosAdapter.updateList(paseosList);
                }
                finalizarCarga();
                return;
            }

            List<Paseo> paseosTemporales = new ArrayList<>();
            List<Task<DocumentSnapshot>> tareas = new ArrayList<>();

            // 1. Carga INMEDIATA de datos básicos (hace que la lista sea responsiva)
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Paseo paseo = doc.toObject(Paseo.class);
                if (paseo == null) continue;
                paseo.setReservaId(doc.getId());
                String mascotaId = doc.getString("id_mascota");
                paseo.setIdMascota(mascotaId);

                // Verificar si el paseo CONFIRMADO debe transicionar a LISTO_PARA_INICIAR
                verificarYTransicionarPaseo(doc.getId(), paseo.getEstado(), paseo.getHora_inicio());

                // Placeholders visuales mientras carga el detalle
                if (paseo.getPaseadorNombre() == null) paseo.setPaseadorNombre("Cargando...");
                if (paseo.getMascotaNombre() == null) paseo.setMascotaNombre("...");

                paseosTemporales.add(paseo);

                // Preparar carga de detalles
                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                String currentMascotaId = paseo.getIdMascota();

                tareas.add(paseadorRef != null ? paseadorRef.get() : Tasks.forResult(null));
                tareas.add(duenoRef != null ? duenoRef.get() : Tasks.forResult(null));
                if (duenoRef != null && currentMascotaId != null && !currentMascotaId.isEmpty()) {
                    tareas.add(db.collection("duenos").document(duenoRef.getId()).collection("mascotas")
                            .document(currentMascotaId).get());
                } else {
                    tareas.add(Tasks.forResult(null));
                }
            }

            // Ordenar preliminarmente y mostrar YA
            java.util.Collections.sort(paseosTemporales, (p1, p2) -> {
                if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                return p2.getFecha().compareTo(p1.getFecha());
            });
            
            paseosList.clear();
            paseosList.addAll(paseosTemporales);
            if (paseosAdapter != null) {
                paseosAdapter.updateList(paseosList);
            }
            // No finalizamos carga (swipeRefresh) todavía, esperamos a los detalles
            if (paseosList.isEmpty()) {
                 rvPaseos.setVisibility(View.GONE);
                 emptyView.setVisibility(View.VISIBLE);
                 actualizarTextoVacio();
            } else {
                 rvPaseos.setVisibility(View.VISIBLE);
                 emptyView.setVisibility(View.GONE);
            }


            // 2. Carga ASÍNCRONA de detalles (enriquece la lista existente)
            if (tareas.isEmpty()) {
                finalizarCarga();
                return;
            }

            Tasks.whenAllSuccess(tareas).addOnSuccessListener(results -> {
                if (isDestroyed() || isFinishing()) return;

                Date now = new Date();
                List<Paseo> nuevosPaseosConDetalles = new ArrayList<>();
                boolean algunCambioDeEstado = false;

                // Reconstruimos la lista iterando sobre el snapshot original para mantener el orden e índices
                List<DocumentSnapshot> docs = querySnapshot.getDocuments();
                int resultIndex = 0;

                for (DocumentSnapshot doc : docs) {
                    Paseo paseo = doc.toObject(Paseo.class);
                    if (paseo == null) {
                        resultIndex += 3;
                        continue;
                    }
                    paseo.setReservaId(doc.getId());
                    paseo.setIdMascota(doc.getString("id_mascota"));

                    // Firebase Function se encarga de CONFIRMADO -> LISTO_PARA_INICIAR
                    // El paseador debe iniciar manualmente usando el boton "Comenzar Paseo"

                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(resultIndex++);
                    DocumentSnapshot duenoDoc = (DocumentSnapshot) results.get(resultIndex++);
                    DocumentSnapshot mascotaDoc = (DocumentSnapshot) results.get(resultIndex++);

                    if (paseadorDoc != null && paseadorDoc.exists()) {
                        paseo.setPaseadorNombre(paseadorDoc.getString("nombre_display"));
                        paseo.setPaseadorFoto(paseadorDoc.getString("foto_perfil"));
                    }
                    if (duenoDoc != null && duenoDoc.exists()) {
                        paseo.setDuenoNombre(duenoDoc.getString("nombre_display"));
                    }
                    if (mascotaDoc != null && mascotaDoc.exists()) {
                        paseo.setMascotaNombre(mascotaDoc.getString("nombre"));
                        paseo.setMascotaFoto(mascotaDoc.getString("foto_principal_url"));
                    } else {
                        paseo.setMascotaNombre("Mascota no encontrada");
                    }
                    nuevosPaseosConDetalles.add(paseo);
                }

                java.util.Collections.sort(nuevosPaseosConDetalles, (p1, p2) -> {
                    if (p1.getFecha() == null || p2.getFecha() == null) return 0;
                    return p2.getFecha().compareTo(p1.getFecha());
                });

                paseosList.clear();
                paseosList.addAll(nuevosPaseosConDetalles);

                if (paseosAdapter != null) {
                    paseosAdapter.updateList(paseosList);
                }
                finalizarCarga();
                
                // Si hubo cambios de estado automáticos, refrescamos tabs si es necesario o dejamos que el listener actúe
                
            }).addOnFailureListener(ex -> {
               Log.e(TAG, "Error cargando detalles relacionados", ex);
               finalizarCarga(); // Finalizar spinner incluso si fallan los detalles
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }

    private void finalizarCarga() {
        swipeRefresh.setRefreshing(false);
        if (paseosList.isEmpty()) {
            rvPaseos.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            actualizarTextoVacio();
        } else {
            rvPaseos.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void actualizarTextoVacio() {
        String titulo;
        String subtitulo;

        switch (estadoActual) {
            case ReservaEstadoValidator.ESTADO_ACEPTADO:
                if ("PASEADOR".equalsIgnoreCase(userRole)) {
                    titulo = "Todo al día";
                    subtitulo = "No tienes paseos esperando confirmación de pago.";
                } else { // DUEÑO
                    titulo = "Ningún pago por confirmar";
                    subtitulo = "Tus solicitudes aceptadas aparecerán aquí.";
                }
                break;
            case ReservaEstadoValidator.ESTADO_CONFIRMADO:
                titulo = "Sin paseos programados";
                subtitulo = "Tus próximos paseos confirmados aparecerán aquí.";
                break;
            case "EN_CURSO":
                titulo = "Ningún paseo activo";
                subtitulo = "No hay paseos en curso actualmente.";
                break;
            case "COMPLETADO":
                titulo = "Sin paseos recientes";
                subtitulo = "Los paseos finalizados se mostrarán aquí.";
                break;
            default:
                titulo = "Sin paseos";
                subtitulo = "No hay información para mostrar.";
                break;
        }
        
        if (tvEmptyTitle != null) tvEmptyTitle.setText(titulo);
        if (tvEmptySubtitle != null) tvEmptySubtitle.setText(subtitulo);
    }

    private void manejarError(Exception e) {
        swipeRefresh.setRefreshing(false);
        Log.e(TAG, "Error al cargar paseos: ", e);
        Toast.makeText(this, "Error al cargar paseos.", Toast.LENGTH_SHORT).show();
        rvPaseos.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    /**
     * Verifica si un paseo CONFIRMADO debe transicionar a LISTO_PARA_INICIAR
     * Transiciona si faltan 15 minutos o menos para el inicio
     */
    private void verificarYTransicionarPaseo(String reservaId, String estado, Date horaInicio) {
        // Solo procesar paseos CONFIRMADO
        if (!"CONFIRMADO".equals(estado) || horaInicio == null) return;

        // Calcular tiempo hasta el inicio
        long ahora = System.currentTimeMillis();
        long horaInicioMillis = horaInicio.getTime();
        long millisHastaInicio = horaInicioMillis - ahora;
        long minutosHastaInicio = millisHastaInicio / (60 * 1000);

        // Si faltan 15 minutos o menos (o ya pasó la hora), transicionar a LISTO_PARA_INICIAR
        if (millisHastaInicio <= (15 * 60 * 1000)) {
            Log.d(TAG, "Transicionando paseo " + reservaId + " a LISTO_PARA_INICIAR (faltan " + minutosHastaInicio + " minutos)");

            db.collection("reservas").document(reservaId)
                .update(
                    "estado", "LISTO_PARA_INICIAR",
                    "hasTransitionedToReady", true,
                    "actualizado_por_sistema", true,
                    "last_updated", com.google.firebase.Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Paseo " + reservaId + " transicionado exitosamente a LISTO_PARA_INICIAR");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Error al transicionar paseo " + reservaId, e);
                });
        }
    }

    private void mostrarDialogMotivoCancelacion(Paseo paseo) {
        if (isFinishing()) return;
        String motivo = paseo.getRazonCancelacion();
        if (motivo == null || motivo.isEmpty()) motivo = "No se especificó un motivo";
        new AlertDialog.Builder(this)
                .setTitle("Motivo de Cancelación")
                .setMessage(motivo)
                .setPositiveButton("Cerrar", null)
                .show();
    }
}

