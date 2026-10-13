import {Component,DestroyRef,effect,inject,signal,untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute,Router,RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Api,Auth} from '../core/api';
import {Page} from '../core/models';
import {SupportTicket,SupportMessage,SupportSubmission,SUPPORT_CATEGORIES,SUPPORT_STATUSES} from '../core/support';
import {messageOf,pretty} from '../core/utils';
import {Icon,Notice,Pager} from '../shared/ui';

@Component({selector:'rw-support',imports:[DatePipe,FormsModule,RouterLink,Icon,Notice,Pager],styles:[`
 :host{display:block}.support-wrap{padding-top:36px;padding-bottom:60px;max-width:1100px}.support-head{display:flex;align-items:start;justify-content:space-between;gap:20px;margin-bottom:24px}
 .support-head h1{margin:8px 0 12px}.support-head p{max-width:630px;color:var(--muted)}.support-wrap textarea{width:100%;min-height:140px;resize:vertical;padding:14px;border:1px solid var(--line);border-radius:12px;font:inherit;background:#fff;color:var(--ink)}
 .request-form{max-width:760px}.support-list{display:grid;gap:12px;margin:20px 0}.support-row{display:block;text-decoration:none;color:inherit;padding:22px;border:1px solid var(--line);background:#fff;border-radius:16px;transition:border-color .2s}
 .support-row:hover{border-color:var(--green)}.support-row h3{margin:8px 0;overflow-wrap:anywhere}.request-meta{display:flex;flex-wrap:wrap;gap:10px 18px;color:var(--muted);font-size:13px}.support-status{display:inline-block;border-radius:24px;background:#edf3ff;padding:5px 12px;font-size:12px;font-weight:600;color:#234ea4}.support-status.resolved{background:#e8f6ee;color:#176344}.support-status.open{background:#fff0d7;color:#795320}
 .support-thread{max-width:820px;margin:auto}.request-reference{font-size:12px;overflow-wrap:anywhere;color:var(--muted)}.thread-messages{display:grid;gap:14px;margin:22px 0}.thread-message{padding:22px;border:1px solid var(--line);border-radius:16px;background:#fff}.thread-message.staff{border-left:4px solid var(--green);background:#f0f5ff}.thread-message.system{background:transparent;padding:12px 18px;border-style:dashed}.message-body{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75;margin-bottom:0}.message-heading{display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;font-size:13px}.message-heading time{color:var(--muted)}.support-action{display:flex;align-items:end;flex-wrap:wrap;gap:12px}.support-action label{flex:1;min-width:160px}.support-note{font-size:13px;color:var(--muted);line-height:1.7}.request-details{display:flex;flex-wrap:wrap;gap:12px;margin:18px 0}.support-wrap .filter-bar{flex-wrap:wrap}.support-wrap .filter-bar label{min-width:140px}.support-wrap .panel{margin-bottom:20px}
 @media(max-width:640px){.support-wrap{padding-top:24px}.support-head{flex-direction:column}.support-head h1{font-size:32px}.support-row,.thread-message{padding:17px}.support-head .button{width:100%}.support-action .button{width:100%}.support-wrap .filter-bar label{min-width:0;width:100%}}
`],template:`
<div class="container support-wrap">
 <div class="support-head"><div><span class="eyebrow">{{admin?'HERE TO MAKE THINGS RIGHT':'A LITTLE HELP, WHEN YOU NEED IT'}}</span><h1>{{admin?'Support inbox.':'We’re here to help.'}}</h1><p>{{admin?'Review passenger requests, reply with guidance and keep each conversation moving.':'Tell us about a booking, payment or account issue. Your conversation stays between you and the support team.'}}</p></div>
 @if(!id()&&!admin){<button class="button primary" (click)="writing.set(!writing())"><rw-icon name="plus"/>{{writing()?'Close request form':'New support request'}}</button>}
 @if(!id()){<button class="button secondary small" [disabled]="loading()||busy()" (click)="load()"><rw-icon name="refresh"/> Refresh</button>}
 </div>
 <rw-notice [text]="error()"/><rw-notice [text]="success()" tone="success"/>
 @if(id()){
  <div class="support-thread"><a class="text-link" [routerLink]="root">← {{admin?'Support inbox':'My support requests'}}</a>
  @if(ticket();as t){
   <section class="panel"><div class="heading-row"><span class="support-status" [class.resolved]="t.status==='RESOLVED'" [class.open]="t.status==='OPEN'">{{pretty(t.status)}}</span><button class="text-link" [disabled]="loading()||busy()" (click)="load()">Refresh conversation</button></div>
   <h2>{{t.subject}}</h2><p class="request-reference">{{t.id}}</p><div class="request-meta"><span>{{category(t.category)}}</span><span>Opened {{t.createdAt|date:'d MMM yyyy, h:mm a'}}</span></div>
   @if(admin){<p class="support-note">{{t.username}} · {{t.email}}</p>}
   @if(t.pnr){<div class="request-details"><a class="text-link" [routerLink]="['/bookings',t.pnr]" [queryParams]="admin?{adminView:'1'}:{}">View booking · PNR {{t.pnr}} ↗</a></div>}
   @if(admin){<form class="support-action" (ngSubmit)="changeStatus()"><label>Status<select aria-label="Status" name="supportStatus" [(ngModel)]="nextStatus" [disabled]="busy()">@for(s of statuses;track s){<option [value]="s">{{pretty(s)}}</option>}</select></label><button class="button secondary" [disabled]="busy()||nextStatus===t.status">Update status</button></form>}
  </section>
  <div class="thread-messages" aria-label="Support conversation" [attr.aria-busy]="loading()">@for(m of chronological();track m.id){<article class="thread-message" [class.staff]="m.staff" [class.system]="m.systemMessage"><div class="message-heading"><strong>{{m.staff?'Railwize support':admin?m.author:'You'}}{{m.systemMessage?' · Status update':''}}</strong><time>{{m.createdAt|date:'d MMM yyyy, h:mm a'}}</time></div><p class="message-body">{{m.body}}</p></article>}</div>
  <rw-pager [page]="messagePage()" [pages]="conversation()?.totalPages||0" [total]="conversation()?.totalElements||0" noun="messages" [busy]="loading()||busy()" (change)="messagePage.set($event);load()"/>
  <p class="support-note">Page 1 contains the latest messages. Conversations refresh every 10 seconds while this tab is visible.</p>
  <form #replyForm="ngForm" class="panel stack-form" (ngSubmit)="reply()"><h3>{{t.status==='RESOLVED'?'Need to continue this conversation?':'Add a reply'}}</h3><label>Your reply<textarea name="supportReply" [(ngModel)]="replyText" required maxlength="3000" [disabled]="busy()" placeholder="Add the details that will help us understand the issue."></textarea></label><p class="support-note">{{t.status==='RESOLVED'?'Sending a reply reopens this request. ':''}}Never include passwords, OTPs or full card details. Replies here do not cancel bookings or process refunds.</p><button class="button primary" [disabled]="replyForm.invalid||!replyText.trim()||busy()">{{busy()?'Sending…':'Send reply'}} <rw-icon name="arrow"/></button></form>
  }@else if(loading()){<div class="loading-state">Loading your conversation…</div>}
  </div>
 }@else{
  @if(writing()&&!admin){<form #newForm="ngForm" class="panel stack-form request-form" (ngSubmit)="create()"><span class="eyebrow">LET’S SORT THIS OUT</span><h2>Tell us what happened.</h2><div class="form-grid"><label>Category<select name="category" [(ngModel)]="draft.category" [disabled]="busy()">@for(c of categories;track c.value){<option [value]="c.value">{{c.label}}</option>}</select></label><label>Booking PNR (optional)<input name="supportPnr" [(ngModel)]="draft.pnr" pattern="[0-9]{10}" maxlength="10" [disabled]="busy()" placeholder="10-digit PNR"/><small>Only a booking from your account can be linked.</small></label></div><label>Subject<input name="supportSubject" [(ngModel)]="draft.subject" required maxlength="160" [disabled]="busy()" placeholder="A short description of the issue"/></label><label>Message<textarea name="supportMessage" [(ngModel)]="draft.message" required maxlength="3000" [disabled]="busy()" placeholder="Explain what happened and what help you need."></textarea></label><p class="support-note">Do not include passwords, OTPs or full card details. You can follow replies here and receive updates at your verified email address. Attachments are not supported.</p><button class="button primary" [disabled]="newForm.invalid||!draft.subject.trim()||!draft.message.trim()||busy()">{{busy()?'Sending request…':'Send support request'}} <rw-icon name="arrow"/></button></form>}
  <form class="filter-bar" (ngSubmit)="apply()"><label>Search requests<input name="supportSearch" [(ngModel)]="filters.search" maxlength="160" [placeholder]="admin?'Subject, PNR, account or email':'Subject, PNR or reference'"/></label><label>Status<select name="filterStatus" [(ngModel)]="filters.status"><option value="">All statuses</option>@for(s of statuses;track s){<option [value]="s">{{pretty(s)}}</option>}</select></label><label>Category<select name="filterCategory" [(ngModel)]="filters.category"><option value="">All categories</option>@for(c of categories;track c.value){<option [value]="c.value">{{c.label}}</option>}</select></label><button class="button secondary" [disabled]="loading()||busy()">Apply filters</button></form>
  <div class="support-list" [attr.aria-busy]="loading()">@for(t of list()?.content||[];track t.id){<a class="support-row" [routerLink]="[root,t.id]"><div class="heading-row"><span class="support-status" [class.resolved]="t.status==='RESOLVED'" [class.open]="t.status==='OPEN'">{{pretty(t.status)}}</span><span class="small muted">{{t.updatedAt|date:'d MMM, h:mm a'}}</span></div><h3>{{t.subject}}</h3><div class="request-meta"><span>{{category(t.category)}}</span>@if(t.pnr){<span>PNR {{t.pnr}}</span>}@if(admin){<span>{{t.username}}</span>}</div><p class="request-reference">{{t.id}}</p></a>}@empty{<section class="empty-state"><rw-icon name="ticket"/><h2>{{loading()?'Loading requests…':'No support requests found.'}}</h2><p>{{admin?'Try another filter or check again for new requests.':'Start a request above whenever you need help, or adjust your filters.'}}</p></section>}</div>
  <rw-pager [page]="page()" [pages]="list()?.totalPages||0" [total]="list()?.totalElements||0" noun="requests" [busy]="loading()||busy()" (change)="page.set($event);load()"/>
  <p class="support-note">Requests are ordered by their latest update. <a routerLink="/help">Browse the Help centre ↗</a></p>
 }
</div>`})
export class SupportPage {
  private api=inject(Api);private auth=inject(Auth);private route=inject(ActivatedRoute);private router=inject(Router);private destroy=inject(DestroyRef);
  readonly admin=this.route.snapshot.data['adminSupport']===true;readonly root=this.admin?'/admin/support':'/support';
  private base=this.admin?'/reservations/admin/support':'/reservations/support';
  categories=SUPPORT_CATEGORIES;statuses=SUPPORT_STATUSES;pretty=pretty;
  id=signal('');ticket=signal<SupportTicket|null>(null);list=signal<Page<SupportTicket>|null>(null);conversation=signal<Page<SupportMessage>|null>(null);
  page=signal(0);messagePage=signal(0);writing=signal(false);busy=signal(false);loading=signal(false);error=signal('');success=signal('');
  draft={category:'BOOKING',subject:'',message:'',pnr:''};replyText='';nextStatus='OPEN';filters={status:'',category:'',search:''};private applied={...this.filters};
  private createReference=new SupportSubmission();private replyReference=new SupportSubmission();private sequence=0;private epoch=0;private owner='';
  constructor(){
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(params=>{this.epoch++;this.sequence++;this.id.set(params.get('id')||'');this.ticket.set(null);this.conversation.set(null);this.replyText='';this.replyReference.clear();this.messagePage.set(0);this.error.set('');this.success.set('');this.busy.set(false);void this.load()});
    const pnr=this.route.snapshot.queryParamMap.get('pnr');if(pnr&&/^\d{10}$/.test(pnr)){this.draft.pnr=pnr;this.writing.set(true)}
    effect(()=>{const owner=this.auth.session()?.username||'';untracked(()=>{if(this.owner===owner)return;const previous=this.owner;this.owner=owner;this.epoch++;this.sequence++;this.ticket.set(null);this.list.set(null);this.conversation.set(null);this.replyText='';this.createReference.clear();this.replyReference.clear();this.busy.set(false);this.error.set('');this.success.set('');if(previous){this.draft={category:'BOOKING',subject:'',message:'',pnr:''};this.writing.set(false)}if(owner)void this.load()})});
    const timer=setInterval(()=>{if(!document.hidden&&!this.busy()&&!this.loading()&&this.auth.signedIn())void this.load(true)},10000);
    this.destroy.onDestroy(()=>{clearInterval(timer);this.epoch++;this.sequence++});
  }
  category(value:string){return this.categories.find(c=>c.value===value)?.label||value}
  chronological(){return [...(this.conversation()?.content||[])].reverse()}
  apply(){this.applied={...this.filters};this.page.set(0);void this.load()}
  async load(silent=false){
    if(!this.auth.signedIn())return;
    const request=++this.sequence,epoch=this.epoch;this.loading.set(true);
    try{if(this.id()){
      const [ticket,messages]=await Promise.all([this.api.get<SupportTicket>(this.base+'/'+encodeURIComponent(this.id())),this.api.get<Page<SupportMessage>>(this.base+'/'+encodeURIComponent(this.id())+'/messages',{page:this.messagePage(),size:20})]);
      if(request!==this.sequence||epoch!==this.epoch)return;const changed=this.ticket()?.version!==ticket.version;this.ticket.set(ticket);this.conversation.set(messages);if(changed)this.nextStatus=ticket.status;
    }else{const list=await this.api.get<Page<SupportTicket>>(this.base,{...this.applied,page:this.page(),size:20});if(request!==this.sequence||epoch!==this.epoch)return;this.list.set(list)}
    this.error.set('');
    }catch(e){if(request===this.sequence&&epoch===this.epoch){if(!silent){this.ticket.set(null);this.conversation.set(null)}this.error.set(messageOf(e))}}finally{if(request===this.sequence)this.loading.set(false)}
  }
  async create(){
    if(this.admin||this.busy()||!this.draft.subject.trim()||!this.draft.message.trim())return;
    const body={category:this.draft.category,subject:this.draft.subject.trim(),pnr:this.draft.pnr.trim()||null,message:this.draft.message.trim()};
    const epoch=this.epoch;this.busy.set(true);this.error.set('');
    try{const ticket=await this.api.post<SupportTicket>(this.base,{...body,requestId:this.createReference.reference(body)});if(epoch!==this.epoch)return;this.draft={category:'BOOKING',subject:'',message:'',pnr:''};this.createReference.clear();await this.router.navigate([this.root,ticket.id])}
    catch(e){if(epoch===this.epoch)this.error.set(messageOf(e))}finally{if(epoch===this.epoch)this.busy.set(false)}
  }
  async reply(){
    if(this.busy()||!this.ticket()||!this.replyText.trim())return;
    const epoch=this.epoch,body={message:this.replyText.trim()},id=this.id();this.busy.set(true);this.error.set('');
    try{await this.api.post<SupportTicket>(this.base+'/'+encodeURIComponent(id)+'/messages',{...body,requestId:this.replyReference.reference(body)});if(epoch!==this.epoch)return;this.replyText='';this.replyReference.clear();this.messagePage.set(0);this.success.set('Your reply has been sent.');await this.load()}
    catch(e){if(epoch===this.epoch)this.error.set(messageOf(e))}finally{if(epoch===this.epoch)this.busy.set(false)}
  }
  async changeStatus(){
    const ticket=this.ticket();if(!this.admin||!ticket||this.busy()||ticket.status===this.nextStatus)return;
    const epoch=this.epoch;this.busy.set(true);this.error.set('');
    try{await this.api.put<SupportTicket>(this.base+'/'+encodeURIComponent(ticket.id)+'/status',{status:this.nextStatus,version:ticket.version});if(epoch!==this.epoch)return;this.success.set('Request status updated.');this.messagePage.set(0);await this.load()}
    catch(e){if(epoch===this.epoch)this.error.set(messageOf(e))}finally{if(epoch===this.epoch)this.busy.set(false)}
  }
}
