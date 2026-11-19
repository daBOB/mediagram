# The walnut's jaw from its voice: RMS loudness per 20 fps frame, normalised to the 95th percentile, lightly smoothed.
# usage: python make-jaw-track.py line.wav jaw-N.json   (the mockup reads it with ?voice=N)
import json, sys, wave
import numpy as np
w = wave.open(sys.argv[1]); x = np.frombuffer(w.readframes(w.getnframes()), np.int16) / 32768.0
hop = w.getframerate() // 20
rms = np.array([np.sqrt(np.mean(x[k:k + hop] ** 2)) for k in range(0, len(x), hop)])
env = np.convolve(np.clip(rms / np.percentile(rms, 95), 0, 1), [0.25, 0.5, 0.25], mode="same")
json.dump([round(float(e), 3) for e in env], open(sys.argv[2], "w"))
