package com.estudio.dispensadorsmart

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRecuperar: Button
    private lateinit var tvEstadoLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etCorreo = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRecuperar = findViewById(R.id.btnRecuperar)
        tvEstadoLogin = findViewById(R.id.tvEstadoLogin)

        btnLogin.setOnClickListener {
            val correo = etCorreo.text.toString().trim().lowercase()
            val password = etPassword.text.toString().trim()

            if (correo.isEmpty() || password.isEmpty()) {
                tvEstadoLogin.text = "Por favor, completa correo y contraseña."
                return@setOnClickListener
            }

            tvEstadoLogin.text = "Verificando..."
            btnLogin.isEnabled = false
            verificarYEntrar(correo, password)
        }

        // RF-04: Recuperación de contraseña mediante Firebase Auth
        btnRecuperar.setOnClickListener {
            val correo = etCorreo.text.toString().trim().lowercase()
            if (correo.isEmpty()) {
                tvEstadoLogin.text = "Escribe tu correo arriba para enviarte el enlace."
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(correo).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Al solicitar recuperación, desbloqueamos la cuenta en Firestore
                    db.collection("usuarios").document(correo)
                        .update(mapOf("bloqueado" to false, "intentos_fallidos" to 0))
                    tvEstadoLogin.text = "Correo enviado. Revisa tu bandeja y vuelve a entrar."
                    Toast.makeText(this, "Enlace de recuperación enviado", Toast.LENGTH_LONG).show()
                } else {
                    tvEstadoLogin.text = "No se pudo enviar el correo. Verifica que esté bien escrito."
                }
            }
        }
    }

    private fun verificarYEntrar(correo: String, password: String) {
        val docRef = db.collection("usuarios").document(correo)

        docRef.get().addOnSuccessListener { documento ->
            if (!documento.exists()) {
                btnLogin.isEnabled = true
                tvEstadoLogin.text = "El correo no está registrado en la base de datos."
                return@addOnSuccessListener
            }

            val bloqueado = documento.getBoolean("bloqueado") ?: false
            val intentosActuales = documento.getLong("intentos_fallidos")?.toInt() ?: 0
            val rol = documento.getString("rol") ?: "usuario"

            // RF-03: Bloqueo automático activo
            if (bloqueado) {
                btnLogin.isEnabled = true
                tvEstadoLogin.text = "CUENTA BLOQUEADA. Usa 'Recuperar cuenta' o pide desbloqueo al Admin."
                return@addOnSuccessListener
            }

            // RF-01: Validar credenciales con Firebase Auth
            auth.signInWithEmailAndPassword(correo, password).addOnCompleteListener { task ->
                btnLogin.isEnabled = true

                if (task.isSuccessful) {
                    // Éxito: reiniciar contador a cero
                    docRef.update("intentos_fallidos", 0)
                    tvEstadoLogin.text = ""

                    // RF-05: Pasar el rol y correo a la pantalla principal
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("ROL_USUARIO", rol)
                    intent.putExtra("CORREO_USUARIO", correo)
                    startActivity(intent)
                    finish()
                } else {
                    // RF-02 y RF-03: Contabilizar intento fallido
                    val nuevosIntentos = intentosActuales + 1
                    if (nuevosIntentos >= 3) {
                        docRef.update(mapOf("intentos_fallidos" to 3, "bloqueado" to true))

                        // Guardar registro histórico del bloqueo
                        val fechaHora = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        val logBloqueo = hashMapOf(
                            "evento" to "BLOQUEO DE CUENTA (3 intentos fallidos)",
                            "usuario" to correo,
                            "fecha_hora" to fechaHora,
                            "timestamp" to System.currentTimeMillis()
                        )
                        db.collection("auditoria_seguridad").add(logBloqueo)

                        tvEstadoLogin.text = "CUENTA BLOQUEADA tras 3 intentos fallidos."
                    } else {
                        val restantes = 3 - nuevosIntentos
                        docRef.update("intentos_fallidos", nuevosIntentos)
                        tvEstadoLogin.text = "Contraseña incorrecta. Te quedan $restantes intento(s)."
                    }
                }
            }
        }.addOnFailureListener { e ->
            btnLogin.isEnabled = true
            tvEstadoLogin.text = "Error de conexión con Firestore: ${e.message}"
        }
    }
}