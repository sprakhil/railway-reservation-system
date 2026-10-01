import { Injectable, inject, signal, effect, untracked, DestroyRef } from '@angular/core';
import { Auth } from './api';
import { ChatApi } from './chat';
import { ChatMessage, ChatEvent } from './chat-stream';
import { messageOf } from './utils';
@Injectable({providedIn:'root'})
export class ChatSession {
 private api=inject(ChatApi);private auth=inject(Auth);private controller:AbortController|null=null;private generation=0;private owner='';
 messages=signal<ChatMessage[]>([]);busy=signal(false);loading=signal(false);error=signal('');status=signal('');draft='';conversationId='';
 prompts=['Show my bookings','When is my next journey?','Which tickets have I cancelled?','How many bookings do I have?','Help me find a train','What is the refund policy?'];
 constructor(){effect(()=>{const owner=this.auth.session()?.username||'';untracked(()=>{if(owner===this.owner)return;this.owner=owner;this.controller?.abort();this.generation++;this.messages.set([]);this.conversationId='';this.draft='';this.busy.set(false);this.loading.set(false);this.error.set('');this.status.set('');if(owner)void this.open(owner)})});inject(DestroyRef).onDestroy(()=>{this.generation++;this.controller?.abort()})}
 private storageKey(owner:string){return 'railwize.chat.'+owner}
 private remember(owner:string,id:string){try{sessionStorage.setItem(this.storageKey(owner),id)}catch{}}
 private async open(owner:string){const version=this.generation;this.loading.set(true);this.error.set('');try{let saved='';try{saved=sessionStorage.getItem(this.storageKey(owner))||''}catch{}let conversation;
  if(saved){try{conversation=await this.api.get(saved)}catch(e){if((e as {status?:number}).status!==404)throw e}}
  conversation??=await this.api.create();if(version!==this.generation)return;this.conversationId=conversation.id;this.messages.set(conversation.messages);this.remember(owner,conversation.id);this.scroll();
 }catch(e){if(version===this.generation)this.error.set(messageOf(e))}finally{if(version===this.generation)this.loading.set(false)}}
 async newConversation(){if(this.busy()||this.loading())return;const version=this.generation,owner=this.owner;this.loading.set(true);this.error.set('');try{if(this.conversationId)await this.api.clear(this.conversationId);if(version!==this.generation)return;this.conversationId='';this.messages.set([]);const result=await this.api.create();if(version!==this.generation)return;this.conversationId=result.id;this.remember(owner,result.id)}catch(e){if(version===this.generation)this.error.set(messageOf(e))}finally{if(version===this.generation)this.loading.set(false)}}
 usePrompt(prompt:string){this.draft=prompt;}
 key(event:KeyboardEvent){if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();if(!this.busy())void this.send()}}
 async send(){if(!this.draft.trim()||this.busy()||!this.conversationId)return;const input=this.draft.trim(),version=this.generation;this.draft='';this.error.set('');this.busy.set(true);this.status.set('Connecting…');const at=new Date().toISOString();this.messages.update(rows=>[...rows,{role:'user',text:input,cards:[],sources:[],at},{role:'assistant',text:'',cards:[],sources:[],at}].slice(-20) as ChatMessage[]);this.controller=new AbortController();this.scroll();
  try{await this.api.send(this.conversationId,input,this.controller.signal,event=>{if(version===this.generation)this.receive(event)})}catch(e){if(version===this.generation){this.error.set(this.controller?.signal.aborted?'Reply stopped. You can send another question.':messageOf(e));this.messages.update(rows=>rows.map((m,i)=>i===rows.length-1&&!m.text?{...m,text:'No complete reply was received.'}:m))}}
  finally{if(version===this.generation){this.busy.set(false);this.status.set('')}}
 }
 private receive(event:ChatEvent){if(event.type==='status'){this.status.set(event.text);return}if(event.type==='done'){this.status.set(event.text==='cached'?'Answered from the policy cache':'Reply complete');return}this.messages.update(rows=>rows.map((m,i)=>i!==rows.length-1?m:{...m,text:event.type==='fallback'?event.text:event.type==='delta'?m.text+event.text:m.text,cards:event.type==='fallback'?[]:event.type==='cards'?event.cards:m.cards,sources:event.type==='fallback'?[]:event.type==='sources'?event.sources:m.sources}));this.scroll()}
 private scroll(){}
 stop(){this.controller?.abort()}
}

