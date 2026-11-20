# -*- coding: utf-8 -*-
"""收集 App 会渲染到的所有字符，供 pyftsubset 切子集用（构建时自动跑）。"""
import json, glob, os, sys

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
chars = set()

corpus = os.environ.get('CLOCK_CORPUS') or os.path.join(APP, 'assets', 'clock.json')
data = json.load(open(corpus, encoding='utf-8'))
if isinstance(data, dict):
    data = data.get('entries') or []
for e in data:
    for k in ('s', 'b', 'h'):
        chars |= set(e.get(k) or '')

# 界面文字：Java 源码与资源
for pat in ('src/**/*.java', 'res/**/*.xml'):
    for p in glob.glob(os.path.join(APP, pat), recursive=True):
        t = open(p, encoding='utf-8', errors='ignore').read()
        chars |= {c for c in t if ord(c) > 0x2000}

chars |= set(chr(c) for c in range(0x20, 0x7f))
chars |= set('　、。，；：？！「」『』（）【】《》〈〉—…·～％＋－×÷＝'
             '０１２３４５６７８９〇“”‘’℃')
chars = {c for c in chars if c not in '\n\r\t'}

sys.stdout.write(''.join(sorted(chars)))
