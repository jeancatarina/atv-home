"""Rebuilds the big content packs: what is hand-written in the pack stays, the generators add to it.
   python3 tools/content/build_all.py   (idempotent: running it twice changes nothing)"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_mostlikely, gen_frequency, gen_couple, gen_twotruths, gen_words, gen_psych, gen_spyfall, gen_tribunal, gen_missions
ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'assets', 'social', 'content')

def merge(name, new, key=lambda x: x.lower()):
    p = os.path.join(ROOT, name + '.json')
    old = json.load(open(p)) if os.path.exists(p) else []
    seen, out = set(), []
    for x in old + new:
        k = key(x)
        if k in seen: continue
        seen.add(k); out.append(x)
    json.dump(out, open(p, 'w'), ensure_ascii=False, indent=0)
    print(f"{name:12s} {len(old):5d} -> {len(out):5d}")
    return out

merge('mostlikely', gen_mostlikely.build())
merge('frequency', gen_frequency.build())
merge('couple', gen_couple.build())
merge('twotruths', gen_twotruths.build())
merge('insider', gen_words.build_words(), key=lambda x: x.lower())
merge('undercover', gen_words.build_pairs(), key=lambda x: "|".join(sorted(p.lower() for p in x.split("|"))))
merge('psychologist', gen_psych.build(), key=lambda x: x.split("|")[0].lower())
merge('spyfall', gen_spyfall.build(), key=lambda x: x.split("|")[0].lower())
merge('tribunal', gen_tribunal.build(), key=lambda x: x.split("|")[0].lower())
m = merge('missions', gen_missions.build(), key=lambda x: x.split("|")[0].lower())
# One player's view must never contain another player's mission text, so no mission may sit inside another:
# when one does, the shorter hand-written one goes.
texts = [x.split("|")[0] for x in m]
keep = [x for x in m if not any(x.split("|")[0] != u and x.split("|")[0] in u for u in texts)]
json.dump(keep, open(os.path.join(ROOT, 'missions.json'), 'w'), ensure_ascii=False, indent=0)
print(f"missions     {len(m)} -> {len(keep)} after removing nested texts")
