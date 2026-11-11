---
title: Card game prompts must not reveal who holds a playable card
author: Andre
date: 2026-11-08
standing: true
id: l-8afaf6b0
scope: repo
status: active
src: init-sessions
---

## Decided
- A review found the TV gave away who could play, because only players with matching cards were prompted. The user chose to show the same prompt to everyone, with the button greyed out when nothing fits.
- In the code: In Durak, ask every player who still holds cards "Dazulegen?", greying the button when nothing fits. In Mau-Mau always offer "Spielen" or "Behalten" after a draw. Ties in Schwimmen cost nobody a life.

_From past Claude sessions here, Nov 7._
