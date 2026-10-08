"""
Download the raw map data used by build_road_graph.py (only needed if you want to rebuild the graph;
the built graph is already committed under backend/src/main/resources/data).

Source: Overture Maps Foundation open data (roads are OpenStreetMap, ODbL). Files are public GeoParquet on
S3; we read only the row groups whose bounding boxes overlap Mumbai, using HTTP range requests, so the
download is a few hundred MB instead of the whole planet.

    pip install pyarrow requests
    python tools/fetch_overture.py data-raw
    python tools/build_road_graph.py data-raw/seg.parquet data-raw/places.parquet backend/src/main/resources/data
"""
import io
import os
import re
import sys

import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.parquet as pq
import requests

BASE = "https://overturemaps-us-west-2.s3.amazonaws.com/"
RELEASE = "2026-09-23.1"
# west, south, east, north: Mumbai island city + western and eastern suburbs
BBOX = (72.79, 18.88, 72.99, 19.20)


class HttpFile(io.RawIOBase):
    """Seekable file over HTTP range requests, so pyarrow can read just the parts it needs."""

    def __init__(self, url, size):
        self.url, self.size, self.pos, self.sess = url, size, 0, requests.Session()

    def seekable(self):
        return True

    def readable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, offset, whence=0):
        self.pos = offset if whence == 0 else (self.pos + offset if whence == 1 else self.size + offset)
        return self.pos

    def readinto(self, b):
        n = min(len(b), self.size - self.pos)
        if n <= 0:
            return 0
        r = self.sess.get(self.url, headers={"Range": f"bytes={self.pos}-{self.pos + n - 1}"}, timeout=120)
        data = r.content
        b[:len(data)] = data
        self.pos += len(data)
        return len(data)


def overlapping_row_groups(theme, typ):
    """Use the per-row-group bbox statistics to skip everything outside Mumbai."""
    west, south, east, north = BBOX
    listing = requests.get(BASE + f"?list-type=2&prefix=release/{RELEASE}/theme={theme}/type={typ}/").text
    for key, size in re.findall(r"<Key>([^<]+)</Key>.*?<Size>(\d+)</Size>", listing):
        f = pq.ParquetFile(HttpFile(BASE + key, int(size)))
        md = f.metadata
        idx = {md.schema.column(i).path: i for i in range(md.num_columns)
               if md.schema.column(i).path.startswith("bbox.")}
        groups = []
        for g in range(md.num_row_groups):
            rg = md.row_group(g)
            stat = lambda name: rg.column(idx[name]).statistics
            if (stat("bbox.xmax").min > east or stat("bbox.xmin").max < west
                    or stat("bbox.ymax").min > north or stat("bbox.ymin").max < south):
                continue
            groups.append(g)
        if groups:
            yield key, int(size), groups


def download(theme, typ, columns, out_path):
    west, south, east, north = BBOX
    tables = []
    for key, size, groups in overlapping_row_groups(theme, typ):
        print(f"  {key.split('/')[-1]}: {len(groups)} row groups", file=sys.stderr)
        t = pq.ParquetFile(HttpFile(BASE + key, size)).read_row_groups(groups, columns=columns + ["bbox"])
        b = t.column("bbox").combine_chunks()
        inside = pc.and_(
            pc.and_(pc.less_equal(b.field("xmin"), east), pc.greater_equal(b.field("xmax"), west)),
            pc.and_(pc.less_equal(b.field("ymin"), north), pc.greater_equal(b.field("ymax"), south)))
        tables.append(t.filter(inside))
    table = pa.concat_tables(tables)
    pq.write_table(table, out_path)
    print(f"{out_path}: {table.num_rows} rows")


if __name__ == "__main__":
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "data-raw"
    os.makedirs(out_dir, exist_ok=True)
    download("transportation", "segment",
             ["id", "geometry", "class", "subtype", "connectors", "names", "road_flags", "speed_limits",
              "access_restrictions"], os.path.join(out_dir, "seg.parquet"))
    download("places", "place", ["id", "geometry", "names", "confidence", "basic_category"],
             os.path.join(out_dir, "places.parquet"))
