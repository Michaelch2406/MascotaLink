const { onRequest } = require("firebase-functions/v2/https");
const { db, FieldValue } = require('../config/firebase');
const { sincronizarPaseador } = require('../utils/sync-helpers');

// --- 🆕 FUNCIÓN DE MIGRACIÓN: Actualizar paseadores_search con campos denormalizados ---
exports.migrarPaseadoresSearch = onRequest(async (req, res) => {
  try {
    console.log("Iniciando migración de paseadores_search con campos denormalizados...");

    // Obtener todos los usuarios con rol PASEADOR
    const usuariosSnapshot = await db.collection("usuarios")
      .where("rol", "==", "PASEADOR")
      .get();

    if (usuariosSnapshot.empty) {
      res.status(200).send({ success: true, message: "No hay paseadores para migrar", total: 0 });
      return;
    }

    const paseadorIds = usuariosSnapshot.docs.map(doc => doc.id);
    console.log(`Encontrados ${paseadorIds.length} paseadores para migrar`);

    let migrados = 0;
    let errores = 0;

    // Procesar en lotes de 10 para no sobrecargar
    for (const paseadorId of paseadorIds) {
      try {
        await sincronizarPaseador(paseadorId);
        migrados++;
        console.log(` Migrado ${paseadorId} (${migrados}/${paseadorIds.length})`);
      } catch (error) {
        errores++;
        console.error(` Error migrando ${paseadorId}:`, error.message);
      }

      // Pausa de 100ms entre cada paseador para no saturar Firestore
      await new Promise(resolve => setTimeout(resolve, 100));
    }

    const resultado = {
      success: true,
      message: "Migración completada",
      total: paseadorIds.length,
      migrados: migrados,
      errores: errores
    };

    console.log("Migración finalizada:", resultado);
    res.status(200).send(resultado);

  } catch (error) {
    console.error("Error en migración:", error);
    res.status(500).send({ success: false, error: error.message });
  }
});

