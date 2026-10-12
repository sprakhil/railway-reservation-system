import assert from 'node:assert/strict';
import {chatBookingQuery} from '../../src/app/core/chat-booking';
import {ChatCard} from '../../src/app/core/chat-stream';
const card={kind:'TRAIN',status:'CNF',passengers:1,fromStation:'AAA',toStation:'BBB',dateOfJourney:'2030-01-02',seatClass:'SL'} as ChatCard;
it('chat booking leaves passenger selection to the booking page',()=>{
 assert.deepEqual(chatBookingQuery(card),{fromStation:'AAA',toStation:'BBB',dateOfJourney:'2030-01-02',seatClass:'SL'});
 assert.ok(!('passengers' in chatBookingQuery(card)!));
});
it('booking cards and unavailable suggestions cannot become train checkout links',()=>{
 assert.equal(chatBookingQuery({...card,kind:'BOOKING'}),null);
 assert.equal(chatBookingQuery({...card,status:'UNAVAILABLE'}),null);
});

