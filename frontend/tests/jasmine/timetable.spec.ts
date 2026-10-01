import assert from 'node:assert/strict';
import { timetablePayload } from '../../src/app/core/timetable';
import { Stop } from '../../src/app/core/models';
const route=():Stop[]=>[
 {stationCode:' pvl ',stopSequence:1,arrivalTime:'15:00',departureTime:'15:10',dayNumber:1,departureDayNumber:1,distanceKm:0},
 {stationCode:'GOA',stopSequence:2,arrivalTime:'11:00',departureTime:'11:01',dayNumber:2,departureDayNumber:2,distanceKm:750}
];
it('normalizes endpoint times without mutating the draft and accepts overnight travel',()=>{
 const draft=route(),payload=timetablePayload(draft);
 assert.equal(payload[0].stationCode,'PVL');assert.equal(payload[0].arrivalTime,null);assert.equal(payload[1].departureTime,null);
 assert.equal(draft[0].arrivalTime,'15:00');assert.equal(payload[1].dayNumber,2);
});
it('rejects reverse chronology, duplicate stations, and invalid cumulative distances',()=>{
 let r=route();r[1].dayNumber=1;assert.throws(()=>timetablePayload(r),/arrival must follow/);
 r=route();r[1].stationCode='PVL';assert.throws(()=>timetablePayload(r),/unique/);
 r=route();r[1].distanceKm=0;assert.throws(()=>timetablePayload(r),/distance must increase/);
});
it('intermediate stop requires both times and supports a midnight departure',()=>{
 const r=route();r.splice(1,0,{...r[1],stationCode:'MID',arrivalTime:'23:55',departureTime:'00:05',dayNumber:1,departureDayNumber:2,distanceKm:300});
 assert.equal(timetablePayload(r)[2].stopSequence,3);
 r[1].departureDayNumber=1;assert.throws(()=>timetablePayload(r),/departure cannot be before arrival/);
 r[1].departureTime=null;assert.throws(()=>timetablePayload(r),/enter a valid time/);
});

