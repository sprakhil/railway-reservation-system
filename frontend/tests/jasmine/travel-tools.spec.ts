import assert from 'node:assert/strict';
import { bookingCalendar, canAddToCalendar } from '../../src/app/core/calendar';
import { normalizeSearch, parseRecentSearches, RecentSearchStore, RECENT_SEARCHES_KEY } from '../../src/app/core/recent-searches';

const today='2026-09-27';
const search={fromStation:'PVL',toStation:'GOA',dateOfJourney:'2026-09-28',seatClass:'SL',passengers:2};
const booking={pnr:'1234567890',trainNumber:'56565',fromStation:'PVL',toStation:'GOA',seatClass:'SL',departureAt:'2026-09-28T15:10:00+05:30',status:'CONFIRMED'};
const now=new Date('2026-09-27T10:00:00Z');

it('calendar represents the exact departure instant and a one-hour reminder without invented arrival',()=>{
  const result=bookingCalendar(booking,now);
  assert.match(result,/DTSTART:20260928T094000Z\r\n/);
  assert.match(result,/TRIGGER:-PT1H\r\n/);
  assert.match(result,/CLASS:PRIVATE\r\n/);
  assert.match(result.replaceAll('\r\n ',''),/does not update automatically/);
  assert.ok(!result.includes('DTEND:'));
  assert.ok(!result.replaceAll('\r\n','').includes('\n'));
  assert.equal(result.match(/UID:.+/)?.[0],bookingCalendar(booking,new Date(now.getTime()+1000)).match(/UID:.+/)?.[0]);
});
it('calendar rejects unconfirmed, cancelled, past and ambiguous departure timestamps',()=>{
  for(const status of ['WAITING','PENDING_PAYMENT','CANCELLED','EXPIRED']){
    assert.equal(canAddToCalendar({...booking,status},now.getTime()),false);
    assert.throws(()=>bookingCalendar({...booking,status},now),/confirmed bookings/);
  }
  for(const departureAt of ['invalid','2026-09-28T15:10:00','2026-09-26T15:10:00Z',now.toISOString()]){
    assert.equal(canAddToCalendar({...booking,departureAt},now.getTime()),false);
  }
});
it('calendar safely escapes text, folds UTF-8 at 75 octets and excludes extra account data',()=>{
  const malicious={...booking,fromStation:'मुंबई'.repeat(30)+',;\\\r\nBEGIN:VEVENT',passengers:[{name:'Private Passenger'}],accessToken:'private-secret'};
  const result=bookingCalendar(malicious,now);
  assert.ok(result.split('\r\n').every(line=>Buffer.byteLength(line,'utf8')<=75));
  const unfolded=result.replaceAll('\r\n ','');
  assert.match(unfolded,/\\,\\;\\\\\\nBEGIN:VEVENT/);
  assert.equal(result.split('\r\n').filter(line=>line==='BEGIN:VEVENT').length,1);
  assert.ok(!result.includes('Private Passenger')&&!result.includes('private-secret'));
  assert.ok(unfolded.includes('मुंबई'.repeat(30)));
});
it('recent searches normalize, deduplicate, cap at five and persist across page loads',()=>{
  const data=new Map<string,string>();
  const storage={getItem:(key:string)=>data.get(key)||null,setItem:(key:string,value:string)=>{data.set(key,value)},removeItem:(key:string)=>{data.delete(key)}};
  const store=new RecentSearchStore(()=>storage);store.setOwner('alice');
  store.save({...search,fromStation:' pvl '},today);
  assert.equal(store.save(search,today).length,1);
  for(let passengers=1;passengers<=6;passengers++)store.save({...search,passengers},today);
  assert.deepEqual(store.read().map(s=>s.passengers),[6,5,4,3,2]);
  assert.deepEqual(new RecentSearchStore(()=>storage).setOwner('alice'),store.read());
  assert.equal(store.remove({...search,passengers:4}).length,4);
  assert.deepEqual(store.clear(),[]);assert.equal(data.has(RECENT_SEARCHES_KEY),false);
});
it('recent search parser ignores corrupt and invalid entries and never retains unrelated fields',()=>{
  for(const raw of ['bad json','{}','null','42'])assert.deepEqual(parseRecentSearches(raw),[]);
  assert.equal(normalizeSearch({...search,passengers:-1}),null);
  assert.equal(normalizeSearch({...search,seatClass:'bad'}),null);
  assert.equal(normalizeSearch({...search,fromStation:'bad code'}),null);
  assert.equal(normalizeSearch({...search,dateOfJourney:'2026-02-30'}),null);
  assert.deepEqual(parseRecentSearches(JSON.stringify([null,{...search,accessToken:'secret'},search])),[search]);
  // Keep a valid historic route so it can be reused with a new date.
  assert.equal(parseRecentSearches(JSON.stringify([{...search,dateOfJourney:'2025-01-01'}])).length,1);
});
it('blocked storage or write quota failures do not prevent saving or clearing in memory',()=>{
  const unavailable=new RecentSearchStore(()=>{throw Error('blocked')});
  assert.deepEqual(unavailable.save(search,today),[search]);
  assert.deepEqual(unavailable.read(),[search]);
  assert.deepEqual(unavailable.clear(),[]);
  const quota=new RecentSearchStore(()=>({getItem:()=>null,setItem:()=>{throw Error('quota')},removeItem:()=>{throw Error('blocked')}}));
  quota.save(search,today);assert.deepEqual(quota.read(),[search]);
  assert.deepEqual(quota.save({...search,dateOfJourney:'2026-09-26'},today),[search]);
  assert.deepEqual(quota.clear(),[]);assert.deepEqual(quota.read(),[]);
});


it('recent searches are isolated per immutable account and shared legacy history is discarded',()=>{
 const data=new Map<string,string>([[RECENT_SEARCHES_KEY,JSON.stringify([search])]]);
 const storage={getItem:(key:string)=>data.get(key)||null,setItem:(key:string,value:string)=>{data.set(key,value)},removeItem:(key:string)=>{data.delete(key)}};
 const store=new RecentSearchStore(()=>storage);
 assert.deepEqual(store.setOwner('alice'),[]);assert.equal(data.has(RECENT_SEARCHES_KEY),false);
 store.save(search,today);assert.deepEqual(store.setOwner(null),[]);
 assert.deepEqual(store.setOwner('bob'),[]);store.save({...search,passengers:3},today);
 assert.deepEqual(store.setOwner('alice'),[search]);assert.equal(store.setOwner('bob')[0].passengers,3);
 store.setOwner(null);store.save(search,today);assert.deepEqual(new RecentSearchStore(()=>storage).read(),[]);
});

