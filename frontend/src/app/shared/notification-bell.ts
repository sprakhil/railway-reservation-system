import { Component, inject, signal, HostListener, ElementRef, ViewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Notifications, NotificationItem, notificationRoute } from '../core/notifications';
import { Icon } from './ui';

@Component({selector:'rw-notification-bell',imports:[Icon,DatePipe,RouterLink],template:`
<button #trigger class="icon-button notification-trigger" [attr.aria-label]="'Notifications, '+inbox.unread()+' unread'" [attr.aria-expanded]="open()" aria-controls="notification-panel" (click)="toggle()"><rw-icon name="bell"/>@if(inbox.unread()){<span class="notification-badge">{{inbox.unread()>99?'99+':inbox.unread()}}</span>}</button>
@if(open()){
<section id="notification-panel" class="notification-panel" aria-label="Notifications">
 <div class="notification-heading"><div><span class="eyebrow">YOUR JOURNEY, UP TO DATE</span><h2>Notifications <span class="notification-count">{{inbox.unread()}} unread</span></h2></div><button class="icon-button" aria-label="Close notifications" (click)="close()"><rw-icon name="close"/></button></div>
 <div class="notification-tools"><span>Updates every 5 seconds</span><button class="text-link" [disabled]="busy()||!inbox.unread()" (click)="readAll()">Mark all as read</button></div>
 @if(inbox.error()||error()){<p class="notification-error" role="status">{{error()||inbox.error()}} <button class="text-link" (click)="inbox.refresh()">Retry</button></p>}
 <div class="notification-items">@for(item of inbox.items().slice(0,3);track item.id){
 <button class="notification-item" [class.unread]="!item.readAt" [disabled]="busy()" (click)="visit(item)">
 <span class="notification-symbol"><rw-icon [name]="item.type.includes('REFUND')?'refresh':item.type==='PROMOTED'||item.type==='PAYMENT_RECEIVED'?'check':'ticket'"/></span>
 <span class="notification-copy"><strong>{{item.title}}</strong><span class="notification-pnr">{{item.supportTicketId?'Support request '+item.supportTicketId:'PNR '+item.pnr}}</span><span class="notification-excerpt">{{item.message}}</span><span class="notification-meta">{{item.createdAt|date:'d MMM, h:mm a'}} <span>{{item.supportTicketId?'View request →':'View booking →'}}</span></span></span>@if(!item.readAt){<span class="unread-dot" aria-label="Unread"></span>}</button>
 }@empty{<div class="notification-empty"><rw-icon name="bell"/><h3>{{inbox.ready()?'You’re all caught up':'Loading your updates…'}}</h3><p>Booking, payment, refund and support updates will appear here.</p></div>}</div>
 <a routerLink="/notifications" class="notification-footer" (click)="open.set(false)">View all notifications <rw-icon name="arrow"/></a>
</section>}
`})
export class NotificationBell {
  inbox=inject(Notifications);private router=inject(Router);private host=inject(ElementRef);open=signal(false);busy=signal(false);error=signal('');
  @ViewChild('trigger') trigger!:ElementRef<HTMLButtonElement>;
  toggle(){this.open.update(v=>!v);this.error.set('');if(this.open())void this.inbox.refresh()}
  close(){this.open.set(false);this.trigger.nativeElement.focus()}
  @HostListener('document:click',['$event']) outside(event:MouseEvent){if(!this.host.nativeElement.contains(event.target))this.open.set(false)}
  @HostListener('document:keydown.escape') escape(){if(this.open())this.close()}
  @HostListener('focusout',['$event']) blur(event:FocusEvent){if(event.relatedTarget&&!this.host.nativeElement.contains(event.relatedTarget))this.open.set(false)}
  async visit(item:NotificationItem){this.busy.set(true);this.error.set('');try{await this.inbox.read(item);this.open.set(false);await this.router.navigate(notificationRoute(item))}catch{this.error.set('Could not mark this update as read. Please retry.')}finally{this.busy.set(false)}}
  async readAll(){this.busy.set(true);this.error.set('');try{await this.inbox.readAll()}catch{this.error.set('Could not mark notifications as read. Please retry.')}finally{this.busy.set(false)}}
}

