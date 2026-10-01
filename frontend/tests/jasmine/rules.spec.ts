import assert from 'node:assert/strict';
import {addMonths,indiaDate,searchError,safeReturn,messageOf} from '../../src/app/core/utils';
const query={fromStation:'DSTA',toStation:'DSTC',dateOfJourney:'2026-11-24',seatClass:'SL',passengers:1};
it('two calendar months is inclusive, not a fixed sixty-day window',()=>{assert.equal(searchError(query,'2026-09-24'),'');assert.match(searchError({...query,dateOfJourney:'2026-11-25'},'2026-09-24'),/two calendar months/)});
it('month ends clamp correctly including leap years',()=>{assert.equal(addMonths('2027-12-31',2),'2028-02-29');assert.equal(addMonths('2026-12-31',2),'2027-02-28')});
it('station identity is checked after trimming and case normalization',()=>assert.match(searchError({...query,toStation:' dsta '},'2026-09-24'),/different stations/));
it('passenger counts reject negatives, fractions, zero and above six',()=>{for(const passengers of [-1,0,1.5,7,NaN])assert.match(searchError({...query,passengers},'2026-09-24'),/between 1 and 6/)});
it('past and impossible dates fail validation',()=>{assert.match(searchError({...query,dateOfJourney:'2026-09-23'},'2026-09-24'),/Choose a date/);assert.match(searchError({...query,dateOfJourney:'2026-09-31'},'2026-09-24'),/valid travel date/)});
it('India date changes before UTC date',()=>assert.equal(indiaDate(new Date('2026-09-24T19:00:00Z')),'2026-09-25'));
it('login returns only to local routes',()=>{assert.equal(safeReturn('//evil.example'),'/');assert.equal(safeReturn('https://evil.example'),'/');assert.equal(safeReturn('/\\evil'),'/');assert.equal(safeReturn('/book/DM001?passengers=2'),'/book/DM001?passengers=2')});
it('API validation and correlation details are readable',()=>{const message=messageOf({status:400,error:{message:'Invalid request',fieldErrors:{'passengers[0].age':'Age must be valid'},requestId:'test-request'}});assert.match(message,/Age must be valid/);assert.match(message,/test-request/);assert.match(messageOf({status:0}),/gateway/) });

