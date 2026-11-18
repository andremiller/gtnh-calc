import { SearchQuery } from '../searchQuery';

describe('Quoted search', () => {
    it('requires contiguous case-insensitive phrase text', () => {
        const query = new SearchQuery('"steel plate"');
        expect(query.Match('Stainless STEEL PLATE')).toBe(true);
        expect(query.Match('steel reinforced plate')).toBe(false);
        expect(query.Match('plate of steel')).toBe(false);
    });
    it('combines phrases, normal terms and mod filters', () => {
        const query = new SearchQuery('@gregtech "steel plate" dense');
        expect(query.mod).toBe('gregtech');
        expect(query.Match('Dense Steel Plate')).toBe(true);
        expect(query.Match('Steel Plate')).toBe(false);
    });
    it('keeps prefilter grams within phrase word boundaries', () => {
        expect(new SearchQuery('"steel plate"').indexBits).toEqual(new SearchQuery('steel plate').indexBits);
        expect(new SearchQuery('""').words).toEqual([]);
    });
});
