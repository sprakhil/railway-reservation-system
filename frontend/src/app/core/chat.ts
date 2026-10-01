import { Injectable, inject } from '@angular/core';
import { Api, Auth } from './api';
import { ChatConversation, ChatEvent, consumeChatStream } from './chat-stream';

@Injectable({providedIn:'root'})
export class ChatApi {
 private api=inject(Api);private auth=inject(Auth);
 create(){return this.api.post<ChatConversation>('/chat/conversations')}
 get(id:string){return this.api.get<ChatConversation>('/chat/conversations/'+encodeURIComponent(id))}
 clear(id:string){return this.api.delete<void>('/chat/conversations/'+encodeURIComponent(id))}
 async send(id:string,message:string,signal:AbortSignal,onEvent:(event:ChatEvent)=>void){
  const owner=this.auth.session()?.username;if(!owner)throw new Error('Please sign in to use the assistant.');
  const deadline=AbortSignal.any([signal,AbortSignal.timeout(65000)]);
  const request=()=>fetch('/api/chat/conversations/'+encodeURIComponent(id)+'/messages',{method:'POST',headers:{'Content-Type':'application/json','Accept':'text/event-stream','Authorization':'Bearer '+this.auth.session()?.accessToken},body:JSON.stringify({message}),credentials:'same-origin',signal:deadline});
  let response=await request();
  if(response.status===401){await this.auth.refresh();if(signal.aborted||owner!==this.auth.session()?.username)throw new Error('Your session changed.');response=await request()}
  if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.message||'The assistant could not connect. Please try again.')}
  if(!response.body||!response.headers.get('content-type')?.includes('text/event-stream'))throw new Error('The assistant could not start a reply.');
  await consumeChatStream(response.body,event=>{if(!signal.aborted&&owner===this.auth.session()?.username)onEvent(event)});
 }
}
