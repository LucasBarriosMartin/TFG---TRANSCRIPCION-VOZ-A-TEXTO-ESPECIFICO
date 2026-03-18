# TFG---TRANSCRIPCION-VOZ-A-TEXTO-ESPECIFICO


MEMORIA
https://www.overleaf.com/project/69b2e7ab71451d3023259c99
Dudas:
- Por qué hay tantas páginas en blanco
- Quién son los colaboradores
- Qué es el Estado de la Cuestión
- Para qué queremos los apéndices

SERVIDOR
- Tailscale (para poder conectarnos al servidor con distintas ips):
tailscale status
tailscale up
tailscale login/logout
- Abrir entorno virtual:
source entorno_tfg/bin/activate
-   Apagar el servidor:
sudo poweroff
Si estamos en el entorno hacer antes:
exit
Ejecutar programa:
python -m uvicorn server:app --host 0.0.0.0 --port 8000

Pyannote: corta el audio separando a los hablantes
SpeechBrain: compara las voces en la base de datos y les pone nombre (pip install torch torchaudio speechbrain)

CORRECCIONES
- Que se visualice bien cuando hay modo oscuro ✅ Dia 10/03/2026
- Que no se bloquee la pantalla automaticamente mientras se esté usando la aplicación, pero no se esté tocando la pantalla. ✅ Dia 10/03/2026
- Hay pérdidas de audio en el modelo de Whisper. ✅ Dia 17/03/2026
- Corregir bug scroll automático (correción que no ha funcionado)
- Que se cambie de vosk a whisper sin necesidad de cerrar la aplicación. 
- Cuando pulsamos el botón de parar deja de procesarse todo el audio anterior.
- Que si habla la misma persona no aparezca de nuevo su nombre.
- Convertir la comunicación en HTTPS

TAREAS PENDIENTES (de mayor a menor importancia)
- En el servidor:
  - Que pueda recibir audios de la aplicación y le devuelva el JSON correspondiente. ✅ Dia 17/03/2026
  - Investigar la TRF (Transformación Rápida de Fourier)
  - Que el SpeechRecognition reconozca "Mi configuración de nombre es..." y lo guarde con ese nombre en los embeddings
  - Que el texto se resuma
- En la aplicación Android:
  - Crear la interfaz para que use una IA u otra dependiendo de si tiene conexión o no ✅ Dia 10/03/2026
  - Programar la comunicación con el servidor (envio del audio y recivo del JSON) ✅ Dia 17/03/2026
  - Que la aplicación cambie automaticamente de Vosk a Whisper si detecta que se va el internet o viceversa ✅ Dia 17/03/2026
  - Crear el panel de configuración
  - Hacer que la app pueda funcionar en modo negro ✅ Día 18/03/2026
 
  https://chatgpt.com/share/69a86761-fcb0-8004-87d8-ac4730414b8f
