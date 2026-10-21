"""hdfv 0.24: Simplified Chinese from the Traditional: lang/zh_tw.json to lang/zh_cn.json (the same keys on the same
lines; only the values change) and the handbook's pages handbook/zh_tw to handbook/zh_cn (the text only: keys, item ids,
directives and the header but its title stay). OpenCC's tw2sp (Taiwan phrases to mainland ones), then the mainland
terms of tools/zh_cn_terms.json (gears are 挡, the workshop's machine names...). Run after any change to zh_tw:

    py -3 -m pip install --user opencc-python-reimplemented    (once)
    py -3 tools/zh_cn.py

Other text (READMEs, release pages) can be converted the same way: py -3 tools/zh_cn.py <file in> <file out>.
"""
import json
import os
import re
import sys

import opencc

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'neoforge', 'src', 'main', 'resources', 'assets', 'hdfv')
TERMS = json.load(open(os.path.join(HERE, 'zh_cn_terms.json'), encoding='utf-8'))
TERMS = sorted(((k, v) for k, v in TERMS.items() if k != '_'), key=lambda kv: -len(kv[0]))
CC = opencc.OpenCC('tw2sp')
PROTECTED = re.compile(r'(\{[^{}]*\}|%(?:\d+\$)?[sd]|`[^`]*`|https?://\S+)')


def convert(text):
    """Simplified Chinese of a Traditional text; {directives}, %s, `code` and links are kept as they are."""
    out = []
    for part in PROTECTED.split(text):
        if not part:
            continue
        if PROTECTED.fullmatch(part):
            out.append(part)
            continue
        s = CC.convert(part)
        for a, b in TERMS:
            s = s.replace(a, b)
        out.append(s)
    return ''.join(out)


def write(path, text):
    old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
    if old != text:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
        return 1
    return 0


VALUE = re.compile(r'^(\s*"[^"]+":\s*)"((?:[^"\\]|\\.)*)"(,?)\s*$')


def lang():
    src = os.path.join(ASSETS, 'lang', 'zh_tw.json')
    lines = open(src, encoding='utf-8').read().split('\n')
    out = []
    for line in lines:
        m = VALUE.match(line)
        if not m:
            out.append(line)
            continue
        value = json.loads('"' + m.group(2) + '"')
        out.append(m.group(1) + json.dumps(convert(value), ensure_ascii=False) + m.group(3))
    text = '\n'.join(out)
    tw, cn = json.loads(open(src, encoding='utf-8').read()), json.loads(text)
    if list(tw) != list(cn):
        sys.exit('zh_cn lost or moved a key')
    return write(os.path.join(ASSETS, 'lang', 'zh_cn.json'), text)


def page(text):
    lines = text.split('\n')
    out, header = [], 0
    for line in lines:
        if line.strip() == '---' and header < 2:
            header += 1
            out.append(line)
        elif header == 1:
            out.append('title: ' + convert(line[6:].strip()) if line.startswith('title:') else line)
        else:
            out.append(convert(line))
    return '\n'.join(out)


def handbook():
    src, dst = os.path.join(ASSETS, 'handbook', 'zh_tw'), os.path.join(ASSETS, 'handbook', 'zh_cn')
    written, names = 0, set()
    for name in sorted(os.listdir(src)):
        if not name.endswith('.md'):
            continue
        names.add(name)
        written += write(os.path.join(dst, name), page(open(os.path.join(src, name), encoding='utf-8').read()))
    for name in os.listdir(dst) if os.path.isdir(dst) else []:
        if name not in names:
            os.remove(os.path.join(dst, name))
    return written


def main():
    if len(sys.argv) == 3:
        write(sys.argv[2], convert(open(sys.argv[1], encoding='utf-8').read()))
        return
    n = lang() + handbook()
    print('zh_cn:', n, 'files written')


if __name__ == '__main__':
    main()
