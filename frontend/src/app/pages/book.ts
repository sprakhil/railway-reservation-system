import {journeySearch} from '../core/journey-link';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api, Auth, Catalogue } from '../core/api';
import { Booking, Page, Passenger, PREFERENCES, Quote, SavedPassenger, Search } from '../core/models';
import { messageOf, pretty, searchError } from '../core/utils';
import { Icon, Notice, Status } from '../shared/ui';
@Component({selector:'rw-book',imports:[FormsModule,CurrencyPipe,DatePipe,RouterLink,Icon,Notice,Status],template:`<section class="page-header"><div class="container"><a routerLink="/journeys" [queryParams]="plannedJourney||query" class="back-link"><rw-icon name="back"/> {{plannedJourney?'Back to journey options':'Back to trains'}}</a><span class="eyebrow">YOU'RE GOING PLACES</span><h1>Who’s coming along?</h1><p>A few details, then you’re ready for checkout.</p></div></section><div class="container page-content"><rw-notice [text]="error()"/>
@if(loading()){<div class="loading-state"><span class="spinner"></span> Checking your journey and fare…</div>}@else if(quote();as q){<div class="booking-layout"><div><div class="step-strip"><span class="done"><i>✓</i> Choose train</span><span class="current"><i>2</i> Passenger details</span><span><i>3</i> Checkout</span></div><rw-notice tone="info" [text]="replay()?'A previous booking request has an uncertain result. Details are locked so you can safely retry the same request without creating another booking.':''"/>
<section class="panel"><span class="eyebrow">YOUR TRAVELLING PARTY</span><h2>How many passengers?</h2><label>Passengers<select aria-label="Number of passengers" [ngModel]="passengerCount" (ngModelChange)="changeCount($event)" [disabled]="busy()||replay()"><option [ngValue]="0" disabled>Choose passenger count</option>@for(n of [1,2,3,4,5,6];track n){<option [ngValue]="n">{{n}} {{n===1?'passenger':'passengers'}}</option>}</select></label><p class="small muted">Choose 1–6 passengers. The fare and availability below apply to everyone in this booking.</p>@if(quoteBusy()){<p role="status">Updating fare and group availability…</p>}<rw-notice [text]="quoteError()"/>@if(passengerCount>0&&!replay()){<button type="button" class="text-link" [disabled]="busy()||quoteBusy()" (click)="refreshQuote()">Refresh fare & availability</button>}</section>
<form #f="ngForm" (ngSubmit)="book()"><fieldset [disabled]="busy()||replay()||quoteBusy()" class="bare-fieldset">@for(p of passengers;track $index;let i=$index){<section class="panel"><div class="heading-row passenger-heading"><h3><span class="number-bubble">{{i+1}}</span> Passenger {{i+1}}</h3><label>Choose a saved passenger<select aria-label="Choose saved passenger" [name]="'saved'+i" [ngModel]="selectedSaved[i]||''" (ngModelChange)="useSaved(i,$event)" [disabled]="savedLoading()||!saved().length"><option value="">Enter details manually</option>@for(s of saved();track s.id){<option [value]="s.id" [disabled]="savedUsed(s.id,i)">{{s.name}} · {{s.age}} years{{savedUsed(s.id,i)?' · Already selected':''}}</option>}</select></label></div><div class="form-grid"><label class="span-two">Full name<input [name]="'name'+i" [(ngModel)]="p.name" required maxlength="100" placeholder="As you’d like it on your ticket"/></label><label>Age<input type="number" [name]="'age'+i" [(ngModel)]="p.age" required min="1" max="120" step="1"/></label><label>Gender<select [name]="'gender'+i" [(ngModel)]="p.gender" required><option value="">Select gender</option><option>MALE</option><option>FEMALE</option><option>OTHER</option></select></label><label class="span-two">Berth preference<select [name]="'berth'+i" [(ngModel)]="p.birthPreference">@for(b of preferences;track b){<option [value]="b">{{b==='NONE'?'No preference':pretty(b)}}</option>}</select><small>We record your preference, but cannot guarantee the berth type.</small></label></div></section>}</fieldset>
<div class="subtle-help" aria-live="polite">@if(savedLoading()){<p>Loading your saved passengers…</p>}@else if(savedError()){<p>{{savedError()}}</p><button type="button" class="button secondary small" [disabled]="busy()||replay()" (click)="loadSaved()">Retry saved passengers</button>}@else if(!saved().length){<p>No saved passengers yet. Enter details above or <a routerLink="/passengers" target="_blank" rel="noopener">manage your saved passengers</a>.</p><button type="button" class="text-link" [disabled]="busy()||replay()" (click)="loadSaved()">Refresh saved passengers</button>}@else{<p>Choose from your saved passengers above, then review their details. Edits here apply only to this booking.</p>}</div><div class="panel booking-consent"><label class="checkbox"><input type="checkbox" name="agreed" [(ngModel)]="agreed" required/><span>I agree to the booking and cancellation policy. All passengers will be confirmed or waitlisted together. A paid WL ticket is automatically promoted, or fully refunded if unconfirmed at the cutoff.</span></label><button class="button primary full" [disabled]="(!replay()&&(f.invalid||!ready()))||!agreed||busy()">{{busy()?'Creating your booking…':replay()?'Retry the same booking':'Continue to checkout'}}<rw-icon name="arrow"/></button><small class="text-center">Your two-minute payment hold starts on the next step.</small></div></form></div>
<aside class="fare-summary panel"><span class="eyebrow">YOUR JOURNEY</span><h2>{{q.trainName}}</h2><p class="muted">#{{q.trainNumber}} · {{q.seatClass.replace('CLASS_','')}}</p><div class="summary-route"><div><span class="route-dot filled"></span><small>{{q.departureAt|date:'d MMM, HH:mm':'+0530'}}</small><strong>{{catalogue.name(q.fromStation)}}</strong></div><div><span class="route-dot"></span><small>{{q.arrivalAt|date:'d MMM, HH:mm':'+0530'}}</small><strong>{{catalogue.name(q.toStation)}}</strong></div></div>@if(passengerCount>0&&!quoteBusy()&&!quoteError()){<rw-status [value]="q.suggestedAllocation"/>}<p class="small muted">{{!passengerCount?'Choose your passenger count to check group availability.':quoteBusy()?'Checking group availability…':quoteError()?'Refresh your quote before continuing.':q.suggestedAllocation==='WL'?'Payment is required for waitlist too. Your entire booking must fit before promotion.':q.suggestedAllocation==='UNAVAILABLE'?'This group cannot currently be booked. Try fewer passengers or another class.':'Availability is checked again when your booking is created.'}}</p><hr><div class="fare-row"><span>Fare per passenger</span><strong>{{q.farePerPassenger|currency:'INR'}}</strong></div><div class="fare-row"><span>Passengers</span><strong>{{passengers.length}}</strong></div><div class="fare-row"><span>Distance</span><span>{{q.distanceKm}} km</span></div><div class="fare-row total"><span>Total fare</span><strong>{{passengerCount>0&&!quoteBusy()&&!quoteError()?(q.totalFare|currency:'INR'):'—'}}</strong></div><span class="soft-label"><rw-icon name="shield"/> Clear fares, from search to checkout</span><a routerLink="/help" class="text-link">Booking & cancellation policy ↗</a></aside></div>}@else{<div class="empty-state"><h2>Let’s choose a journey first.</h2><a routerLink="/journeys" class="button primary">Find a train</a></div>}</div>`})
export class Book {api=inject(Api);auth=inject(Auth);catalogue=inject(Catalogue);route=inject(ActivatedRoute);router=inject(Router);query:Search={fromStation:'',toStation:'',dateOfJourney:'',seatClass:'SL',passengers:1};quote=signal<Quote|null>(null);loading=signal(true);busy=signal(false);error=signal('');savedError=signal('');saved=signal<SavedPassenger[]>([]);savedLoading=signal(false);selectedSaved:(number|null)[]=[];passengers:Passenger[]=[];preferences=PREFERENCES;pretty=pretty;agreed=false;replay=signal(false);private attempt:{key:string;body:unknown;username:string}|null=null;
passengerCount=0;quoteBusy=signal(false);quoteError=signal('');private quoteGeneration=0;
plannedJourney=journeySearch(this.route.snapshot.queryParamMap.get('journey'));
constructor(){const p=this.route.snapshot.queryParamMap;this.query={fromStation:p.get('fromStation')||'',toStation:p.get('toStation')||'',dateOfJourney:p.get('dateOfJourney')||'',seatClass:p.get('seatClass')||'SL',passengers:1};void this.load()}
async load(){
 try{
  const e=searchError(this.query);if(e)throw new Error(e);
  this.quote.set(await this.api.get<Quote>('/reservations/quote',{...this.query,trainNumber:this.route.snapshot.paramMap.get('trainNumber')}));
  try{const stored=JSON.parse(sessionStorage.getItem('railwize.pending-booking')||'null');
   if(stored?.username===this.auth.session()?.username&&stored?.body?.trainNumber===this.quote()!.trainNumber&&stored.body.dateOfJourney===this.query.dateOfJourney&&stored.body.fromStation===this.query.fromStation&&stored.body.toStation===this.query.toStation&&stored.body.seatClass===this.query.seatClass&&Array.isArray(stored.body.passengers)&&stored.body.passengers.length>=1&&stored.body.passengers.length<=6){
    this.attempt=stored;this.passengers=stored.body.passengers;this.passengerCount=this.passengers.length;this.query.passengers=this.passengerCount;this.replay.set(true);await this.refreshQuote();
   }
  }catch{sessionStorage.removeItem('railwize.pending-booking')}
  void this.loadSaved();void this.catalogue.load().catch(()=>{});
 }catch(e){this.error.set(messageOf(e))}finally{this.loading.set(false)}
}
ready(){return this.passengerCount>0&&!this.quoteBusy()&&!this.quoteError()&&this.quote()?.passengerCount===this.passengers.length&&this.quote()?.suggestedAllocation!=='UNAVAILABLE'}
async changeCount(value:number){
 if(this.busy()||this.replay())return;
 const count=Number(value);if(!Number.isInteger(count)||count<1||count>6)return;
 this.passengerCount=count;this.query.passengers=count;this.agreed=false;
 this.passengers=Array.from({length:count},(_,i)=>this.passengers[i]||{name:'',age:null,gender:'',birthPreference:'NONE'});
 this.selectedSaved=Array.from({length:count},(_,i)=>this.selectedSaved[i]||null);
 await this.refreshQuote();
}
async refreshQuote(){
 if(!this.passengerCount||this.busy())return;
 const generation=++this.quoteGeneration;this.quoteBusy.set(true);this.quoteError.set('');
 try{const quote=await this.api.get<Quote>('/reservations/quote',{...this.query,passengers:this.passengerCount,trainNumber:this.route.snapshot.paramMap.get('trainNumber')});if(generation===this.quoteGeneration)this.quote.set(quote)}
 catch(e){if(generation===this.quoteGeneration)this.quoteError.set(messageOf(e))}
 finally{if(generation===this.quoteGeneration)this.quoteBusy.set(false)}
}
async loadSaved(){
 if(this.savedLoading())return;
 this.savedLoading.set(true);this.savedError.set('');
 try{let page=0;const all:SavedPassenger[]=[];while(true){const p=await this.api.get<Page<SavedPassenger>>('/users/me/passengers',{page,size:100});all.push(...p.content);if(++page>=p.totalPages)break}this.saved.set(all)}
 catch(e){this.savedError.set('Saved passengers could not load. You can still enter details manually. '+messageOf(e))}
 finally{this.savedLoading.set(false)}
}
savedUsed(id:number,index:number){return this.selectedSaved.some((selected,i)=>i!==index&&selected===id)}
useSaved(i:number,value:string|number){
 if(this.busy()||this.replay())return;
 const id=Number(value);
 if(!id){this.selectedSaved[i]=null;return}
 const p=this.saved().find(s=>s.id===id);
 if(!p||this.savedUsed(id,i))return;
 this.selectedSaved[i]=id;
 this.passengers[i]={name:p.name,age:p.age,gender:p.gender,birthPreference:p.berthPreference||'NONE'};
}
async book(){if(!this.attempt&&!this.ready()){this.error.set('Choose passengers and refresh the fare before continuing.');return}this.busy.set(true);this.error.set('');try{if(!this.attempt){if(this.passengers.some(p=>!p.name.trim()||!Number.isInteger(p.age)||p.age!<1||p.age!>120||!p.gender))throw new Error('Please check each passenger’s name, age and gender.');this.attempt={key:crypto.randomUUID(),username:this.auth.session()!.username,body:{trainNumber:this.quote()!.trainNumber,fromStation:this.query.fromStation,toStation:this.query.toStation,seatClass:this.query.seatClass,dateOfJourney:this.query.dateOfJourney,expectedTotalFare:this.quote()!.totalFare,passengers:this.passengers.map(p=>({...p,name:p.name.trim()}))}};sessionStorage.setItem('railwize.pending-booking',JSON.stringify(this.attempt))}const b=await this.api.post<Booking>('/reservations/book',this.attempt.body,{'Idempotency-Key':this.attempt.key});sessionStorage.removeItem('railwize.pending-booking');await this.router.navigate(['/bookings',b.pnr],{queryParams:this.plannedJourney?{journey:JSON.stringify(this.plannedJourney)}:{}})}catch(e){const status=(e as {status?:number}).status;if(status&&status>=400&&status<500){this.attempt=null;sessionStorage.removeItem('railwize.pending-booking');this.replay.set(false)}else if(this.attempt)this.replay.set(true);this.error.set(messageOf(e));}finally{this.busy.set(false)}}}
