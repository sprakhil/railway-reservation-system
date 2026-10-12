import {ChatCard} from './chat-stream';
/** Passenger count is deliberately chosen on the booking page, never inferred from a chat quote. */
export function chatBookingQuery(card:ChatCard){
 if(card.kind!=='TRAIN'||card.status==='UNAVAILABLE')return null;
 return {fromStation:card.fromStation,toStation:card.toStation,dateOfJourney:card.dateOfJourney,seatClass:card.seatClass};
}
