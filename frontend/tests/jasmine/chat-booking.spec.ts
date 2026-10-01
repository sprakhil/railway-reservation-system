import assert from 'node:assert/strict';
import {chatBookingQuery} from '../../src/app/core/chat-booking';
import {ChatCard} from '../../src/app/core/chat-stream';
const card={kind:'TRAIN',status:'CNF',passengers:1,fromStation:'AAA',toStation:'BBB',dateOfJourney:'2030-01-02',seatClass:'SL'} as ChatCard;
it('chat booking requires an explicit valid count, independent of quoted count',()=>{
 for(const value of [0,-1,7,1.5,NaN])assert.equal(chatBookingQuery(card,value),null);
 assert.equal(chatBookingQuery(card,4)?.passengers,4);
 assert.equal(chatBookingQuery(card,4)?.dateOfJourney,'2030-01-02');
});
it('booking cards and unavailable suggestions cannot become train checkout links',()=>{
 assert.equal(chatBookingQuery({...card,kind:'BOOKING'},2),null);
 assert.equal(chatBookingQuery({...card,status:'UNAVAILABLE'},2),null);
});

