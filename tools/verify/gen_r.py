#!/usr/bin/env python3
"""Writes a stand-in `com.femi.gallery.R` (and `MainActivity`) from the app's real resources.

Usage: gen_r.py <app/src/main/res> <output.kt>

Every <string>, <plurals> and <string-array> name in values/strings.xml, and every file name in drawable*/,
mipmap*/, layout*/ and raw/, becomes a constant, so a screen that names a resource that does not exist
fails the type check, and a new string needs no manual edit of a stub.
"""
import os
import re
import sys


def names_in_values(res):
    path = os.path.join(res, "values", "strings.xml")
    text = open(path, encoding="utf8").read()
    strings = set(re.findall(r'<string\s+name="([^"]+)"', text)) | set(re.findall(r'<string-array\s+name="([^"]+)"', text))
    plurals = set(re.findall(r'<plurals\s+name="([^"]+)"', text))
    return strings, plurals


def names_in_dirs(res, prefix):
    found = set()
    for d in os.listdir(res):
        if d == prefix or d.startswith(prefix + "-"):
            for f in os.listdir(os.path.join(res, d)):
                found.add(os.path.splitext(f)[0])
    return found


def ident(name):
    return name.replace(".", "_")


def main():
    res, out = sys.argv[1], sys.argv[2]
    strings, plurals = names_in_values(res)
    groups = {
        "string": strings,
        "plurals": plurals,
        "drawable": names_in_dirs(res, "drawable"),
        "mipmap": names_in_dirs(res, "mipmap"),
        "layout": names_in_dirs(res, "layout"),
        "raw": names_in_dirs(res, "raw"),
    }
    lines = ['@file:Suppress("unused")', "package com.femi.gallery", "class MainActivity", "class R {"]
    n = 1
    for group, names in groups.items():
        lines.append(f"    class {group} {{ companion object {{")
        for name in sorted(names):
            lines.append(f"        const val {ident(name)} = {n}")
            n += 1
        lines.append("    } }")
    lines.append("}")
    open(out, "w", encoding="utf8").write("\n".join(lines) + "\n")


main()
