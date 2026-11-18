package com.estudio.dispensadorsmart

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : AppCompatActivity() {

    private lateinit var rtdb: DatabaseReference
    private lateinit var db: FirebaseFirestore

    private lateinit var swEstadoDispensador: Switch
    private lateinit var btnAjustesWifi: Button
    private lateinit var lvUsuarios: ListView
    private lateinit var tvResumenActividad: TextView
    private lateinit var btnVolverAdmin: Button

    private val listaCorreos = ArrayList<String>()
    private val listaVisualUsuarios = ArrayList<String>()
    private lateinit var adapterUsuarios: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        rtdb = FirebaseDatabase.getInstance().getReference("dispensador")
        db = FirebaseFirestore.getInstance()

        swEstadoDispensador = findViewById(R.id.swEstadoDispensador)
        btnAjustesWifi = findViewById(R.id.btnAjustesWifi)
        lvUsuarios = findViewById(R.id.lvUsuarios)
        tvResumenActividad = findViewById(R.id.tvResumenActividad)
        btnVolverAdmin = findViewById(R.id.btnVolverAdmin)

        adapterUsuarios = ArrayAdapter(this, R.layout.item_lista_tarjeta, listaVisualUsuarios)
        lvUsuarios.adapter = adapterUsuarios

        // RF-07: Cargar estado actual y permitir cambiarlo
        rtdb.child("estado").get().addOnSuccessListener { snapshot ->
            val estado = snapshot.getValue(String::class.java) ?: "Desconectado"
            swEstadoDispensador.isChecked = (estado == "En línea")
            swEstadoDispensador.text = "Estado actual: $estado"
        }

        swEstadoDispensador.setOnCheckedChangeListener { _, isChecked ->
            val nuevoEstado = if (isChecked) "En línea" else "Desconectado"
            rtdb.child("estado").setValue(nuevoEstado)
            swEstadoDispensador.text = "Estado actual: $nuevoEstado"
            Toast.makeText(this, "Dispensador cambiado a: $nuevoEstado", Toast.LENGTH_SHORT).show()
        }

        // RF-07: Acceso a ajustes de red del teléfono
        btnAjustesWifi.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        // RF-06: Cargar usuarios y permitir desbloquear al tocar
        cargarUsuarios()

        lvUsuarios.setOnItemClickListener { _, _, position, _ ->
            val correoSeleccionado = listaCorreos[position]
            AlertDialog.Builder(this)
                .setTitle("Gestionar cuenta")
                .setMessage("¿Deseas desbloquear y reiniciar los intentos fallidos de $correoSeleccionado?")
                .setPositiveButton("Desbloquear") { _, _ ->
                    db.collection("usuarios").document(correoSeleccionado)
                        .update(mapOf("bloqueado" to false, "intentos_fallidos" to 0))
                        .addOnSuccessListener {
                            // Registrar el desbloqueo en la auditoría
                            val fechaHora = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            val logDesbloqueo = hashMapOf(
                                "evento" to "DESBLOQUEO POR ADMIN",
                                "usuario" to correoSeleccionado,
                                "fecha_hora" to fechaHora,
                                "timestamp" to System.currentTimeMillis()
                            )
                            db.collection("auditoria_seguridad").add(logDesbloqueo)

                            Toast.makeText(this, "Usuario desbloqueado", Toast.LENGTH_SHORT).show()
                            cargarUsuarios()
                            cargarResumenActividad() // Actualiza el resumen al instante
                        }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // RF-06: Resumen de actividad general
        cargarResumenActividad()

        btnVolverAdmin.setOnClickListener { finish() }
    }

    private fun cargarUsuarios() {
        db.collection("usuarios").get().addOnSuccessListener { documentos ->
            listaCorreos.clear()
            listaVisualUsuarios.clear()

            for (doc in documentos) {
                val correo = doc.id
                val rol = doc.getString("rol") ?: "usuario"
                val bloqueado = doc.getBoolean("bloqueado") ?: false
                val intentos = doc.getLong("intentos_fallidos") ?: 0
                val estadoTexto = if (bloqueado) "BLOQUEADA" else "Activa (intentos: $intentos)"

                listaCorreos.add(correo)
                listaVisualUsuarios.add("$correo [$rol]\nEstado: $estadoTexto")
            }
            adapterUsuarios.notifyDataSetChanged()
        }
    }

    private fun cargarResumenActividad() {
        db.collection("historial").get().addOnSuccessListener { docs ->
            val totalDispensados = docs.size()
            db.collection("horarios").get().addOnSuccessListener { horariosDocs ->
                val totalHorarios = horariosDocs.size()

                // Consultar los últimos 3 eventos de bloqueos/desbloqueos
                db.collection("auditoria_seguridad")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(3)
                    .get()
                    .addOnSuccessListener { logsDocs ->
                        val textoLogs = StringBuilder()
                        if (logsDocs.isEmpty) {
                            textoLogs.append("Sin incidentes de bloqueo registrados.")
                        } else {
                            for (log in logsDocs) {
                                val ev = log.getString("evento") ?: ""
                                val usr = log.getString("usuario") ?: ""
                                val fh = log.getString("fecha_hora") ?: ""
                                textoLogs.append("• [$fh] $ev: $usr\n")
                            }
                        }

                        tvResumenActividad.text = "• Dispensados totales: $totalDispensados\n" +
                                "• Horarios programados: $totalHorarios\n\n" +
                                "Últimos eventos de seguridad:\n$textoLogs"
                    }
            }
        }
    }
}