// --- FUNCIÓN DE MANTENIMIENTO (Ejecutar una vez y borrar si se desea) ---
exports.recalcularContadores = onRequest(async (req, res) => {
    try {
        console.log("Iniciando recálculo masivo de paseos completados...");

        // 1. Obtener todas las reservas completadas
        const reservasSnapshot = await db.collection("reservas")
            .where("estado", "==", "COMPLETADO")
            .get();

        console.log(`Se encontraron ${reservasSnapshot.size} reservas completadas en total.`);

        const paseadorCounts = {};
        const duenoCounts = {};

        // 2. Contar paseos
        reservasSnapshot.forEach(doc => {
            const data = doc.data();

            // -- Paseador --
            const paseadorRef = data.id_paseador;
            let paseadorId = null;
            if (paseadorRef) {
                if (typeof paseadorRef === 'string') paseadorId = paseadorRef;
                else if (paseadorRef.id) paseadorId = paseadorRef.id;
            }
            if (paseadorId) {
                if (!paseadorCounts[paseadorId]) paseadorCounts[paseadorId] = 0;
                paseadorCounts[paseadorId]++;
            }

            // -- Dueño --
            const duenoRef = data.id_dueno;
            let duenoId = null;
            if (duenoRef) {
                if (typeof duenoRef === 'string') duenoId = duenoRef;
                else if (duenoRef.id) duenoId = duenoRef.id;
            }
            if (duenoId) {
                if (!duenoCounts[duenoId]) duenoCounts[duenoId] = 0;
                duenoCounts[duenoId]++;
            }
        });

        // 3. Actualizar (Batch)
        const updates = [];
        let batch = db.batch();
        let counter = 0;

        // Actualizar Paseadores
        for (const [pid, total] of Object.entries(paseadorCounts)) {
            const ref = db.collection("paseadores").doc(pid);
            batch.set(ref, { num_servicios_completados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }

        // Actualizar Dueños
        for (const [did, total] of Object.entries(duenoCounts)) {
            const ref = db.collection("duenos").doc(did);
            batch.set(ref, { num_paseos_solicitados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }

        if (counter > 0) {
            updates.push(batch.commit());
        }

        await Promise.all(updates);

        res.status(200).send({
            message: "Recálculo completado exitosamente.",
            paseadores: paseadorCounts,
            duenos: duenoCounts
        });

    } catch (error) {
        console.error("Error en recálculo:", error);
        res.status(500).send({ error: error.message });
    }
});

exports.migrarCampoEsPrimerDia = onRequest(async (req, res) => {
  console.log("🔄 Iniciando migración de es_primer_dia_grupo...");

  try {
    // Obtener todas las reservas de grupos que NO tienen el campo
    const reservasSnapshot = await db.collection("reservas")
      .where("es_grupo", "==", true)
      .get();

    if (reservasSnapshot.empty) {
      console.log(" No hay reservas de grupo para migrar");
      res.status(200).send({ success: true, message: "No hay reservas para migrar", updated: 0 });
      return;
    }

    console.log(` Encontradas ${reservasSnapshot.size} reservas de grupo`);

    // Agrupar reservas por grupo_reserva_id
    const grupos = new Map();

    reservasSnapshot.docs.forEach(doc => {
      const data = doc.data();
      const grupoId = data.grupo_reserva_id;

      if (!grupoId) {
        console.warn(`  Reserva ${doc.id} tiene es_grupo=true pero sin grupo_reserva_id`);
        return;
      }

      if (!grupos.has(grupoId)) {
        grupos.set(grupoId, []);
      }

      grupos.get(grupoId).push({
        id: doc.id,
        fecha: data.fecha,
        tienecampo: data.es_primer_dia_grupo !== undefined
      });
    });

    console.log(`📦 ${grupos.size} grupos encontrados`);

    // Procesar cada grupo
    const batches = [];
    let currentBatch = db.batch();
    let batchCount = 0;
    let totalUpdates = 0;

    grupos.forEach((reservas, grupoId) => {
      // Ordenar por fecha (de menor a mayor)
      reservas.sort((a, b) => {
        const aMillis = a.fecha?.toMillis() || 0;
        const bMillis = b.fecha?.toMillis() || 0;
        return aMillis - bMillis;
      });

      console.log(`  📅 Grupo ${grupoId}: ${reservas.length} reservas`);

      // Marcar cada reserva
      reservas.forEach((reserva, index) => {
        const esPrimera = index === 0;

        // Solo actualizar si no tiene el campo o está incorrecto
        if (!reserva.tienecampo) {
          const ref = db.collection("reservas").doc(reserva.id);
          currentBatch.update(ref, {
            es_primer_dia_grupo: esPrimera
          });

          batchCount++;
          totalUpdates++;

          console.log(`    ${esPrimera ? '🥇' : '  '} ${reserva.id} → es_primer_dia_grupo: ${esPrimera}`);

          // Firestore batch limit is 500
          if (batchCount >= 450) {
            batches.push(currentBatch.commit());
            currentBatch = db.batch();
            batchCount = 0;
          }
        }
      });
    });

    // Commit remaining batch
    if (batchCount > 0) {
      batches.push(currentBatch.commit());
    }

    // Execute all batches in parallel
    await Promise.all(batches);

    console.log(` Migración completada: ${totalUpdates} reservas actualizadas en ${batches.length} batch(es)`);

    res.status(200).send({
      success: true,
      message: `Migración completada exitosamente`,
      grupos: grupos.size,
      totalReservas: reservasSnapshot.size,
      updated: totalUpdates,
      batches: batches.length
    });

  } catch (error) {
    console.error(" Error en migración:", error);
    res.status(500).send({
      success: false,
      error: error.message,
      stack: error.stack
    });
  }
});

exports.migrarResenasADesnormalizado = onRequest({
  timeoutSeconds: 540,
  memory: "512MiB"
}, async (req, res) => {
  try {
      console.log("🚀 Iniciando migración de reseñas...");

      let totalMigradas = 0;
      let totalErrores = 0;
      const errores = [];

      // Migrar resenas_duenos
      console.log("📝 Procesando resenas_duenos...");
      const resenasDuenosSnapshot = await db.collection('resenas_duenos').get();
      console.log(`  Total encontradas: ${resenasDuenosSnapshot.size}`);

      for (const resenaDoc of resenasDuenosSnapshot.docs) {
          try {
              const resenaData = resenaDoc.data();
              const autorId = resenaData.paseadorId || resenaData.autorId;

              if (!autorId) {
                  totalErrores++;
                  continue;
              }

              if (resenaData.autorNombre && resenaData.autorFotoUrl && resenaData.autorId && resenaData.autorRol) {
                  continue;
              }

              const autorDoc = await db.collection('usuarios').doc(autorId).get();

              if (!autorDoc.exists) {
                  errores.push({ resenaId: resenaDoc.id, error: 'Usuario no encontrado' });
                  totalErrores++;
                  continue;
              }

              const autorData = autorDoc.data();
              await resenaDoc.ref.update({
                  autorId: autorId,
                  autorNombre: autorData.nombre_display || 'Paseador',
                  autorFotoUrl: autorData.foto_perfil || null,
                  autorRol: 'paseador'
              });

              totalMigradas++;
          } catch (error) {
              errores.push({ resenaId: resenaDoc.id, error: error.message });
              totalErrores++;
          }
      }

      // Migrar resenas_paseadores
      console.log("📝 Procesando resenas_paseadores...");
      const resenasPaseadoresSnapshot = await db.collection('resenas_paseadores').get();
      console.log(`  Total encontradas: ${resenasPaseadoresSnapshot.size}`);

      for (const resenaDoc of resenasPaseadoresSnapshot.docs) {
          try {
              const resenaData = resenaDoc.data();
              const autorId = resenaData.duenoId || resenaData.autorId;

              if (!autorId) {
                  totalErrores++;
                  continue;
              }

              if (resenaData.autorNombre && resenaData.autorFotoUrl && resenaData.autorId && resenaData.autorRol) {
                  continue;
              }

              const autorDoc = await db.collection('usuarios').doc(autorId).get();

              if (!autorDoc.exists) {
                  errores.push({ resenaId: resenaDoc.id, error: 'Usuario no encontrado' });
                  totalErrores++;
                  continue;
              }

              const autorData = autorDoc.data();
              await resenaDoc.ref.update({
                  autorId: autorId,
                  autorNombre: autorData.nombre_display || 'Usuario de Walki',
                  autorFotoUrl: autorData.foto_perfil || null,
                  autorRol: 'dueno'
              });

              totalMigradas++;
          } catch (error) {
              errores.push({ resenaId: resenaDoc.id, error: error.message });
              totalErrores++;
          }
      }

      const resultado = {
          exito: true,
          totalMigradas,
          totalErrores,
          errores: errores.slice(0, 10),
          mensaje: `✅ Migración completada: ${totalMigradas} reseñas migradas, ${totalErrores} errores`
      };

      console.log("🎉 " + resultado.mensaje);
      res.status(200).json(resultado);

  } catch (error) {
      console.error("❌ Error fatal en migración:", error);
      res.status(500).json({
          exito: false,
          mensaje: `Error en migración: ${error.message}`
      });
  }
});
