import { Component, inject, signal, effect, untracked, DestroyRef } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { Notifications, NotificationItem } from '../core/notifications';
import { Auth } from '../core/api';
import { Page } from '../core/models';
import { Icon, Notice, Pager } from '../shared/ui';

@Component({selector:'rw-notifications',imports:[DatePipe,Icon,Notice,Pager],template:`
<div class="container page-section notification-page"><span class="eyebrow">ALWAYS IN THE KNOW</span><h1>Your journey updates<span class="brand-dot">.</span></h1><p class="muted">One place for your booking, payment and refund updates.</p>
<div class="notification-page-tools"><div class="button-row"><button class="button small" [class.primary]="!unreadOnly()" [class.secondary]="unreadOnly()" [attr.aria-pressed]="!unreadOnly()" (click)="filter(false)">All updates</button><button class="button small" [class.primary]="unreadOnly()" [class.secondary]="!unreadOnly()" [attr.aria-pressed]="unreadOnly()" (click)="filter(true)">Unread · {{inbox.unread()}}</button></div><div class="button-row"><button class="button secondary small" [disabled]="busy()||loading()" (click)="refresh()"><rw-icon name="refresh"/> Refresh</button><button class="text-link" [disabled]="busy()||!inbox.unread()" (click)="readAll()">Mark all as read</button></div></div>
<rw-notice [text]="error()"/><div class="notification-history" [attr.aria-busy]="loading()">
@for(item of data()?.content||[];track item.id){<button class="notification-item" [class.unread]="!item.readAt" [disabled]="busy()" (click)="visit(item)"><span class="notification-symbol"><rw-icon [name]="item.type.includes('REFUND')?'refresh':item.type==='PROMOTED'||item.type==='PAYMENT_RECEIVED'?'check':'ticket'"/></span><span class="notification-copy"><strong>{{item.title}}</strong><span class="notification-pnr">PNR {{item.pnr}}</span><span>{{item.message}}</span><span class="notification-meta">{{item.createdAt|date:'d MMM y, h:mm a'}} <span>View booking →</span></span></span>@if(!item.readAt){<span class="unread-dot" aria-label="Unread"></span>}</button>}
@empty{<div class="notification-empty"><rw-icon name="bell"/><h2>{{loading()?'Loading your updates…':unreadOnly()?'You’re all caught up':'Your next journey starts here'}}</h2><p>{{unreadOnly()?'No unread notifications.': 'New booking, payment and refund updates will appear here.'}}</p></div>}</div>
<rw-pager [page]="page()" [pages]="data()?.totalPages||0" [total]="data()?.totalElements||0" noun="notifications" [busy]="loading()" (change)="page.set($event)"/><p class="small muted notification-footnote">Updates refresh every 5 seconds while this tab is visible. Open a booking for its current status.</p></div>
`})
export class NotificationHistory {
  inbox=inject(Notifications);private auth=inject(Auth);private router=inject(Router);page=signal(0);unreadOnly=signal(false);data=signal<Page<NotificationItem>|null>(null);error=signal('');loading=signal(false);busy=signal(false);private sequence=0;
  constructor(){effect(()=>{this.inbox.revision();this.page();this.unreadOnly();const owner=this.auth.session()?.username;untracked(()=>{if(owner)void this.load();else{this.sequence++;this.data.set(null)}})});inject(DestroyRef).onDestroy(()=>this.sequence++)}
  filter(value:boolean){this.page.set(0);this.unreadOnly.set(value)}
  async load(){const request=++this.sequence,owner=this.auth.session()?.username;this.loading.set(true);try{const result=await this.inbox.fetch(this.page(),this.unreadOnly());if(request!==this.sequence||owner!==this.auth.session()?.username)return;if(this.page()>0&&this.page()>=result.notifications.totalPages){this.page.set(Math.max(0,result.notifications.totalPages-1));return}this.data.set(result.notifications);this.error.set('')}catch{if(request===this.sequence)this.error.set('Notifications could not load. Please refresh to try again.')}finally{if(request===this.sequence)this.loading.set(false)}}
  async refresh(){await this.inbox.refresh();await this.load()}
  async readAll(){this.busy.set(true);try{await this.inbox.readAll();await this.load()}catch{this.error.set('Could not mark updates as read. Please retry.')}finally{this.busy.set(false)}}
  async visit(item:NotificationItem){this.busy.set(true);try{await this.inbox.read(item);await this.router.navigate(['/bookings',item.pnr])}catch{this.error.set('Could not mark this update as read. Please retry.')}finally{this.busy.set(false)}}
}
