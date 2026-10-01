import { ChatCard } from './chat-stream';
/** A train suggestion is a quote, never implicit consent to a passenger count. */
export function chatBookingQuery(card:ChatCard,passengers:number){
 if(card.kind!=='TRAIN'||card.status==='UNAVAILABLE'||!Number.isInteger(passengers)||passengers<1||passengers>6)return null;
 return {fromStation:card.fromStation,toStation:card.toStation,dateOfJourney:card.dateOfJourney,seatClass:card.seatClass,passengers};
}
