import {normalizeSearch} from './recent-searches';
import {RouteSearch} from './models';
// Only public, validated search fields can be carried back from a leg's checkout.
export function journeySearch(raw:string|null):RouteSearch|null {
  if(!raw||raw.length>600)return null;
  try{return normalizeSearch(JSON.parse(raw))}catch{return null}
}
