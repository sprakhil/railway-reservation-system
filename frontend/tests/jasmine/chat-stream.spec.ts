import assert from 'node:assert/strict';
import {consumeChatStream,ChatEvent} from '../../src/app/core/chat-stream';
function bytes(text:string){const values=new TextEncoder().encode(text);return new ReadableStream<Uint8Array>({start(controller){for(const value of values)controller.enqueue(new Uint8Array([value]));controller.close()}})}
function frame(type:string,text=''){return 'event: '+type+'\r\ndata: '+JSON.stringify({type,text,cards:[],sources:[]})+'\r\n\r\n'}
it('chat stream handles byte-split UTF8 and CRLF',async()=>{const events:ChatEvent[]=[];await consumeChatStream(bytes(frame('delta','Hello ₹ traveller')+frame('done')),e=>events.push(e));assert.equal(events[0].text,'Hello ₹ traveller');assert.equal(events.length,2)});
it('chat stream rejects malformed and truncated replies',async()=>{await assert.rejects(consumeChatStream(bytes('data: nope\n\n'),()=>{}));await assert.rejects(consumeChatStream(bytes(frame('delta','partial')),()=>{}),/interrupted/);await assert.rejects(consumeChatStream(bytes('data: {}\n\n'),()=>{}),/unreadable/)});
it('chat stream enforces frame size limit',async()=>{await assert.rejects(consumeChatStream(bytes('data: '+ 'x'.repeat(65537)),()=>{}),/too large/)});

