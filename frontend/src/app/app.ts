import { Component, inject, signal, ElementRef, ViewChild, HostListener, DestroyRef } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth, Api } from './core/api';
import { Icon, Notice } from './shared/ui';
import { ChatPage } from './pages/chat';
import { NotificationBell } from './shared/notification-bell';
import { messageOf } from './core/utils';
@Component({selector:'rw-root',imports:[RouterOutlet,RouterLink,RouterLinkActive,Icon,Notice,NotificationBell,ChatPage],template:`
<a class="skip-link" href="#main">Skip to content</a><div class="utility-bar"><div class="container"><span><i></i> A little closer to your next destination</span><a routerLink="/help">A little help along the way <span>↗</span></a></div></div>
<header class="site-header"><div class="container nav-row"><a routerLink="/" class="brand" aria-label="Railwize home"><span class="brand-mark"><rw-icon name="train"/></span><span>railwize<span class="brand-dot">.</span></span></a><button class="menu-toggle icon-button" aria-label="Toggle navigation" [attr.aria-expanded]="menu()" (click)="menu.set(!menu())"><rw-icon [name]="menu()?'close':'menu'"/></button><nav [class.open]="menu()" (click)="menu.set(false)" aria-label="Main navigation"><a routerLink="/journeys" routerLinkActive="active">Find a train</a><a routerLink="/bookings" routerLinkActive="active">My journeys</a><a routerLink="/passengers" routerLinkActive="active">Saved passengers</a>@if(auth.admin()){<a routerLink="/admin" routerLinkActive="active">Admin workspace</a>}</nav><div class="nav-account">@if(auth.signedIn()){<rw-notification-bell/><a routerLink="/account" class="account-link"><span class="avatar">{{auth.username().slice(0,1).toUpperCase()}}</span><span>{{auth.username()}}</span></a><button class="icon-button" aria-label="Log out" title="Log out" [disabled]="busy()" (click)="logout()"><rw-icon name="logout"/></button>}@else{<a routerLink="/auth/login" class="button primary small">Sign in <rw-icon name="arrow"/></a>}</div></div></header>
@if(error()){<div class="container"><rw-notice [text]="error()"/><button class="text-link" (click)="error.set('')">Dismiss</button></div>}
<main id="main" tabindex="-1"><router-outlet/></main>
@if(auth.signedIn()&&!router.url.startsWith('/assistant')){<button #assistantLauncher type="button" class="assistant-launcher" [attr.aria-expanded]="assistantOpen()" aria-controls="railwize-assistant-panel" (click)="toggleAssistant()"><rw-icon name="sparkle"/> Ask Railwize <span>{{assistantOpen()?'−':'+'}}</span></button>@if(assistantOpen()){<section id="railwize-assistant-panel" class="assistant-panel" role="dialog" aria-label="Ask Railwize" aria-modal="false"><rw-chat [compact]="true" (dismiss)="closeAssistant()" (navigateAway)="assistantOpen.set(false)"/></section>}}
<footer class="site-footer"><div class="container footer-top"><a routerLink="/" class="brand"><span class="brand-mark"><rw-icon name="train"/></span><span>railwize.</span></a><p>For the places you'll go.<br>And the people you'll see.</p><div><a routerLink="/help">Booking guide</a><a routerLink="/account">Your account</a><a routerLink="/bookings">Your journeys</a></div></div><div class="container footer-bottom"><span>© {{year}} Railwize · Journeys, thoughtfully planned</span><span>Your next chapter starts here.</span><span>Made for the journey <rw-icon name="route"/></span></div></footer>
`})
export class App {
 assistantOpen=signal(false);
 @ViewChild('assistantLauncher') assistantLauncher?:ElementRef<HTMLButtonElement>;
 constructor(){const subscription=this.router.events.subscribe(event=>{if(event instanceof NavigationEnd)this.assistantOpen.set(false)});inject(DestroyRef).onDestroy(()=>subscription.unsubscribe())}
 toggleAssistant(){if(this.assistantOpen()){this.closeAssistant();return}this.assistantOpen.set(true);requestAnimationFrame(()=>document.getElementById('chat-message')?.focus())}
 closeAssistant(){this.assistantOpen.set(false);this.assistantLauncher?.nativeElement.focus()}
 @HostListener('document:keydown.escape') escapeAssistant(){if(this.assistantOpen())this.closeAssistant()}
auth=inject(Auth);api=inject(Api);router=inject(Router);menu=signal(false);busy=signal(false);error=signal('');year=new Date().getFullYear();async logout(){this.busy.set(true);this.error.set('');try{await this.api.post<void>('/auth/logout');this.assistantOpen.set(false);this.auth.clear();await this.router.navigate(['/'])}catch(e){this.error.set(messageOf(e)+' Your session could not be revoked. Please retry logout.')}finally{this.busy.set(false)}}}

