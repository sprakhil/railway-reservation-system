import assert from 'node:assert/strict';
import {journeySearch} from '../../src/app/core/journey-link';
it('journey return link carries only validated public search fields',()=>{
 const search={fromStation:' aaa ',toStation:'ccc',dateOfJourney:'2030-01-02',seatClass:'SL',passengers:2};
 assert.deepEqual(journeySearch(JSON.stringify({...search,accessToken:'ignored',url:'https://invalid.example'})),{fromStation:'AAA',toStation:'CCC',dateOfJourney:search.dateOfJourney});
});
it('malformed journey context never becomes navigation data',()=>{
 for(const raw of [null,'{','https://example.invalid','x'.repeat(601),JSON.stringify({fromStation:'AAA',toStation:'AAA',dateOfJourney:'2030-01-02',seatClass:'SL',passengers:2})])assert.equal(journeySearch(raw),null);
});

