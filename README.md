# 🐾 Dispensador Smart

Aplicación móvil nativa para Android desarrollada en **Kotlin** con vistas **XML** y conectada a **Firebase**, diseñada para administrar y simular el control remoto de un dispensador inteligente de alimentos para mascotas vía Wi-Fi.

## 📋 Funcionalidades Principales
* **Autenticación y Seguridad:** Inicio de sesión con correo/contraseña, contador de intentos fallidos, bloqueo automático al 3er intento, recuperación de cuenta por correo y bitácora de auditoría de bloqueos.
* **Gestión de Roles:** Diferenciación entre **Administrador** y **Usuario estándar**.
* **Control en Tiempo Real:** Indicador de estado (`En línea` / `Desconectado`) sincronizado en menos de 3 segundos mediante Firebase Realtime Database.
* **Dispensado Manual y Programado:** Selección de porciones, ejecución automática de horarios recurrentes y notificaciones locales en el dispositivo.
* **Historial Paginado:** Registro cronológico completo con paginación y seguimiento acumulado de dispensados y gramos servidos.
* **Panel de Administrador:** Control simulado de conectividad, desbloqueo de cuentas y resumen de actividad.

## 🛠️ Stack Tecnológico
* **Lenguaje:** Kotlin
* **Interfaz:** Vistas XML nativas (compatibilidad desde Android 8.0 / API 26)
* **Backend en la nube (Firebase):**
  * *Firebase Authentication* (acceso y recuperación)
  * *Cloud Firestore* (perfiles, roles, horarios, historial y auditoría)
  * *Firebase Realtime Database* (estado en tiempo real y órdenes inmediatas)

## 🔑 Credenciales de Prueba
| Rol | Correo | Contraseña |
| :--- | :--- | :--- |
| **Administrador** | `admin@test.com` | `123456` |
| **Usuario estándar** | `usuario@test.com` | `123456` |

## 📦 Instalación
El archivo instalable **`DispensadorSmart.apk`** se encuentra disponible en la sección **Releases** de este repositorio.
