import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Api,Catalogue} from '../core/api';
import {JourneyAlternativesPanel} from '../shared/journey-alternatives';
import {CLASSES,Quote,RouteSearch,TrainSearchResult} from '../core/models';
import {messageOf,routeSearchError,duration,tomorrow} from '../core/utils';
import {SearchForm} from '../shared/search-form';
import {Icon,Notice} from '../shared/ui';
@Component({selector:'rw-journeys',imports:[JourneyAlternativesPanel,SearchForm,Icon,Notice,CurrencyPipe,DatePipe,RouterLink],styles:[`
.class-options{display:grid;grid-template-columns:repeat(auto-fit,minmax(185px,1fr));gap:12px;padding:0 24px 24px}.class-option{display:flex;flex-direction:column;gap:9px;border:1px solid #d8e3da;background:#f7faf3;border-radius:14px;padding:17px;min-width:0}.class-option.waitlisted{background:#fffaf0;border-color:#eadbb8}.class-option.closed{background:#f3f3f0;color:#6d746e}.class-option h4{margin:0;font-size:14px}.class-fare{font-size:24px;line-height:1.4}.class-option small{font-size:12px;color:#63746a;line-height:1.5}.class-option .button{margin-top:auto;font-size:12px;padding:10px}.class-stock{font-size:13px;font-weight:600;color:#24543f}.waitlisted .class-stock{color:#886319}.train-note{padding:0 24px 18px;font-size:12px;color:#6a776e}.results-refresh{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
@media(max-width:520px){.class-options{grid-template-columns:repeat(2,minmax(0,1fr));padding:0 16px 16px;gap:8px}.class-option{padding:12px}.class-fare{font-size:20px}.class-option h4{font-size:12px}.train-note{padding:0 16px 16px}}
`],template:`
<section class="page-header"><div class="container"><span class="eyebrow">YOUR NEXT CHAPTER</span><h1>Find your way there.</h1><p>Choose your route. Compare the classes. Make it your journey.</p></div></section>
<div class="container page-content"><div class="search-card compact"><rw-search-form [initial]="query()"/></div><rw-notice [text]="error()"/>
@if(loading()){<div class="loading-state" role="status"><span class="spinner"></span> Finding trains, fares and available seats…</div>}
@else if(searched()){
<div class="results-heading"><div><h2>{{catalogue.name(query().fromStation)}} <span class="muted">→</span> {{catalogue.name(query().toStation)}}</h2><p>{{query().dateOfJourney|date:'EEE, d MMM yyyy'}} · {{trains().length}} {{trains().length===1?'train':'trains'}} · All classes</p></div><div class="results-refresh"><span class="soft-label">Fares per passenger</span><button class="text-link" (click)="load()">Refresh availability</button></div></div>
<div class="results-layout" [class.no-direct-results]="!trains().length"><aside class="travel-aside"><span class="eyebrow">GOOD TO KNOW</span><h3>Your journey,<br>your choice.</h3><div><rw-icon name="ticket"/><p><strong>Compare every class</strong>Choose a class below, then enter your passenger count and details.</p></div><div><rw-icon name="users"/><p><strong>Up to six passengers</strong>Your total fare and group availability are checked on the booking page.</p></div><div><rw-icon name="clock"/><p><strong>No hold during search</strong>Your two-minute payment hold begins after you create a booking.</p></div><a routerLink="/help" class="text-link">Read the booking guide ↗</a></aside>
<div class="train-results">@for(train of trains();track train.trainNumber){@if(train.classes[0];as q){
<article class="train-card"><div class="train-top"><div class="train-title"><span class="train-icon"><rw-icon name="train"/></span><div><small>#{{train.trainNumber}}</small><h3>{{train.trainName}}</h3></div></div><span class="soft-label">{{train.classes.length}} {{train.classes.length===1?'class':'classes'}}</span></div>
<div class="train-journey"><div><strong>{{q.departureAt|date:'HH:mm':'+0530'}}</strong><span>{{q.fromStation}}</span><small>{{q.departureAt|date:'d MMM':'+0530'}}</small></div><div class="journey-line"><small>{{duration(q.departureAt,q.arrivalAt)}}</small><div><i></i><span></span><rw-icon name="train"/><span></span><i></i></div><small>{{q.distanceKm}} km · Direct train</small></div><div class="text-right"><strong>{{q.arrivalAt|date:'HH:mm':'+0530'}}</strong><span>{{q.toStation}}</span><small>{{q.arrivalAt|date:'d MMM':'+0530'}}</small></div></div>
<div class="class-options">@for(c of train.classes;track c.seatClass){<section class="class-option" [class.waitlisted]="c.suggestedAllocation==='WL'" [class.closed]="c.suggestedAllocation==='UNAVAILABLE'" [attr.aria-label]="className(c.seatClass)+' on '+train.trainNumber"><h4>{{className(c.seatClass)}}</h4><strong class="class-fare">{{c.farePerPassenger|currency:'INR':'symbol':'1.2-2'}}</strong><small>per passenger</small>
<span class="class-stock">{{c.suggestedAllocation==='CNF'?c.availableSeats+' seats available':c.suggestedAllocation==='WL'?'Waitlist available':'Booking closed'}}</span>
@if(c.suggestedAllocation==='WL'){<small>{{c.availableSeats}} unallocated seats · existing waitlist has priority. Full refund if unconfirmed at cutoff.</small>}
@if(c.suggestedAllocation==='UNAVAILABLE'){<small>No confirmable seat; the waitlist cutoff has passed.</small><button class="button secondary" disabled>Unavailable</button>}@else{<a class="button" [class.primary]="c.suggestedAllocation==='CNF'" [class.secondary]="c.suggestedAllocation==='WL'" [routerLink]="['/book',c.trainNumber]" [queryParams]="bookingQuery(c)">{{c.suggestedAllocation==='WL'?'Choose waitlist':'Choose '+c.seatClass.replace('CLASS_','')}} <rw-icon name="arrow"/></a>}
</section>}</div><p class="train-note">Availability can change. Your passenger count, final fare and confirmation status are checked before checkout.</p></article>
}}@empty{<div class="empty-state"><rw-icon name="train"/><h3>No direct trains found.</h3><p>Try another date or explore connecting and nearby-station options below.</p></div>}</div></div>
<rw-journey-alternatives [query]="query()" [automatic]="needsAlternatives()"/>
}@else{<div class="empty-state spacious"><rw-icon name="route"/><h2>Where will the tracks take you?</h2><p>Choose two stations and a travel date to see trains across every available class.</p></div>}</div>`})
export class Journeys {
 api=inject(Api);catalogue=inject(Catalogue);route=inject(ActivatedRoute);
 query=signal<RouteSearch>({fromStation:'',toStation:'',dateOfJourney:tomorrow()});trains=signal<TrainSearchResult[]>([]);
 error=signal('');loading=signal(false);searched=signal(false);duration=duration;private generation=0;
 constructor(){this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(p=>{this.query.set({fromStation:p.get('fromStation')||'',toStation:p.get('toStation')||'',dateOfJourney:p.get('dateOfJourney')||tomorrow()});void this.load()})}
 className(value:string){return CLASSES.find(c=>c.value===value)?.label||value}
 bookingQuery(q:Quote){return {fromStation:q.fromStation,toStation:q.toStation,dateOfJourney:q.boardingDate,seatClass:q.seatClass}}
 needsAlternatives(){return !this.trains().some(t=>t.classes.some(c=>c.suggestedAllocation==='CNF'))}
 async load(){const generation=++this.generation;this.error.set('');this.searched.set(false);this.loading.set(false);this.trains.set([]);if(!this.query().fromStation&&!this.query().toStation)return;const error=routeSearchError(this.query());if(error){this.error.set(error);return}this.loading.set(true);try{const results=await this.api.get<TrainSearchResult[]>('/reservations/trains',{...this.query()});if(generation===this.generation){this.trains.set(results);this.searched.set(true)}}catch(e){if(generation===this.generation)this.error.set(messageOf(e))}finally{if(generation===this.generation)this.loading.set(false)}}
}
