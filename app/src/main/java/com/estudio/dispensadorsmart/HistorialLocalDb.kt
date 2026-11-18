package com.estudio.dispensadorsmart

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistorialLocalDb(context: Context) : SQLiteOpenHelper(context, "dispensador_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE historial_local (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "fecha_hora TEXT, " +
                    "cantidad TEXT, " +
                    "origen TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS historial_local")
        onCreate(db)
    }

    fun guardarRegistroLocal(fechaHora: String, cantidad: String, origen: String) {
        val db = writableDatabase
        val valores = ContentValues().apply {
            put("fecha_hora", fechaHora)
            put("cantidad", cantidad)
            put("origen", origen)
        }
        db.insert("historial_local", null, valores)
        db.close()
    }
}