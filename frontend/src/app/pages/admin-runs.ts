import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Page } from '../core/models';
import { Icon, Notice, Pager } from '../shared/ui';
import { messageOf, tomorrow } from '../core/utils';

interface RunView {
 trainNumber:string; runDate:string; sourceDepartureAt:string; cancelled:boolean;
 reason:string|null; cancelledBy:string|null; cancelledAt:string|null;
 bookings:number; passengers:number; confirmedBookings:number; waitingBookings:number; unpaidBookings:number;
 refundAmount:number; processedRefunds:number; outstandingRefunds:number;
}
interface CancelledRun {trainNumber:string;runDate:string;reason:string;cancelledBy:string;cancelledAt:string}

@Component({selector:'rw-admin-runs',imports:[FormsModule,CurrencyPipe,DatePipe,RouterLink,Icon,Notice,Pager],template:`
<div class="admin-title"><span class="eyebrow">SERVICE UPDATES</span><h1>Train run cancellations</h1><p>Cancel one dated service while preserving tickets and every other run.</p></div>
<rw-notice [text]="error()"/>
<section class="panel"><h2>Choose a run</h2><p class="muted">Use the date this train departs its first station, in India time. This includes passengers boarding later in an overnight journey. Only runs that have not started can be cancelled.</p>
<form #form="ngForm" (ngSubmit)="review()"><fieldset class="bare-fieldset" [disabled]="busy()"><div class="form-grid">
<label>Train number<input name="train" [(ngModel)]="train" (ngModelChange)="clearReview()" pattern="[A-Za-z0-9]{1,10}" required maxlength="10" placeholder="e.g. QA101"/></label>
<label>Original departure date<input name="date" type="date" [(ngModel)]="date" (ngModelChange)="clearReview()" required/></label></div>
<button class="button secondary" [disabled]="form.invalid||busy()">{{busy()?'Please wait…':'Review affected bookings'}}</button></fieldset></form></section>
@if(view();as v){<section class="panel" aria-live="polite"><div class="heading-row"><div><span class="eyebrow">{{v.trainNumber}} · {{v.runDate}}</span><h2>{{v.cancelled?'Run cancelled':'Review cancellation'}}</h2></div><button class="button secondary small" [disabled]="busy()" (click)="review()">Refresh</button></div>
<p>Source departure: {{v.sourceDepartureAt|date:'d MMM yyyy, HH:mm':'+0530'}} IST</p>
<div class="payment-grid"><div><small>Affected bookings / passengers</small><strong>{{v.bookings}} / {{v.passengers}}</strong></div><div><small>CNF / WL / unpaid</small><strong>{{v.confirmedBookings}} / {{v.waitingBookings}} / {{v.unpaidBookings}}</strong></div><div><small>Full refund total</small><strong>{{v.refundAmount|currency:'INR'}}</strong></div></div>
@if(v.cancelled){<div class="notice"><rw-icon name="info"/><span><strong>Cancelled by {{v.cancelledBy}}</strong> · {{v.cancelledAt|date:'d MMM yyyy, HH:mm':'+0530'}} IST<br/>{{v.reason}}</span></div><p>Processed: <strong>{{v.processedRefunds|currency:'INR'}}</strong> · Outstanding: <strong>{{v.outstandingRefunds|currency:'INR'}}</strong></p><p class="subtle-help">Outstanding includes provider refunds still pending or needing retry. Late captures can increase the refund total. Refund completion is separate from cancelling the run.</p><a routerLink="/admin/bookings" class="text-link">Open booking management ↗</a>}
@else{<form #cancelForm="ngForm" (ngSubmit)="cancel()"><fieldset class="bare-fieldset" [disabled]="busy()"><label>Reason shown to passengers<textarea name="reason" [(ngModel)]="reason" required minlength="5" maxlength="500" rows="3" placeholder="Explain why this train will not run"></textarea></label><p class="subtle-help">All active CNF, WL and unpaid bookings on this run will be cancelled. Captured ticket fares receive full test refunds with zero deduction. Previously cancelled bookings and independent onward tickets are unchanged. There is no undo.</p><label class="check-label"><input type="checkbox" name="ack" [(ngModel)]="ack" required/> I have checked the train, original run date and cancellation reason.</label><button class="button danger" [disabled]="cancelForm.invalid||!ack||busy()">{{busy()?'Cancelling…':'Cancel this run & request full refunds'}}</button></fieldset></form>}
</section>}
<section class="panel"><div class="heading-row"><h2>Cancellation history</h2><button class="button secondary small" [disabled]="busy()" (click)="loadHistory()">Refresh history</button></div><rw-notice [text]="historyError()"/><p class="subtle-help">Open a run to inspect its reason and current refund progress. Records are retained for audit.</p>
@if(history();as h){<div class="table-scroll"><table><thead><tr><th>Train / run date</th><th>Reason</th><th>Administrator</th><th></th></tr></thead><tbody>@for(r of h.content;track r.trainNumber+'|'+r.runDate){<tr><td><strong>{{r.trainNumber}}</strong><br/>{{r.runDate}}</td><td>{{r.reason}}</td><td>{{r.cancelledBy}}</td><td><button class="text-link" [disabled]="busy()" (click)="open(r)">Details</button></td></tr>}@empty{<tr><td colspan="4">No cancelled runs yet.</td></tr>}</tbody></table></div><rw-pager [page]="h.page" [pages]="h.totalPages" [total]="h.totalElements" (change)="changePage($event)"/>}</section>
`})
export class AdminRuns {
 private api=inject(Api);train='';date=tomorrow();reason='';ack=false;busy=signal(false);error=signal('');historyError=signal('');view=signal<RunView|null>(null);history=signal<Page<CancelledRun>|null>(null);page=0;
 constructor(){void this.loadHistory()}
 clearReview(){this.view.set(null);this.ack=false;this.error.set('')}
 private path(train=this.train.trim().toUpperCase(),date=this.date){return '/reservations/admin/train-runs/'+encodeURIComponent(train)+'/'+encodeURIComponent(date)}
 async review(){this.busy.set(true);this.error.set('');this.view.set(null);this.ack=false;try{this.view.set(await this.api.get<RunView>(this.path()))}catch(e){this.error.set(messageOf(e))}finally{this.busy.set(false)}}
 async cancel(){const v=this.view();if(!v||v.cancelled||!this.ack||this.busy())return;this.busy.set(true);this.error.set('');try{this.view.set(await this.api.post<RunView>(this.path(v.trainNumber,v.runDate)+'/cancel',{reason:this.reason.trim()}));this.ack=false;await this.loadHistory()}catch(e){this.error.set(messageOf(e)+' Refresh this run to check its state before retrying.')}finally{this.busy.set(false)}}
 async loadHistory(){this.historyError.set('');try{this.history.set(await this.api.get<Page<CancelledRun>>('/reservations/admin/train-runs',{page:this.page,size:10}))}catch(e){this.historyError.set(messageOf(e))}}
 changePage(page:number){this.page=page;void this.loadHistory()}
 open(r:CancelledRun){this.train=r.trainNumber;this.date=r.runDate;void this.review()}
}
