"""
Builds the offline Mumbai road graph used by the routing engine.

Input : seg.parquet (Overture Maps transportation segments, which are OpenStreetMap roads)
        places.parquet (Overture places, used only for hospital *locations*)
Output: backend/src/main/resources/data/mumbai-roads.bin.gz
        backend/src/main/resources/data/hospital-sites.csv

Run tools/fetch_overture.py first to download the two parquet files.

Graph model
  node = an Overture "connector" (a junction or road end)
  edge = the piece of a road between two consecutive connectors, with its shape points
  All roads are treated as two-way (simplification; documented in the README).

Binary format (big-endian, gzip-compressed), read by RoadGraphLoader.java:
  int magic 0x47484D52 ("GHMR"), int version 1
  int nameCount, then nameCount UTF strings
  int nodeCount, then per node: int latE6, int lonE6
  int edgeCount, then per edge: int from, int to, byte roadClass, int nameIdx(-1 = none),
                                 float lengthMeters, short shapeCount, shapeCount x (int latE6, int lonE6)
  int signalCount, then signalCount node ids
"""
import gzip, math, struct, sys, csv, collections
import pyarrow.parquet as pq
from shapely import from_wkb

SEG, PLACES, OUT_DIR = sys.argv[1], sys.argv[2], sys.argv[3]

# Road classes we drive on, mapped to the RoadClass enum ordinal in Java.
CLASSES = {"motorway": 0, "trunk": 1, "primary": 2, "secondary": 3,
           "tertiary": 4, "unclassified": 5, "residential": 6}
MAJOR = {"trunk", "primary", "secondary"}

def hav(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))

t = pq.read_table(SEG, columns=["id", "geometry", "subtype", "class", "connectors", "names", "road_flags"]).to_pylist()

node_ids, node_xy = {}, []
def node(cid, lon, lat):
    if cid not in node_ids:
        node_ids[cid] = len(node_xy)
        node_xy.append((lat, lon))
    return node_ids[cid]

names, name_idx = [], {}
edges = []          # (u, v, cls, nameIdx, length, shape[(lat,lon)])
cls_at_node = collections.defaultdict(set)
for r in t:
    if r["subtype"] != "road" or r["class"] not in CLASSES:
        continue
    flags = {v for f in (r["road_flags"] or []) for v in f["values"]}
    if "is_under_construction" in flags:
        continue
    coords = list(from_wkb(r["geometry"]).coords)  # (lon, lat)
    if len(coords) < 2 or not r["connectors"]:
        continue
    # cumulative length fraction of each vertex, to place connectors on vertices
    cum = [0.0]
    for (x1, y1), (x2, y2) in zip(coords, coords[1:]):
        cum.append(cum[-1] + hav(y1, x1, y2, x2))
    total = cum[-1] or 1.0
    frac = [c / total for c in cum]
    stops = []
    for c in sorted(r["connectors"], key=lambda c: c["at"]):
        vi = min(range(len(frac)), key=lambda i: abs(frac[i] - c["at"]))
        stops.append((vi, c["connector_id"]))
    nm = (r["names"] or {}).get("primary") if r["names"] else None
    ni = -1
    if nm:
        ni = name_idx.setdefault(nm, len(names))
        if ni == len(names):
            names.append(nm)
    cls = CLASSES[r["class"]]
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if b <= a:
            continue
        u = node(ca, *coords[a]); v = node(cb, *coords[b])
        if u == v:
            continue
        shape = [(lat, lon) for lon, lat in coords[a:b + 1]]
        length = cum[b] - cum[a]
        edges.append((u, v, cls, ni, length, shape))
        cls_at_node[u].add(r["class"]); cls_at_node[v].add(r["class"])

# keep the largest connected component so every point is reachable
adj = collections.defaultdict(list)
for i, (u, v, *_ ) in enumerate(edges):
    adj[u].append(v); adj[v].append(u)
seen, best = set(), []
for s in range(len(node_xy)):
    if s in seen or s not in adj:
        continue
    comp, stack = [], [s]
    seen.add(s)
    while stack:
        x = stack.pop(); comp.append(x)
        for y in adj[x]:
            if y not in seen:
                seen.add(y); stack.append(y)
    if len(comp) > len(best):
        best = comp
keep = set(best)
remap = {old: new for new, old in enumerate(sorted(keep))}
nodes = [node_xy[o] for o in sorted(keep)]
edges = [(remap[u], remap[v], c, n, l, s) for (u, v, c, n, l, s) in edges if u in keep and v in keep]

# Approximate traffic signals: junctions where 3+ roads meet and 2+ of them are major roads.
deg = collections.Counter()
for u, v, *_ in edges:
    deg[u] += 1; deg[v] += 1
major_cnt = collections.Counter()
for u, v, c, *_ in edges:
    if c in (1, 2, 3):
        major_cnt[u] += 1; major_cnt[v] += 1
signals = []
for n in range(len(nodes)):
    if deg[n] >= 3 and major_cnt[n] >= 2:
        lat, lon = nodes[n]
        if all(hav(lat, lon, *nodes[s]) > 120 for s in signals[-200:]):
            signals.append(n)

