# TFG---TRANSCRIPCION-VOZ-A-TEXTO-ESPECIFICO

MEMORIA
https://www.overleaf.com/project/69b2e7ab71451d3023259c99
Dudas:
- Diferencia entre motivación y objetivos: motivacion de la carencia y cuales son los objetivos y los objetivos
- La aplicación es una comparativa, ¿deberiamos comernos la cabeza para que la aplicación cambie dinámicamente? Entonces, ¿cómo cambiaría la motivación en ese caso?
- ¿Se puede poner en la bibliografía el documento de Eugenio o referenciarlo de alguna manera?: no lo copiamos, ponemos resumen como que hemos hablado eso
- Qué es el Estado de la Cuestión: es lo mismo que estado del arte
- Para qué queremos los apéndices: para meter el manual de uso accesible

SERVIDOR
- Tailscale (para poder conectarnos al servidor con distintas ips):
tailscale status
tailscale up
tailscale login/logout
- Abrir entorno virtual:
source entorno_tfg/bin/activate
- Lanzar el servidor (lanzar servidor antes de levantar Caddy):
python -m uvicorn optimizado_medio:app --host 127.0.0.1 --port 8000
- Lanzar Caddy (para la conexión HTTPS):
sudo caddy run
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
- Que se cambie de vosk a whisper sin necesidad de cerrar la aplicación. ✅ Dia 06/04/2026
- Cuando pulsamos el botón de parar deja de procesarse todo el audio anterior. ✅ Dia 06/04/2026
- Corregir bug scroll automático (correción que no ha funcionado)
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
  - Implementar el Speech-Recognition en el modo offline, es decir, que pueda reconocer las voces de quien esté hablando.
  - Hacer que la app pueda funcionar en modo negro. ✅ Día 18/03/2026
 - En los dos:
   - Que se puedan subir mensajes de voz y la aplicación lo transcriba.
 
  https://chatgpt.com/share/69a86761-fcb0-8004-87d8-ac4730414b8f





  Capítulo 1: Introducción
Diagrama de Gantt
Decir qué ha hecho cada uno

Capítulo 2: 
Hacer la introducción en inglés (futuro)

Capítulo 3:
En un principio nada importante

Capítulo 4:
Diagramas de los casos de uso (como lo que tiene Carlos)

Capítulo 5:
Fotos esquema general de la app
Foto flujo de la app con Vosk
Foto flujo de la app con Whisper
Foto de la línea Braille

Capítulo 6:
Explicar todo
Poner fotos de la aplicación en el móvil
Explicar los distintos listener y cómo se cambia de modelo
Explicar menú de configuración
Fotos del menú de configuración
Pondría la sección 6.5 de la comunicación con el servidor como la última cosa de esa sección, ya que el siguiente capítulo es el del servidor

Capítulo 7:
Poner una foto del JSON donde guardamos la info para enseñar la estructura
Explicar qué es Tavily, cómo funciona y por qué lo usamos
Explicar Caddy y conexión HTTPS

Capítulo 8:
Poner fotos del gasto de batería del Vosk
Poner fotos del gasto de batería del Whisper
Poner foto de lo de RAM en móvil antiguo
Poner fotos de cuánto ocupa la app con ambos tipos de Vosk
Hacer comparativa de todo

Capítulo 9:
Meter una cantidad de cosa aquí horrible



APÉNDICE: MANUAL DE USUARIO

Yo haría una sección donde hacer un manual con las distintas cosas que se pueden hacer
