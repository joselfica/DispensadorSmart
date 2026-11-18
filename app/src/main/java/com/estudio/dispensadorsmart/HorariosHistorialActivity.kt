package com.estudio.dispensadorsmart

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class HorariosHistorialActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var etHoraHorario: EditText
    private lateinit var etDiasHorario: EditText
    private lateinit var spPorcionHorario: Spinner
    private lateinit var btnGuardarHorario: Button
    private lateinit var lvHorarios: ListView
    private lateinit var tvTotalHistorial: TextView
    private lateinit var contenedorHistorial: LinearLayout
    private lateinit var btnAnterior: Button
    private lateinit var btnSiguiente: Button
    private lateinit var tvPaginaActual: TextView
    private lateinit var btnVolverHorarios: Button

    private val idsHorarios = ArrayList<String>()
    private val estadosHorarios = ArrayList<Boolean>()
    private val listaVisualHorarios = ArrayList<String>()
    private lateinit var adapterHorarios: ArrayAdapter<String>

    // Lista en memoria para paginación fluida
    private val todosLosRegistros = ArrayList<String>()
    private var paginaActual = 1
    private val elementosPorPagina = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_horarios_historial)

        db = FirebaseFirestore.getInstance()

        etHoraHorario = findViewById(R.id.etHoraHorario)
        etDiasHorario = findViewById(R.id.etDiasHorario)
        spPorcionHorario = findViewById(R.id.spPorcionHorario)
        btnGuardarHorario = findViewById(R.id.btnGuardarHorario)
        lvHorarios = findViewById(R.id.lvHorarios)
        tvTotalHistorial = findViewById(R.id.tvTotalHistorial)
        contenedorHistorial = findViewById(R.id.contenedorHistorial)
        btnAnterior = findViewById(R.id.btnAnterior)
        btnSiguiente = findViewById(R.id.btnSiguiente)
        tvPaginaActual = findViewById(R.id.tvPaginaActual)
        btnVolverHorarios = findViewById(R.id.btnVolverHorarios)

        val porciones = arrayOf("30g (Porción pequeña)", "50g (Porción mediana)", "100g (Porción grande)")
        spPorcionHorario.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, porciones)

        adapterHorarios = ArrayAdapter(this, android.R.layout.simple_list_item_1, listaVisualHorarios)
        lvHorarios.adapter = adapterHorarios

        // Guardar horario
        btnGuardarHorario.setOnClickListener {
            val hora = etHoraHorario.text.toString().trim()
            val dias = etDiasHorario.text.toString().trim()
            val porcion = spPorcionHorario.selectedItem.toString()

            if (hora.isEmpty() || dias.isEmpty()) {
                Toast.makeText(this, "Ingresa hora y días", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoHorario = hashMapOf(
                "hora" to hora,
                "dias" to dias,
                "porcion" to porcion,
                "activo" to true
            )

            db.collection("horarios").add(nuevoHorario).addOnSuccessListener {
                etHoraHorario.setText("")
                etDiasHorario.setText("")
                Toast.makeText(this, "Horario guardado", Toast.LENGTH_SHORT).show()
                cargarHorarios()
            }
        }

        // Activar o pausar horario al tocarlo
        lvHorarios.setOnItemClickListener { _, _, position, _ ->
            val docId = idsHorarios[position]
            val nuevoEstado = !estadosHorarios[position]

            db.collection("horarios").document(docId).update("activo", nuevoEstado)
                .addOnSuccessListener {
                    val texto = if (nuevoEstado) "activado" else "pausado"
                    Toast.makeText(this, "Horario $texto", Toast.LENGTH_SHORT).show()
                    cargarHorarios()
                }
        }

        // Botones de paginación
        btnAnterior.setOnClickListener {
            if (paginaActual > 1) {
                paginaActual--
                renderizarPagina()
            }
        }

        btnSiguiente.setOnClickListener {
            val totalPaginas = ceil(todosLosRegistros.size.toDouble() / elementosPorPagina).toInt()
            if (paginaActual < totalPaginas) {
                paginaActual++
                renderizarPagina()
            }
        }

        cargarHorarios()
        cargarHistorialPaginado()

        btnVolverHorarios.setOnClickListener { finish() }
    }

    private fun cargarHorarios() {
        db.collection("horarios").get().addOnSuccessListener { documentos ->
            idsHorarios.clear()
            estadosHorarios.clear()
            listaVisualHorarios.clear()

            for (doc in documentos) {
                val hora = doc.getString("hora") ?: "--:--"
                val dias = doc.getString("dias") ?: ""
                val porcion = doc.getString("porcion") ?: ""
                val activo = doc.getBoolean("activo") ?: true
                val estadoStr = if (activo) "ACTIVO" else "PAUSADO"

                idsHorarios.add(doc.id)
                estadosHorarios.add(activo)
                listaVisualHorarios.add("$hora | $dias | $porcion [$estadoStr]")
            }
            adapterHorarios.notifyDataSetChanged()
        }
    }

    private fun cargarHistorialPaginado() {
        db.collection("historial")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { documentos, error ->
                if (error != null || documentos == null) {
                    tvTotalHistorial.text = "Error al cargar el historial"
                    return@addSnapshotListener
                }

                todosLosRegistros.clear()
                var totalGramos = 0
                val totalEventos = documentos.size()

                if (totalEventos == 0) {
                    tvTotalHistorial.text = "📊 Total: 0 dispensados (0g acumulados)"
                    renderizarPagina()
                    return@addSnapshotListener
                }

                for ((index, doc) in documentos.withIndex()) {
                    val fechaHora = doc.getString("fecha_hora") ?: "Sin fecha"
                    val cantidad = doc.getString("cantidad") ?: "0g"
                    val origen = doc.getString("origen") ?: "Desconocido"

                    val gramos = cantidad.substringBefore("g").trim().toIntOrNull() ?: 0
                    totalGramos += gramos

                    val numero = totalEventos - index
                    val textoTarjeta = "#$numero  •  $fechaHora\nCantidad: $cantidad\nOrigen: $origen"
                    todosLosRegistros.add(textoTarjeta)
                }

                // Resumen global siempre visible
                tvTotalHistorial.text = "📊 Total: $totalEventos dispensados  |  ${totalGramos}g servidos"

                // Reiniciar a página 1 al recibir datos nuevos y dibujar
                paginaActual = 1
                renderizarPagina()
            }
    }

    private fun renderizarPagina() {
        contenedorHistorial.removeAllViews()

        val totalEventos = todosLosRegistros.size
        val totalPaginas = if (totalEventos == 0) 1 else ceil(totalEventos.toDouble() / elementosPorPagina).toInt()

        if (paginaActual > totalPaginas) paginaActual = totalPaginas

        val indiceInicio = (paginaActual - 1) * elementosPorPagina
        val indiceFin = minOf(indiceInicio + elementosPorPagina, totalEventos)

        for (i in indiceInicio until indiceFin) {
            val tarjetaItem = TextView(this).apply {
                text = todosLosRegistros[i]
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 14f
                setLineSpacing(4f, 1f)
                setBackgroundResource(R.drawable.bg_input)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 14)
                layoutParams = params
            }
            contenedorHistorial.addView(tarjetaItem)
        }

        // Actualizar indicador y estado de botones
        tvPaginaActual.text = "Pág. $paginaActual de $totalPaginas"
        btnAnterior.isEnabled = paginaActual > 1
        btnAnterior.alpha = if (btnAnterior.isEnabled) 1.0f else 0.4f

        btnSiguiente.isEnabled = paginaActual < totalPaginas
        btnSiguiente.alpha = if (btnSiguiente.isEnabled) 1.0f else 0.4f
    }
}