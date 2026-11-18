package com.estudio.dispensadorsmart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var rtdb: DatabaseReference
    private lateinit var db: FirebaseFirestore

    private lateinit var tvBienvenida: TextView
    private lateinit var tvEstadoDispensador: TextView
    private lateinit var spPorcion: Spinner
    private lateinit var btnDispensar: Button
    private lateinit var btnHorariosHistorial: Button
    private lateinit var btnPanelAdmin: Button
    private lateinit var btnCerrarSesion: Button

    private var estadoActual = "Desconectado"
    private var correoUsuario = ""
    private var rolUsuario = "usuario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pedir permiso de notificaciones en Android 13+ (RF-13)
        pedirPermisoNotificaciones()

        // Recuperar datos enviados desde LoginActivity
        rolUsuario = intent.getStringExtra("ROL_USUARIO") ?: "usuario"
        correoUsuario = intent.getStringExtra("CORREO_USUARIO") ?: "usuario@test.com"

        // Inicializar Firebase
        rtdb = FirebaseDatabase.getInstance().getReference("dispensador")
        db = FirebaseFirestore.getInstance()

        // Enlazar vistas
        tvBienvenida = findViewById(R.id.tvBienvenida)
        tvEstadoDispensador = findViewById(R.id.tvEstadoDispensador)
        spPorcion = findViewById(R.id.spPorcion)
        btnDispensar = findViewById(R.id.btnDispensar)
        btnHorariosHistorial = findViewById(R.id.btnHorariosHistorial)
        btnPanelAdmin = findViewById(R.id.btnPanelAdmin)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        tvBienvenida.text = "Conectado como: $correoUsuario ($rolUsuario)"

        // RF-05: Mostrar botón Panel Admin solo si el rol es "admin"
        if (rolUsuario == "admin") {
            btnPanelAdmin.visibility = View.VISIBLE
        } else {
            btnPanelAdmin.visibility = View.GONE
        }

        // RF-10: Configurar selector de porciones
        val opcionesPorcion = arrayOf("30g (Porción pequeña)", "50g (Porción mediana)", "100g (Porción grande)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesPorcion)
        spPorcion.adapter = adapter

        // RF-08: Escuchar estado del dispensador en tiempo real
        escucharEstadoDispensador()

        // RF-09: Acción de dispensado manual
        btnDispensar.setOnClickListener {
            ejecutarDispensadoManual()
        }

        // Navegación a las pantallas de la Fase 4
        btnHorariosHistorial.setOnClickListener {
            startActivity(Intent(this, HorariosHistorialActivity::class.java))
        }

        btnPanelAdmin.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }

        // Cerrar sesión
        btnCerrarSesion.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun escucharEstadoDispensador() {
        rtdb.child("estado").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                estadoActual = snapshot.getValue(String::class.java) ?: "Desconectado"
                tvEstadoDispensador.text = estadoActual

                if (estadoActual == "En línea") {
                    tvEstadoDispensador.setTextColor(Color.parseColor("#2E7D32")) // Verde
                    btnDispensar.isEnabled = true
                } else {
                    tvEstadoDispensador.setTextColor(Color.parseColor("#D32F2F")) // Rojo
                    btnDispensar.isEnabled = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstadoDispensador.text = "Error de red"
            }
        })
    }

    private fun ejecutarDispensadoManual() {
        if (estadoActual != "En línea") {
            Toast.makeText(this, "El dispensador está desconectado", Toast.LENGTH_SHORT).show()
            return
        }

        val porcionSeleccionada = spPorcion.selectedItem.toString()
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaHoraActual = formatoFecha.format(Date())

        btnDispensar.isEnabled = false
        btnDispensar.text = "ENVIANDO ORDEN..."

        // 1. Enviar orden a Realtime Database (RNF-04: < 3 segundos)
        val ordenMap = mapOf(
            "porcion" to porcionSeleccionada,
            "usuario" to correoUsuario,
            "fecha_hora" to fechaHoraActual
        )

        rtdb.child("ultima_orden").setValue(ordenMap).addOnSuccessListener {
            // 2. Guardar registro en el Historial de Firestore (RF-12)
            val historialMap = hashMapOf(
                "fecha_hora" to fechaHoraActual,
                "cantidad" to porcionSeleccionada,
                "origen" to "Manual ($correoUsuario)",
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("historial").add(historialMap).addOnSuccessListener {
                btnDispensar.isEnabled = true
                btnDispensar.text = "DISPENSAR AHORA"
                Toast.makeText(this, "¡Dispensado exitoso: $porcionSeleccionada!", Toast.LENGTH_SHORT).show()
                lanzarNotificacionLocal("Dispensador Smart", "Se dispensaron $porcionSeleccionada correctamente.")
            }.addOnFailureListener {
                btnDispensar.isEnabled = true
                btnDispensar.text = "DISPENSAR AHORA"
                Toast.makeText(this, "Orden enviada, pero falló al guardar historial", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            btnDispensar.isEnabled = true
            btnDispensar.text = "DISPENSAR AHORA"
            Toast.makeText(this, "Error al enviar orden al dispensador", Toast.LENGTH_SHORT).show()
        }
    }

    // RF-13: Notificación local
    private fun lanzarNotificacionLocal(titulo: String, mensaje: String) {
        val channelId = "canal_dispensador_smart"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones Dispensador",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}