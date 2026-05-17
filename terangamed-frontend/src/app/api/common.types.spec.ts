import { toHttpParams } from './common.types';

describe('toHttpParams', () => {
  it('retourne HttpParams vide si input null/undefined', () => {
    expect(toHttpParams(undefined).keys()).toEqual([]);
  });

  it('filtre les valeurs null / undefined / empty string', () => {
    const params = toHttpParams({
      a: 'value',
      b: null,
      c: undefined,
      d: '',
      e: 0,
      f: false
    });
    expect(params.keys().sort()).toEqual(['a', 'e', 'f']);
    expect(params.get('a')).toBe('value');
    expect(params.get('e')).toBe('0');
    expect(params.get('f')).toBe('false');
  });

  it('sérialise les Date en ISO', () => {
    const date = new Date('2026-04-15T10:00:00Z');
    const params = toHttpParams({ from: date });
    expect(params.get('from')).toBe(date.toISOString());
  });

  it('arrays → params répétés (skip valeurs null)', () => {
    const params = toHttpParams({
      sort: ['lastName,asc', null, 'createdAt,desc']
    });
    expect(params.getAll('sort')).toEqual(['lastName,asc', 'createdAt,desc']);
  });
});
