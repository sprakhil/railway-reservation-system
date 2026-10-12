import { RouteSearch } from './models';
import { indiaDate, routeSearchError } from './utils';

export const RECENT_SEARCHES_KEY = 'railwize.recent-searches.v1';
export const RECENT_SEARCH_LIMIT = 5;
type SearchStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

// Read only the public search fields, including when storage contains unexpected data.
export function normalizeSearch(value: unknown): RouteSearch | null {
  if (!value || typeof value !== 'object') return null;
  const s = value as Record<string, unknown>;
  if (typeof s['fromStation'] !== 'string' || typeof s['toStation'] !== 'string'
      || typeof s['dateOfJourney'] !== 'string') return null;
  const query: RouteSearch = {
    fromStation: s['fromStation'].trim().toUpperCase(),
    toStation: s['toStation'].trim().toUpperCase(),
    dateOfJourney: s['dateOfJourney']
  };
  if (![query.fromStation, query.toStation].every(code => /^[A-Z0-9]{1,10}$/.test(code))
      || routeSearchError(query, query.dateOfJourney)) return null;
  return query;
}

export function searchKey(s: RouteSearch): string {
  return [s.fromStation, s.toStation, s.dateOfJourney].join('|');
}

export function parseRecentSearches(raw: string | null): RouteSearch[] {
  try {
    const values: unknown = JSON.parse(raw || '[]');
    if (!Array.isArray(values)) return [];
    const result: RouteSearch[] = [];
    const seen = new Set<string>();
    for (const value of values) {
      const s = normalizeSearch(value);
      if (s && !seen.has(searchKey(s))) {
        result.push(s); seen.add(searchKey(s));
        if (result.length === RECENT_SEARCH_LIMIT) break;
      }
    }
    return result;
  } catch { return []; }
}

export class RecentSearchStore {
  private memory: RouteSearch[] = [];
  private persistent = true;
  private owner: string | null = null;
  constructor(private storage: () => SearchStorage = () => localStorage) {}
  setOwner(owner: string | null): RouteSearch[] {
    if (owner !== this.owner) { this.owner = owner; this.memory = []; this.persistent = true; }
    // Old shared history has no verifiable owner. Never migrate it into an account.
    try { this.storage().removeItem(RECENT_SEARCHES_KEY); } catch {}
    return this.read();
  }
  private key(){ return 'railwize.recent-searches.v2.'+encodeURIComponent(this.owner!); }
  read(): RouteSearch[] {
    try { if (this.owner && this.persistent) this.memory = parseRecentSearches(this.storage().getItem(this.key())); }
    catch { this.persistent = false; }
    return this.memory.map(s => ({ ...s }));
  }
  save(value: RouteSearch, today = indiaDate()): RouteSearch[] {
    const s = normalizeSearch(value);
    if (!s || routeSearchError(s, today)) return this.read();
    return this.write([s, ...this.read().filter(item => searchKey(item) !== searchKey(s))].slice(0, RECENT_SEARCH_LIMIT));
  }
  remove(s: RouteSearch): RouteSearch[] {
    return this.write(this.read().filter(item => searchKey(item) !== searchKey(s)));
  }
  clear(): RouteSearch[] { return this.write([]); }
  private write(values: RouteSearch[]): RouteSearch[] {
    this.memory = values;
    if (!this.owner) return values.map(s => ({ ...s }));
    try {
      if (values.length) this.storage().setItem(this.key(), JSON.stringify(values));
      else this.storage().removeItem(this.key());
    } catch { this.persistent = false; }
    return values.map(s => ({ ...s }));
  }
}
