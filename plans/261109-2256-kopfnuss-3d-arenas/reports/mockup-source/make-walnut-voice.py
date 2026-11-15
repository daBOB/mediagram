# Voices the Kopfnuss walnut's lines with Gemini TTS, voice Puck (chosen 2026-11-15), one WAV per line, silence trimmed.
# usage: GEMINI_API_KEY=... python make-walnut-voice.py <out-dir> "line 1" "line 2" ...
# The style goes in a short "Say ... :" lead; a longer instruction gets read aloud as part of the line.
import subprocess, sys, wave
from google import genai
from google.genai import types

out, lines = sys.argv[1], sys.argv[2:]
client = genai.Client()
cfg = types.GenerateContentConfig(response_modalities=["AUDIO"], speech_config=types.SpeechConfig(
    voice_config=types.VoiceConfig(prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name="Puck"))))
for i, line in enumerate(lines):
    r = client.models.generate_content(model="gemini-3.8-flash-tts", config=cfg,
        contents=f"Say in German like a cheeky, fast-talking TV quiz show host, cheerful and quick: {line}")
    raw = f"{out}/line-{i:02d}-raw.wav"
    with wave.open(raw, "wb") as w:  # 24 kHz, 16-bit mono PCM
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(24000)
        w.writeframes(r.candidates[0].content.parts[0].inline_data.data)
    trim = "silenceremove=start_periods=1:start_threshold=-45dB,areverse," * 2
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", raw, "-af", trim.rstrip(","), f"{out}/line-{i:02d}.wav"], check=True)
    print(f"{out}/line-{i:02d}.wav")
