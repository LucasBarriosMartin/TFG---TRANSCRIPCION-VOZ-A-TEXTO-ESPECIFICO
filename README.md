# TFG---TRANSCRIPCION-VOZ-A-TEXTO-ESPECIFICO

SERVIDOR
- Tailscale (para poder conectarnos al servidor con distintas ips):
tailscale status
tailscale up
tailscale login/logout
- Abrir entorno virtual:
source entorno_tfg/bin/activate
-   Apagar el servidor:
Si estamos en el entorno:
exit
sudo poweroff
Si no estamos en el entorno:
sudo shutdown

Pyannote: corta el audio separando a los hablantes
SpeechBrain: compara las voces en la base de datos y les pone nombre (pip install torch torchaudio speechbrain)

CORRECCIONES
- Que se visualice bien cuando hay modo oscuro
- Que no se bloquee la pantalla automaticamente mientras se esté usando la aplicación, pero no se esté tocando la pantalla.
- Corregir bug scroll aurtomático
- Convertir la comunicación en HTTPS

TAREAS PENDIENTES (de mayor a menor importancia)
- En el servidor:
  - Que pueda recibir audios de la aplicación y le devuelva el JSON correspondiente.
  - Que el SpeechRecognition reconozca "Mi configuración de nombre es..." y lo guarde con ese nombre en los embeddings
  - Que el texto se resuma
- En la aplicación Android:
  - Crear la interfaz para que use una IA u otra dependiendo de si tiene conexión o no
  - Programar la comunicación con el servidor (envio del audio y recivo del JSON)
  - Crear el panel de configuración y que la app pueda funcionar en modo negro
  - Que la aplicación cambie automaticamente de Vosk a Whisper si detecta que se va el internet o viceversa
 
  https://chatgpt.com/share/69a86761-fcb0-8004-87d8-ac4730414b8f
