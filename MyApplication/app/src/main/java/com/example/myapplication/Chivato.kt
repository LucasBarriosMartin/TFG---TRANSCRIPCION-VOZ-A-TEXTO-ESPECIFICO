import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class Chivato(context: Context) {

    // Cogemos el servicio del sistema de Android que vigila las redes
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // OPCIÓN A: ¿Tengo internet AHORA MISMO? (Para cuando pulsas el botón)
    fun hayConexion(): Boolean {
        val redActual = connectivityManager.activeNetwork ?: return false
        val capacidades = connectivityManager.getNetworkCapabilities(redActual) ?: return false

        // Comprobamos si la red tiene capacidad para salir a Internet
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Para hacer el cambiazo entre Vosk y Whisper
    fun escucharCambiosDeRed(alCambiarEstado: (Boolean) -> Unit) {
        val chivato = object : ConnectivityManager.NetworkCallback() {

            // ¡Internet ha vuelto! (Pasamos al modo Online - Whisper)
            override fun onAvailable(network: Network) {
                alCambiarEstado(true)
            }

            // ¡Nos quedamos sin conexión! (Pasamos al modo Offline - Vosk)
            override fun onLost(network: Network) {
                alCambiarEstado(false)
            }
        }

        // Le decimos a Android que active nuestro chivato
        connectivityManager.registerDefaultNetworkCallback(chivato)
    }
}