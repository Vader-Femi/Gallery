#!/usr/bin/env python3
"""Builds the offline map data in app/src/main/assets/map from Natural Earth (public domain).

    python3 tools/build_map_data.py [directory with the downloaded .geojson files]

Without an argument the files are downloaded from the natural-earth-vector repository on GitHub.
The output formats are read by MapDataCodec (domain/geo/MapDataCodec.kt); keep the two in sync.

Polylines (land.bin, borders.bin), all numbers big-endian:
    int32 magic 0x47524E47 ("GRNG"), int32 version 1, int32 count, then per polyline:
    int32 point count, int32 first longitude, int32 first latitude (both in 1/10000 degree),
    then for every further point the change in longitude and in latitude as zigzag varints.
Places (places.bin):
    int32 magic 0x47504C43 ("GPLC"), int32 version 1, int32 count, then per place:
    int32 latitude, int32 longitude (1/10000 degree), int32 population in thousands,
    uint16 name length in bytes, the name in UTF-8.
"""
import json
import struct
import sys
import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
SOURCES = {
    "land": "ne_50m_land.geojson",
    "borders": "ne_50m_admin_0_boundary_lines_land.geojson",
    "places": "ne_50m_populated_places_simple.geojson",
}
SCALE = 10_000
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/map"


def load(name, source_dir):
    if source_dir:
        return json.loads((Path(source_dir) / name).read_text(encoding="utf-8"))
    with urllib.request.urlopen(BASE + name) as response:
        return json.loads(response.read().decode("utf-8"))


def quantise(points):
    out = []
    for lon, lat in points:
        q = (round(lon * SCALE), round(lat * SCALE))
        if not out or out[-1] != q:
            out.append(q)
    return out


def varint(value):
    value = (value << 1) ^ (value >> 63)  # zigzag
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def encode_polylines(lines):
    body = bytearray()
    for line in lines:
        body += struct.pack(">iii", len(line), line[0][0], line[0][1])
        for (lon0, lat0), (lon1, lat1) in zip(line, line[1:]):
            body += varint(lon1 - lon0) + varint(lat1 - lat0)
    return struct.pack(">iii", 0x47524E47, 1, len(lines)) + bytes(body)


def rings(geometry):
    kind, coordinates = geometry["type"], geometry["coordinates"]
    if kind == "Polygon":
        polygons = [coordinates]
    elif kind == "MultiPolygon":
        polygons = coordinates
    else:
        return []
    result = []
    for polygon in polygons:
        for ring in polygon:
            points = quantise(ring)
            if len(points) > 1 and points[0] == points[-1]:
                points.pop()  # rings are closed implicitly
            if len(points) >= 3:
                result.append(points)
    return result


def lines(geometry):
    kind, coordinates = geometry["type"], geometry["coordinates"]
    parts = [coordinates] if kind == "LineString" else coordinates if kind == "MultiLineString" else []
    result = []
    for part in parts:
        points = quantise(part)
        if len(points) >= 2:
            result.append(points)
    return result


def encode_places(features):
    places = []
    for feature in features:
        p = feature["properties"]
        name = p["name"].encode("utf-8")
        if not name or len(name) > 255:
            continue
        places.append((round(p["latitude"] * SCALE), round(p["longitude"] * SCALE),
                       min(int(p["pop_max"] or 0) // 1000, 2**31 - 1), name))
    body = bytearray()
    for lat, lon, pop, name in places:
        body += struct.pack(">iiiH", lat, lon, pop, len(name)) + name
    return struct.pack(">iii", 0x47504C43, 1, len(places)) + bytes(body)


def main():
    source_dir = sys.argv[1] if len(sys.argv) > 1 else None
    OUT.mkdir(parents=True, exist_ok=True)
    land = [r for f in load(SOURCES["land"], source_dir)["features"] for r in rings(f["geometry"])]
    borders = [l for f in load(SOURCES["borders"], source_dir)["features"] for l in lines(f["geometry"])]
    places = load(SOURCES["places"], source_dir)["features"]
    outputs = {
        "land.bin": encode_polylines(land),
        "borders.bin": encode_polylines(borders),
        "places.bin": encode_places(places),
    }
    for name, data in outputs.items():
        (OUT / name).write_bytes(data)
        print(f"{name}: {len(data) / 1024:.0f} KB")
    print(f"{len(land)} land rings, {len(borders)} border lines, {len(places)} places")


if __name__ == "__main__":
    main()
