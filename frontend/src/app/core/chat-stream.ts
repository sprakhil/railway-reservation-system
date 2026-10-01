export interface ChatCard {kind:'TRAIN'|'BOOKING';title:string;pnr:string|null;trainNumber:string;fromStation:string;toStation:string;dateOfJourney:string;seatClass:string;passengers:number;status:string;fare:number;availableSeats:number|null;departureAt:string;refundStatus:string|null;refundAmount:number|null}
export interface ChatSource {id:string;title:string;excerpt:string}
export interface ChatEvent {type:'status'|'delta'|'cards'|'sources'|'fallback'|'done';text:string;cards:ChatCard[];sources:ChatSource[]}
export interface ChatMessage {role:'user'|'assistant';text:string;cards:ChatCard[];sources:ChatSource[];at:string}
export interface ChatConversation {id:string;expiresAt:string;messages:ChatMessage[]}

/** Incremental SSE parser supports split UTF-8 characters, CRLF and multiline data. */
export async function consumeChatStream(stream:ReadableStream<Uint8Array>,onEvent:(event:ChatEvent)=>void):Promise<void>{
 const reader=stream.getReader(),decoder=new TextDecoder();let pending='',finished=false;
 const parse=(frame:string)=>{
  const data=frame.split('\n').filter(line=>line.startsWith('data:')).map(line=>line.slice(5).trimStart()).join('\n');
  if(!data)return;
  const event=JSON.parse(data) as ChatEvent;
  if(!['status','delta','cards','sources','fallback','done'].includes(event.type)||typeof event.text!=='string'||!Array.isArray(event.cards)||!Array.isArray(event.sources))throw new Error('The assistant returned an unreadable update. Please try again.');
  onEvent(event);if(event.type==='done')finished=true;
 };
 try{while(true){const {value,done}=await reader.read();pending+=done?decoder.decode():decoder.decode(value,{stream:true});pending=pending.replace(/\r\n/g,'\n');if(pending.length>65536)throw new Error('The assistant response was too large.');let boundary;while((boundary=pending.indexOf('\n\n'))>=0){parse(pending.slice(0,boundary));pending=pending.slice(boundary+2)}if(done){if(pending.trim())parse(pending);break}}
  if(!finished)throw new Error('The reply was interrupted. Please send your question again.');
 }finally{await reader.cancel().catch(()=>{});reader.releaseLock()}
}
