#!/usr/bin/env python3
"""Generates docs/images/usage.svg, the "How to use" picture in the README (Python standard library only).

Usage: python3 tools/docs/make_usage_svg.py docs/images/usage.svg
"""
import sys
PTS = {"Q":10,"U":1,"I":1,"Z":10,"E":1,"N":1,"T":1,"R":1,"A":1,"O":1,"D":2}
N = 9
# premium squares (row, col): DL light blue, TL blue, DW pink, TW orange
PREM = {(0,0):"TW",(0,8):"TW",(8,0):"TW",(8,8):"TW",(1,1):"DW",(1,7):"DW",(7,1):"DW",(7,7):"DW",
        (2,4):"DL",(4,2):"DL",(6,4):"DL",(4,6):"TL",(0,4):"TL",(8,4):"TL",(4,0):"DL",(4,8):"DL"}
PCOL = {"DL":"#BFE3F2","TL":"#4E9BD1","DW":"#F3B6C2","TW":"#F0A35E"}
BOARD = {(3,2):"R",(3,3):"A",(3,4):"T",(3,5):"E",(4,2):"O",(5,2):"D"}
MOVE = {(0,5):"Q",(1,5):"U",(2,5):"I",(4,5):"T"}  # QUIET down through the E of RATE
RACK = list("QUIZENT")
out = []
A = out.append

def tile(x, y, s, ch, fill="#F6E3B4", stroke="#C9A85A", txt="#3B2F1A"):
    A(f'<rect x="{x}" y="{y}" width="{s}" height="{s}" rx="{s*0.14:.1f}" fill="{fill}" stroke="{stroke}" stroke-width="1"/>')
    A(f'<text x="{x+s*0.42:.1f}" y="{y+s*0.72:.1f}" text-anchor="middle" font-size="{s*0.56:.1f}" font-weight="700" fill="{txt}">{ch}</text>')
    A(f'<text x="{x+s*0.92:.1f}" y="{y+s*0.93:.1f}" text-anchor="end" font-size="{s*0.24:.1f}" fill="{txt}">{PTS[ch]}</text>')

def board(x0, y0, c, move=None, labels=True):
    A(f'<rect x="{x0-2}" y="{y0-2}" width="{N*c+4}" height="{N*c+4}" rx="3" fill="#2F3640"/>')
    for r in range(N):
        for k in range(N):
            x, y = x0 + k*c, y0 + r*c
            p = PREM.get((r,k))
            A(f'<rect x="{x+0.5}" y="{y+0.5}" width="{c-1}" height="{c-1}" rx="1.5" fill="{PCOL[p] if p else "#E9ECEF"}"/>')
            if p and labels and (r,k) not in BOARD and not (move and (r,k) in move):
                A(f'<text x="{x+c/2}" y="{y+c*0.64:.1f}" text-anchor="middle" font-size="{c*0.38:.1f}" font-weight="600" fill="#FFFFFF">{p}</text>')
    for (r,k),ch in BOARD.items():
        tile(x0+k*c+0.5, y0+r*c+0.5, c-1, ch)
    if move:
        for (r,k),ch in move.items():
            tile(x0+k*c+0.5, y0+r*c+0.5, c-1, ch, fill="#CFE3FF", stroke="#2F6FDB", txt="#12305E")

