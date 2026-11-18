#!/usr/bin/env python3
"""Read-only comparison of calculator version-6/7 exports (directories or .bin files)."""
import argparse
from collections import Counter
import gzip
import json
from pathlib import Path
import struct


class Pack:
    def __init__(self, path):
        path = Path(path)
        self.path = path / 'data.bin' if path.is_dir() else path
        with gzip.open(self.path, 'rb') as source:
            self.data = source.read(512 * 1024 * 1024 + 1)
        if len(self.data) > 512 * 1024 * 1024 or len(self.data) < 32 or len(self.data) % 4:
            raise ValueError('Invalid or oversized word buffer')
        self.words = memoryview(self.data).cast('B')
        self.version = self.word(0)
        if self.version not in (6, 7):
            raise ValueError(f'Unsupported version: {self.version}')
        self.goods = {}
        self.goods_by_pointer = {}
        self.groups = {}
        self.methods = {}
        self.recipes = {}
        self.recipe_ids = {}
        for header, fluid in ((1, False), (2, True)):
            for p in self.array(self.word(header)):
                record = {'id': self.string(self.word(p + 4)), 'name': self.string(self.word(p + 5)),
                          'mod': self.string(self.word(p + 6)), 'internalName': self.string(self.word(p + 7)),
                          'tooltip': [self.string(s) for s in self.array(self.word(p + 10))],
                          'unlocalizedName': self.string(self.word(p + 11)), 'nbt': self.string(self.word(p + 12)),
                          'fluid': fluid}
                if fluid:
                    record['gas'] = self.word(p + 15) == 1
                else:
                    record['stackSize'] = self.word(p + 15)
                    record['damage'] = self.word(p + 16)
                self.goods_by_pointer[p] = record
                self.goods[record['id']] = record
        for p in self.array(self.word(3)):
            self.groups[p] = sorted(self.goods_by_pointer[v]['id'] for v in self.array(self.word(p + 5)))
        for p in self.array(self.word(4)):
            self.methods[p] = {'name': self.string(self.word(p)), 'category': self.string(self.word(p + 1)),
                               'dimensions': self.array(self.word(p + 2)), 'shapeless': self.word(p + 4) == 1,
                               'singleblocks': self.refs(self.word(p + 5)), 'alternatives': self.refs(self.word(p + 3)),
                               'defaultCrafter': self.good_id(self.word(p + 6))}
        prefix = 0 if self.version == 6 else 4
        for p in self.array(self.word(5)):
            identity = self.string(self.word(p + prefix))
            io = self.array(self.word(p + prefix + 1))
            if len(io) % 5:
                raise ValueError('Invalid I/O array')
            entries = []
            for i in range(0, len(io), 5):
                kind, goods, slot, amount, chance = io[i:i + 5]
                variants = self.groups[goods] if kind == 1 else [self.good_id(goods)]
                entries.append([kind, slot, amount, chance, variants])
            record = {'method': self.methods[self.word(p + prefix + 2)], 'io': entries, 'gt': None}
            gt = self.word(p + prefix + 3)
            if gt != -1:
                metadata = {}
                for m in self.array(self.word(gt + 4)):
                    metadata[self.string(self.word(m))] = struct.unpack_from('<d', self.data, (m + 1) * 4)[0]
                record['gt'] = {'voltage': self.word(gt), 'ticks': self.word(gt + 1), 'amperage': self.word(gt + 2),
                                'tier': self.word(gt + 3), 'metadata': metadata, 'conflicts': self.word(gt + 5),
                                'specialValue': self.word(gt + 6)}
            self.recipes[identity] = record
            self.recipe_ids[p] = identity
        self.remaps = {self.string(self.word(p)): self.recipe_ids[self.word(p + 1)] for p in self.array(self.word(7))}
        self.categories = Counter(r['method']['category'] for r in self.recipes.values())
        self.types = Counter(r['method']['category'] + ':' + r['method']['name'] for r in self.recipes.values())

    def word(self, p):
        if p < 0 or p >= len(self.data) // 4:
            raise ValueError(f'Pointer outside file: {p}')
        return struct.unpack_from('<i', self.data, p * 4)[0]

    def array(self, p):
        length = self.word(p)
        if length < 0 or p + 1 + length > len(self.data) // 4:
            raise ValueError('Invalid array length')
        return [self.word(p + 1 + i) for i in range(length)]

    def string(self, p):
        if p == -1:
            return None
        length = self.word(p)
        if length < 0 or (p + 1) * 4 + length > len(self.data):
            raise ValueError('Invalid string length')
        return self.data[(p + 1) * 4:(p + 1) * 4 + length].decode('utf-8')

    def good_id(self, p):
        return None if p == -1 else self.string(self.word(p + 4))

    def refs(self, p):
        return [self.good_id(v) for v in self.array(p)]


def difference(left, right, limit):
    only_left, only_right = sorted(left.keys() - right.keys()), sorted(right.keys() - left.keys())
    changes = [{'id': k, 'fields': sorted(f for f in left[k] if left[k].get(f) != right[k].get(f))}
               for k in sorted(left.keys() & right.keys()) if left[k] != right[k]]
    return {'onlyReference': len(only_left), 'onlyCandidate': len(only_right), 'changed': len(changes),
            'referenceSamples': only_left[:limit], 'candidateSamples': only_right[:limit], 'changeSamples': changes[:limit]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference')
    parser.add_argument('candidate')
    parser.add_argument('--samples', type=int, default=10)
    args = parser.parse_args()
    if args.samples < 0:
        parser.error('--samples must be nonnegative')
    left, right = Pack(args.reference), Pack(args.candidate)
    categories = sorted(left.categories.keys() | right.categories.keys())
    types = sorted(left.types.keys() | right.types.keys())
    output = {'reference': str(left.path), 'candidate': str(right.path),
              'categories': {k: [left.categories[k], right.categories[k]] for k in categories},
              'recipeTypes': {k: [left.types[k], right.types[k]] for k in types},
              'goods': difference(left.goods, right.goods, args.samples),
              'recipes': difference(left.recipes, right.recipes, args.samples),
              'remaps': {'reference': len(left.remaps), 'candidate': len(right.remaps)}}
    print(json.dumps(output, indent=2, ensure_ascii=False, allow_nan=False))


if __name__ == '__main__':
    main()
