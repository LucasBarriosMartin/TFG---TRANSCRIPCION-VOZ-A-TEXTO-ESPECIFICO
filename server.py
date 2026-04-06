from fastapi import FastAPI, UploadFile, File
import io
import os
import torch
import torchaudio
import whisper
import numpy as np
from speechbrain.inference.speaker import SpeakerRecognition
from sklearn.metrics.pairwise import cosine_similarity
import json
from pydub import AudioSegment
import threading
import time

ARCHIVO_DB = "bd.json"
UMBRAL = 0.2
NUM_MAX_EMBEDDINGS = 30
SAMPLE_RATE_OBJETIVO = 16000

print("Cargando SpeechBrain...")
verificador = SpeakerRecognition.from_hparams(
    source="speechbrain/spkrec-ecapa-voxceleb",
    savedir="modelo_voces"
)

print("Cargando Whisper...")
modelo_whisper = whisper.load_model("medium")
print("Modelos listos.")

_db_lock = threading.Lock()

def _cargar_db_inicial() -> dict:
    if os.path.exists(ARCHIVO_DB):
        with open(ARCHIVO_DB, "r", encoding="utf-8") as f:
            raw = json.load(f)
        return {
            nombre: [np.array(e, dtype=np.float32) for e in embeddings]
            for nombre, embeddings in raw.items()
        }
    return {}

db_memoria: dict = _cargar_db_inicial()

def _guardar_db():
    with open(ARCHIVO_DB, "w", encoding="utf-8") as f:
        json.dump(
            {nombre: [e.tolist() for e in embeddings]
             for nombre, embeddings in db_memoria.items()},
            f, indent=4, ensure_ascii=False
        )

app = FastAPI(title="Servidor IA")

def cargar_audio_desde_bytes(audio_bytes: bytes) -> torch.Tensor:
    segmento = AudioSegment.from_file(io.BytesIO(audio_bytes))
    segmento = segmento.set_frame_rate(SAMPLE_RATE_OBJETIVO).set_channels(1).set_sample_width(2)
    muestras = np.frombuffer(segmento.raw_data, dtype=np.int16).astype(np.float32) / 32768.0
    return torch.tensor(muestras).unsqueeze(0)

def obtener_embedding(senial: torch.Tensor) -> np.ndarray:
    embedding = verificador.encode_batch(senial).squeeze()
    embedding = torch.nn.functional.normalize(embedding, dim=0)
    return embedding.detach().cpu().numpy().astype(np.float32)

def identificar_o_registrar(senial: torch.Tensor) -> str:
    global db_memoria
    embedding_nuevo = obtener_embedding(senial)

    # Si la IA no pudo extraer una huella valida (array vacio o corrupto)
    if embedding_nuevo is None or embedding_nuevo.size == 0 or np.isnan(embedding_nuevo).any():
        print("Audio sin voz valida o embedding vacio. Descartando y enviando a 'Vacio'.")
        return "Vacio"

    with _db_lock:
        mejor_similitud = -1.0 # Empezamos por debajo de 0
        mejor_persona = None

        # Buscar el mejor match
        similitudes_debug = {}

        for nombre, lista_embeddings in db_memoria.items():
            # Evitamos leer categorías que se hayan quedado vacias en el JSON
            if not lista_embeddings:
                continue

            matriz = np.stack(lista_embeddings)
            sims = matriz @ embedding_nuevo
            sim_max = float(sims.max())
        
            similidades_persona = sims.tolist()
            similitudes_debug[nombre] = {
                "max": round(sim_max, 3),
                "todas": [round(s, 3) for s in similidades_persona]
            }
        
            if sim_max > mejor_similitud:
                mejor_similitud = sim_max
                mejor_persona = nombre
        
        # PRINT BONITO PARA DEBUG
        print("\n--- SIMILITUDES ---")
        for nombre, datos in similitudes_debug.items():
            print(f"{nombre}: max={datos['max']} | embeddings={datos['todas']}")
        print("-------------------\n")

        # Reconocido (supera el umbral)
        if mejor_similitud >= UMBRAL and mejor_persona is not None:
            print(f"Reconocido: {mejor_persona} (sim={mejor_similitud:.3f})")

            # opcional: seguir refinando embeddings
            if mejor_similitud < 0.95:
                db_memoria[mejor_persona].append(embedding_nuevo)

                if len(db_memoria[mejor_persona]) > NUM_MAX_EMBEDDINGS:
                    db_memoria[mejor_persona] = db_memoria[mejor_persona][-NUM_MAX_EMBEDDINGS:]

                _guardar_db()

            return mejor_persona

        # No reconocido -> Va al saco de "Desconocido"
        if "Desconocido" not in db_memoria:
            db_memoria["Desconocido"] = []

        db_memoria["Desconocido"].append(embedding_nuevo)

        # limitar tamanio
        if len(db_memoria["Desconocido"]) > NUM_MAX_EMBEDDINGS:
            db_memoria["Desconocido"] = db_memoria["Desconocido"][-NUM_MAX_EMBEDDINGS:]

        _guardar_db()

        print(f"No reconocido -> guardado en Desconocido (sim={mejor_similitud:.3f})")

        return "Desconocido"

def transcribir_desde_tensor(senial: torch.Tensor) -> str:
    audio_numpy = senial.squeeze().cpu().numpy()
    
    # condition_on_previous_text=False hace que no se invente tanto
    resultado = modelo_whisper.transcribe(audio_numpy, language="es", condition_on_previous_text=False)
    
    # Extraemos los segmentos de la transcripción
    segmentos = resultado.get("segments", [])
    
    if not segmentos:
        return "[Ruido]"

    # Revisamos si Whisper esta seguro de que NO hay voz
    # Si la probabilidad es mayor al 60% (0.6), asumimos que es una alucinacion por ruido
    for segmento in segmentos:
        probabilidad_sin_voz = segmento.get("no_speech_prob", 0.0)
        if probabilidad_sin_voz > 0.6:
            print(f"Descartado por Whisper. Probabilidad de que sea solo ruido: {probabilidad_sin_voz:.2f}")
            return "[Ruido]"
            
    texto = resultado["text"].strip()
    
    # Si el texto es muy corto (ej. "a", ".", " "), tambien lo descartamos
    if len(texto) < 2:
        return "[Ruido]"
        
    return texto

@app.get("/api/ping")
def ping():
    return {"message": "Servidor vivo"}

@app.post("/api/transcribir")
async def transcribir(archivo: UploadFile = File(...)):
    print(f"Recibiendo: {archivo.filename}")
    inicio = time.time()
    audio_bytes = await archivo.read()
    senial = cargar_audio_desde_bytes(audio_bytes)
    persona = identificar_o_registrar(senial)
    texto = transcribir_desde_tensor(senial)
    print(f"[{persona}]: {texto.strip()}")
    fin = time.time()
    print(f"Tiempo: {fin - inicio:.3f} segundos")
    return {"speaker": persona, "text": texto.strip()}
    