E6 = 1_000_000
with gzip.open(f"{OUT_DIR}/mumbai-roads.bin.gz", "wb", compresslevel=9) as f:
    w = lambda fmt, *v: f.write(struct.pack(">" + fmt, *v))
    w("ii", 0x47484D52, 1)
    w("i", len(names))
    for nm in names:
        b = nm.encode("utf-8")[:65000]; w("H", len(b)); f.write(b)
    w("i", len(nodes))
    for lat, lon in nodes:
        w("ii", round(lat * E6), round(lon * E6))
    w("i", len(edges))
    for u, v, c, n, l, s in edges:
        w("iibif", u, v, c, n, l)
        w("h", len(s))
        for lat, lon in s:
            w("ii", round(lat * E6), round(lon * E6))
    w("i", len(signals))
    for s in signals:
        w("i", s)
print("nodes", len(nodes), "edges", len(edges), "names", len(names), "signals", len(signals))

# ---------- hospital sites (real locations, generic names, simulated capacity) ----------
LOCALITIES = [("Colaba",18.9067,72.8147),("Fort",18.9339,72.8355),("Marine Lines",18.9447,72.8236),
 ("Girgaon",18.9550,72.8150),("Grant Road",18.9635,72.8160),("Byculla",18.9790,72.8330),("Mazgaon",18.9650,72.8440),
 ("Tardeo",18.9700,72.8130),("Mahalaxmi",18.9820,72.8240),("Worli",19.0000,72.8150),("Lower Parel",18.9960,72.8300),
 ("Parel",19.0030,72.8410),("Sewri",19.0000,72.8560),("Dadar",19.0190,72.8430),("Prabhadevi",19.0160,72.8290),
 ("Wadala",19.0170,72.8650),("Matunga",19.0270,72.8550),("Mahim",19.0410,72.8400),("Sion",19.0430,72.8620),
 ("Dharavi",19.0400,72.8550),("Bandra West",19.0600,72.8360),("Bandra East",19.0600,72.8500),("BKC",19.0660,72.8680),
 ("Kurla",19.0720,72.8830),("Chembur",19.0620,72.9000),("Khar",19.0720,72.8370),("Santacruz",19.0820,72.8400),
 ("Kalina",19.0750,72.8600),("Vile Parle",19.1000,72.8450),("Ghatkopar",19.0860,72.9080),("Andheri West",19.1360,72.8300),
 ("Andheri East",19.1150,72.8650),("Saki Naka",19.1030,72.8880),("Powai",19.1180,72.9050),("Vikhroli",19.1110,72.9280),
 ("Jogeshwari",19.1400,72.8550),("Goregaon",19.1630,72.8500),("Malad",19.1860,72.8480),("Bhandup",19.1450,72.9380),
 ("Mulund",19.1720,72.9560),("Juhu",19.1000,72.8270),("Versova",19.1310,72.8150),("Kanjurmarg",19.1290,72.9300),
 ("Govandi",19.0550,72.9150),("Mankhurd",19.0480,72.9300),("Kandivali",19.2050,72.8510)]
SKIP = ("eye", "dental", "ivf", "fertility", "ayur", "clinic", "nursing", "skin", "physio", "homoeo", "homeo",
        "ent ", "vet", "pet", "ortho", "maternity", "women", "child", "kid", "cancer", "gastro", "ambulance", "spectra")
P = pq.read_table(PLACES, columns=["geometry", "names", "confidence", "basic_category"]).to_pylist()
cands = []
for r in P:
    nm = ((r["names"] or {}).get("primary") or "").lower()
    if r["basic_category"] != "hospital" or (r["confidence"] or 0) < 0.9 or "hospital" not in nm:
        continue
    if any(s in nm for s in SKIP):
        continue
    pt = from_wkb(r["geometry"]); cands.append((r["confidence"], pt.y, pt.x))
cands.sort(reverse=True)
lat_min = min(n[0] for n in nodes); lat_max = max(n[0] for n in nodes)
lon_min = min(n[1] for n in nodes); lon_max = max(n[1] for n in nodes)
picked = []
for conf, lat, lon in cands:
    if not (lat_min < lat < lat_max and lon_min < lon < lon_max) or lon > 72.975:
        continue
    if all(hav(lat, lon, p[0], p[1]) > 1800 for p in picked):
        picked.append((lat, lon))
    if len(picked) >= 32:
        break
used = collections.Counter()
KINDS = ["Multispeciality Hospital", "General Hospital", "Heart & Trauma Centre", "Municipal Hospital", "Medical College Hospital"]
with open(f"{OUT_DIR}/hospital-sites.csv", "w", newline="") as f:
    wr = csv.writer(f); wr.writerow(["name", "lat", "lon"])
    for i, (lat, lon) in enumerate(picked):
        loc = min(LOCALITIES, key=lambda l: hav(lat, lon, l[1], l[2]))[0]
        used[loc] += 1
        kind = KINDS[i % len(KINDS)]
        name = f"{loc} {kind}" if used[loc] == 1 else f"{loc} {kind} {used[loc]}"
        wr.writerow([name, f"{lat:.6f}", f"{lon:.6f}"])
print("hospitals", len(picked))