W, H = 640, 420
A(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="-apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif">')
A('<title>How to use the word finder: tap the book button above your rack, then pick a move and press Play</title>')
A(f'<rect width="{W}" height="{H}" rx="14" fill="#F4F6F8"/>')

# ---------- step 1: game screen ----------
px, py, pw, ph = 40, 48, 230, 352
A(f'<text x="{px+pw/2}" y="30" text-anchor="middle" font-size="16" font-weight="600" fill="#1F2937">1. Open a game, tap the book</text>')
A(f'<rect x="{px}" y="{py}" width="{pw}" height="{ph}" rx="20" fill="#D0D5DB" stroke="#9CA3AF" stroke-width="2"/>')
# score bar
A(f'<rect x="{px+12}" y="{py+12}" width="{pw-24}" height="30" rx="8" fill="#FFFFFF"/>')
A(f'<text x="{px+22}" y="{py+32}" font-size="12" fill="#111827"><tspan font-weight="700">You</tspan> 203</text>')
A(f'<text x="{px+pw-22}" y="{py+32}" text-anchor="end" font-size="12" fill="#111827">186 <tspan font-weight="700">Anna</tspan></text>')
c = 22
bx = px + (pw - N*c)/2
board(bx, py+54, c)
# book button above the rack
fx, fy = px+pw-34, py+ph-74
A(f'<circle cx="{fx}" cy="{fy}" r="21" fill="none" stroke="#2563EB" stroke-width="2.5" stroke-dasharray="4 3"/>')
A(f'<circle cx="{fx}" cy="{fy}" r="15" fill="#1B5E9E"/>')
A(f'<path d="M{fx-8} {fy-5}c3-1.5 6-1.5 8 .5v10c-2-2-5-2-8-.5zM{fx+8} {fy-5}c-3-1.5-6-1.5-8 .5v10c2-2 5-2 8-.5z" fill="#FFFFFF"/>')
A(f'<text x="{fx-28}" y="{fy+5}" text-anchor="end" font-size="13" font-weight="700" fill="#2563EB">tap</text>')
# rack
rs = 27; rx0 = px + (pw - 7*rs - 6*3)/2; ry = py+ph-44
for i,ch in enumerate(RACK):
    tile(rx0 + i*(rs+3), ry, rs, ch)

# arrow
A('<path d="M286 224h40" stroke="#6B7280" stroke-width="2.5" fill="none"/><path d="M326 217l11 7-11 7z" fill="#6B7280"/>')

# ---------- step 2: overlay ----------
ox, oy, ow, oh = 352, 48, 248, 352
A(f'<text x="{ox+ow/2}" y="30" text-anchor="middle" font-size="16" font-weight="600" fill="#1F2937">2. Pick a move, press Play</text>')
A(f'<rect x="{ox}" y="{oy}" width="{ow}" height="{oh}" rx="20" fill="#FFFFFF" stroke="#9CA3AF" stroke-width="2"/>')
# mini rack
ms = 17; mx0 = ox + (ow - 7*ms - 6*2)/2
used = list(MOVE.values())
for i,ch in enumerate(RACK):
    if ch in used:
        used.remove(ch)
        tile(mx0 + i*(ms+2), oy+12, ms, ch, fill="#ECEEF1", stroke="#C7CBD1", txt="#9AA1AB")
    else:
        tile(mx0 + i*(ms+2), oy+12, ms, ch)
# preview board with the chosen move
c2 = 14
board(ox + (ow - N*c2)/2, oy+38, c2, move=MOVE, labels=False)
# sort chips
cy = oy + 38 + N*c2 + 10
chips = [("Score", 48, True), ("Length", 52, False), ("A-Z", 38, False)]
x = ox + 16
for label, w, on in chips:
    A(f'<rect x="{x}" y="{cy}" width="{w}" height="20" rx="10" fill="{"#1B5E9E" if on else "#E5E7EB"}"/>')
    A(f'<text x="{x+w/2}" y="{cy+14}" text-anchor="middle" font-size="11" fill="{"#FFFFFF" if on else "#374151"}">{label}</text>')
    x += w + 6
# move list
moves = [("QUIET", 31), ("QUINT", 28), ("ZINE", 24), ("TUNE", 9)]
ly = cy + 28; rh = 25
for i,(w,s) in enumerate(moves):
    y = ly + i*rh
    if i == 0:
        A(f'<rect x="{ox+12}" y="{y}" width="{ow-24}" height="{rh-2}" rx="6" fill="#DBEAFE" stroke="#3B82F6"/>')
    elif i > 0:
        A(f'<path d="M{ox+12} {y-1}h{ow-24}" stroke="#E5E7EB"/>')
    fw = ' font-weight="700"' if i == 0 else ''
    A(f'<text x="{ox+24}" y="{y+16}" font-size="13" fill="#111827"{fw}>{w}</text>')
    A(f'<text x="{ox+ow-24}" y="{y+16}" text-anchor="end" font-size="13" fill="#111827"{fw}>{s}</text>')
by = oy + oh - 42
A(f'<rect x="{ox+ow/2-48}" y="{by}" width="96" height="30" rx="15" fill="#16A34A"/>')
A(f'<text x="{ox+ow/2}" y="{by+20}" text-anchor="middle" font-size="14" font-weight="700" fill="#FFFFFF">Play</text>')
A('</svg>')
open(sys.argv[1], "w").write("\n".join(out) + "\n")
