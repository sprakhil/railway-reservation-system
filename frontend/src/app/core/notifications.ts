import { Injectable, inject, signal, effect, untracked, DestroyRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { Auth } from './api';
import { Page } from './models';
import { NotificationRefresh } from './notification-refresh';

export interface NotificationItem {id:string;pnr:string|null;supportTicketId?:string|null;type:string;title:string;message:string;createdAt:string;readAt:string|null}
export function notificationRoute(item:NotificationItem):string[]{return item.supportTicketId&&/^SUP-[0-9a-f-]{36}$/.test(item.supportTicketId)?['/support',item.supportTicketId]:item.pnr?['/bookings',item.pnr]:['/notifications']}
export interface Inbox {notifications:Page<NotificationItem>;unreadCount:number}
export const NOTIFICATION_POLL_MS=5000;
const PATH='/api/reservations/notifications';

@Injectable({providedIn:'root'})
export class Notifications {
  private http=inject(HttpClient); private auth=inject(Auth); private refreshSignal=inject(NotificationRefresh);
  items=signal<NotificationItem[]>([]); unread=signal(0); revision=signal(0); loading=signal(false); error=signal(''); ready=signal(false);
  private owner=''; private generation=0; private active=false; private queued=false; private failures=0; private nextAttempt=0;
  constructor(){
    effect(()=>{const owner=this.auth.session()?.username||'';untracked(()=>{
      if(owner===this.owner)return;this.owner=owner;this.generation++;this.active=false;this.queued=false;
      this.items.set([]);this.unread.set(0);this.ready.set(false);this.error.set('');this.loading.set(false);this.failures=0;this.nextAttempt=0;this.revision.update(n=>n+1);
      if(owner)void this.refresh();
    })});
    effect(()=>{this.refreshSignal.version();untracked(()=>void this.refresh())});
    const timer=setInterval(()=>{if(Date.now()>=this.nextAttempt)void this.refresh()},NOTIFICATION_POLL_MS);
    const visible=()=>{if(!document.hidden)void this.refresh()};document.addEventListener('visibilitychange',visible);
    inject(DestroyRef).onDestroy(()=>{clearInterval(timer);document.removeEventListener('visibilitychange',visible);this.generation++});
  }
  async fetch(page=0,unreadOnly=false,size=20):Promise<Inbox>{
    return firstValueFrom(this.http.get<Inbox>(PATH,{params:{page,size,unreadOnly}}).pipe(timeout(8000)));
  }
  async refresh(){
    if(!this.owner||document.hidden)return;
    if(this.active){this.queued=true;return}
    const generation=this.generation;this.active=true;this.loading.set(true);
    try{
      const data=await this.fetch(0,false,5);if(generation!==this.generation)return;
      const changed=!this.ready()||!!this.error()||JSON.stringify(this.items())!==JSON.stringify(data.notifications.content)||this.unread()!==data.unreadCount;
      this.items.set(data.notifications.content);this.unread.set(data.unreadCount);this.ready.set(true);this.error.set('');this.failures=0;this.nextAttempt=0;
      if(changed)this.revision.update(n=>n+1);
    }catch{if(generation===this.generation){this.error.set('Notifications could not refresh. We’ll retry automatically.');this.nextAttempt=Date.now()+Math.min(30000,5000*2**++this.failures)}}
    finally{if(generation===this.generation){this.active=false;this.loading.set(false);if(this.queued){this.queued=false;if(!this.error())void this.refresh()}}}
  }
  async read(item:NotificationItem){if(!item.readAt)await firstValueFrom(this.http.put<void>(PATH+'/'+encodeURIComponent(item.id)+'/read',{}).pipe(timeout(8000)));await this.refresh()}
  async readAll(){await firstValueFrom(this.http.put<void>(PATH+'/read-all',{}).pipe(timeout(8000)));await this.refresh()}
}
