import { Search, RouteSearch } from './models';
export function indiaDate(date=new Date()):string {
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(date);
  return ['year','month','day'].map(t=>parts.find(p=>p.type===t)!.value).join('-');
}
export function addMonths(date:string,count:number):string {
  const [y,m,d]=date.split('-').map(Number);
  const last=new Date(Date.UTC(y,m-1+count+1,0)).getUTCDate();
  return new Date(Date.UTC(y,m-1+count,Math.min(d,last))).toISOString().slice(0,10);
}
export function tomorrow():string {const d=indiaDate(); const t=new Date(d+'T00:00:00Z');t.setUTCDate(t.getUTCDate()+1);return t.toISOString().slice(0,10)}
export function routeSearchError(s:RouteSearch,today=indiaDate()):string {
  if(!s.fromStation.trim()||!s.toStation.trim())return 'Choose both your departure and arrival station.';
  if(s.fromStation.trim().toUpperCase()===s.toStation.trim().toUpperCase())return 'Choose two different stations for your journey.';
  if(!/^\d{4}-\d{2}-\d{2}$/.test(s.dateOfJourney)||!Number.isFinite(Date.parse(s.dateOfJourney))||new Date(s.dateOfJourney).toISOString().slice(0,10)!==s.dateOfJourney)return 'Choose a valid travel date.';
  if(s.dateOfJourney<today||s.dateOfJourney>addMonths(today,2))return 'Choose a date from today through two calendar months ahead.';
  return '';
}
export function searchError(s:Search,today=indiaDate()):string {
  return routeSearchError(s,today)||(!Number.isInteger(+s.passengers)||+s.passengers<1||+s.passengers>6?'A booking can have between 1 and 6 passengers.':'');
}
export function safeReturn(value:string|null):string {return value?.startsWith('/')&&!value.startsWith('//')&&!value.includes('\\')?value:'/'}
export function messageOf(error:unknown):string {
  const e=error as {status?:number;error?:{message?:string;fieldErrors?:Record<string,string>;requestId?:string}|string;message?:string};
  if(e?.status===0)return 'We could not reach the railway service. Check that the backend and gateway are running, then try again.';
  if(e?.error&&typeof e.error==='object') {
    const fields=Object.entries(e.error.fieldErrors||{}).map(([key,v])=>`${key}: ${v}`).join(' · ');
    return [e.error.message||'The request could not be completed.',fields,e.error.requestId?`Reference: ${e.error.requestId}`:''].filter(Boolean).join(' ');
  }
  return (typeof e?.error==='string'&&!e.error.startsWith('<')?e.error:e?.message)||'Something went wrong. Please try again.';
}
export function duration(from:string,to:string):string {const m=Math.round((Date.parse(to)-Date.parse(from))/60000);return `${Math.floor(m/60)}h ${m%60}m`}
export function pretty(value:string|null|undefined):string {return (value||'—').replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase())}
