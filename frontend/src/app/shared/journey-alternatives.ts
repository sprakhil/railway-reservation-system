import {Component,effect,inject,input,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {Api,Catalogue} from '../core/api';
import {JourneyAlternatives,Quote,Search} from '../core/models';
import {messageOf,duration} from '../core/utils';
import {Icon,Notice} from './ui';

@Component({selector:'rw-journey-alternatives',imports:[FormsModule,CurrencyPipe,DatePipe,RouterLink,Icon,Notice],template:`
<section class="alternative-section" aria-label="Connecting and nearby journeys">
  <div class="alternative-heading"><div><span class="eyebrow">MORE WAYS TO GET THERE</span><h2>A change of train. A new possibility.</h2><p>Explore one-interchange journeys and stations near either end of your route.</p></div><span class="feature-icon mint"><rw-icon name="route"/></span></div>
  <form class="alternative-controls" (ngSubmit)="load()">
    <label class="nearby-toggle"><input type="checkbox" name="nearby" [(ngModel)]="nearby" [disabled]="loading()"/> Include nearby stations</label>
    <label>Nearby radius<select name="radius" [(ngModel)]="radius" [disabled]="!nearby||loading()"><option [ngValue]="25">25 km</option><option [ngValue]="50">50 km</option><option [ngValue]="100">100 km</option></select></label>
    <label>Minimum transfer<select name="buffer" [(ngModel)]="buffer" [disabled]="loading()"><option [ngValue]="60">1 hour</option><option [ngValue]="90">1½ hours</option><option [ngValue]="120">2 hours</option></select></label>
    <button class="button secondary" [disabled]="loading()">{{loading()?'Finding options…':data()?'Refresh alternatives':'Find alternatives'}} <rw-icon name="arrow"/></button>
  </form>
  <p class="alternative-guidance">Transfers use published timetables at the same station, with up to 6 hours between trains. Delays are not predicted. Nearby distances are straight-line estimates; allow extra time and budget for local travel.</p>
  <rw-notice [text]="error()"/>
  @if(loading()){<div class="loading-state" role="status"><span class="spinner"></span> Matching stations, connections and available seats…</div>}
  @if(data();as results){
    @for(note of results.notices;track note){<rw-notice [text]="note" tone="info"/>}
    <div class="alternative-count"><strong>{{results.options.length}} {{results.options.length===1?'alternative':'alternatives'}}</strong><span>Seats-available options first, then earliest arrival · {{results.minTransferMinutes}}–{{results.maxTransferMinutes}} min transfer</span></div>
    @for(plan of results.options;track plan.id){
      <article class="alternative-card">
        <div class="alternative-top"><span class="soft-label">{{plan.legs.length===2?'1 interchange':'Direct from an alternative station'}}</span><span class="availability-label" [class.amber-text]="plan.allocation==='WL'">{{plan.allocation==='CNF'?'Seats currently available':plan.legs.length===2?'Waitlist on at least one leg':'Waitlist available'}}</span></div>
        <div class="alternative-route"><div><strong>{{plan.legs[0].fromStation}}</strong><span>{{catalogue.name(plan.legs[0].fromStation)}}</span><small>{{plan.legs[0].departureAt|date:'d MMM, HH:mm':'+0530'}} IST</small></div><div class="alternative-connection"><rw-icon name="route"/><span>{{plan.interchangeStation?'via '+plan.interchangeStation:'Direct'}}</span><small>{{minutes(plan.durationMinutes)}} </small></div><div><strong>{{plan.legs[plan.legs.length-1].toStation}}</strong><span>{{catalogue.name(plan.legs[plan.legs.length-1].toStation)}}</span><small>{{plan.legs[plan.legs.length-1].arrivalAt|date:'d MMM, HH:mm':'+0530'}} IST</small></div></div>
        @if(plan.legs[0].fromStation!==query().fromStation || plan.legs[plan.legs.length-1].toStation!==query().toStation){<div class="nearby-explanation"><rw-icon name="pin"/><p>@if(plan.legs[0].fromStation!==query().fromStation){Board at <strong>{{plan.legs[0].fromStation}}</strong>, about {{plan.originDistanceKm}} km from {{query().fromStation}}. }@if(plan.legs[plan.legs.length-1].toStation!==query().toStation){Arrive at <strong>{{plan.legs[plan.legs.length-1].toStation}}</strong>, about {{plan.destinationDistanceKm}} km from {{query().toStation}}. }Arrange local transport separately.</p></div>}
        <div class="alternative-bottom"><div><strong>{{plan.totalFare|currency:'INR':'symbol':'1.0-2'}}</strong><small>Train fare for {{query().passengers}} · local transport extra</small></div><button class="button secondary small" [attr.aria-expanded]="expanded()===plan.id" (click)="expanded.set(expanded()===plan.id?'':plan.id);accepted=false">{{expanded()===plan.id?'Hide itinerary':'Review itinerary'}} <rw-icon name="arrow"/></button></div>
        @if(expanded()===plan.id){<div class="itinerary-details">
          @for(leg of plan.legs;track $index;let i=$index){
            @if(i>0){<div class="transfer-banner"><rw-icon name="clock"/><span><strong>{{plan.transferMinutes}} minutes to change trains at {{plan.interchangeStation}}</strong>Same station · scheduled transfer time · no delay guarantee</span></div>}
            <div class="itinerary-leg"><span class="leg-number">{{i+1}}</span><div><h3>{{leg.trainName}}</h3><p>#{{leg.trainNumber}} · {{leg.seatClass.replace('CLASS_','')}} · {{leg.fromStation}} → {{leg.toStation}}</p><p>{{leg.departureAt|date:'d MMM, HH:mm':'+0530'}} → {{leg.arrivalAt|date:'d MMM, HH:mm':'+0530'}} IST · {{duration(leg.departureAt,leg.arrivalAt)}}</p><strong>{{leg.totalFare|currency:'INR':'symbol':'1.0-2'}}</strong> · <span>{{leg.suggestedAllocation==='CNF'?leg.availableSeats+' seats available':'Waitlisted'}}</span></div></div>
          }
          @if(plan.legs.length===2){<p class="itinerary-warning">Two separate tickets and payments are required. Booking one leg does not reserve the other. Check both legs before paying; each ticket has its own cancellation rules. @if(plan.allocation==='WL'){A waitlisted leg may remain unconfirmed, leaving you without a complete journey.}</p>}
          <label class="itinerary-consent"><input type="checkbox" [(ngModel)]="accepted"/> I have checked the actual stations{{plan.legs.length===2?', transfer time and separate-ticket conditions':''}}.</label>
          <div class="button-row">@for(leg of plan.legs;track $index;let i=$index){@if(accepted){<a class="button primary small" [routerLink]="['/book',leg.trainNumber]" [queryParams]="legQuery(leg,plan.legs.length===2)">{{plan.legs.length===2?'Book leg '+(i+1):'Book this train'}}</a>}@else{<button class="button primary small" disabled>{{plan.legs.length===2?'Book leg '+(i+1):'Book this train'}}</button>}}</div>
          <small class="muted">Fare and availability are checked again when booking. @if(plan.legs.length===2){Use “Return to journey options” on your ticket to book the next leg.}</small>
        </div>}
      </article>
    }@empty{<div class="empty-state"><rw-icon name="route"/><h3>No suitable alternatives found.</h3><p>Try another date, class or nearby radius. Only published, bookable routes are considered.</p></div>}
  }
</section>`})
export class JourneyAlternativesPanel {
  query=input.required<Search>();automatic=input(false);api=inject(Api);catalogue=inject(Catalogue);
  data=signal<JourneyAlternatives|null>(null);loading=signal(false);error=signal('');expanded=signal('');
  nearby=true;radius=50;buffer=60;accepted=false;duration=duration;private generation=0;
  constructor(){effect(()=>{this.query();const auto=this.automatic();++this.generation;this.data.set(null);this.error.set('');this.expanded.set('');this.accepted=false;this.loading.set(false);if(auto)void this.load()})}
  minutes(value:number){return Math.floor(value/60)+'h '+(value%60)+'m total'}
  legQuery(leg:Quote,connection:boolean):Search & {journey?:string}{return {...(connection?{journey:JSON.stringify(this.query())}:{}),fromStation:leg.fromStation,toStation:leg.toStation,dateOfJourney:leg.boardingDate,seatClass:leg.seatClass,passengers:leg.passengerCount}}
  async load(){const generation=++this.generation;this.loading.set(true);this.error.set('');this.data.set(null);this.expanded.set('');this.accepted=false;try{const result=await this.api.get<JourneyAlternatives>('/reservations/alternatives',{...this.query(),nearby:this.nearby,radiusKm:this.radius,minTransferMinutes:this.buffer,maxTransferMinutes:360});if(generation===this.generation)this.data.set(result)}catch(e){if(generation===this.generation)this.error.set(messageOf(e))}finally{if(generation===this.generation)this.loading.set(false)}}
}
