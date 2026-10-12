import { Component, effect, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { Catalogue, Auth } from '../core/api';
import { RouteSearch } from '../core/models';
import { addMonths, indiaDate, routeSearchError, tomorrow } from '../core/utils';
import { Icon, Notice } from './ui';
import { RecentSearchStore, searchKey } from '../core/recent-searches';
@Component({selector:'rw-search-form',imports:[FormsModule,DatePipe,Icon,Notice],template:`
<form class="search-form" (ngSubmit)="search()">
  <div class="station-pair"><label class="search-field"><span><rw-icon name="pin"/> From</span><input aria-label="Departure station" name="from" [(ngModel)]="form.fromStation" list="stations" placeholder="Departure station" required maxlength="10" autocomplete="off"/></label><button type="button" class="swap" aria-label="Swap stations" (click)="swap()"><rw-icon name="swap"/></button><label class="search-field"><span><rw-icon name="pin"/> To</span><input aria-label="Arrival station" name="to" [(ngModel)]="form.toStation" list="stations" placeholder="Arrival station" required maxlength="10" autocomplete="off"/></label></div>
  <label class="search-field"><span><rw-icon name="calendar"/> Travel date</span><input aria-label="Travel date" type="date" name="date" [(ngModel)]="form.dateOfJourney" [min]="min" [max]="max" required/></label>
  <button class="button primary search-button" type="submit"><rw-icon name="search"/><span>Find trains</span></button>
</form>
<datalist id="stations">@for(s of catalogue.stations();track s.stationId){<option [value]="s.stationCode">{{s.stationName}}{{s.city?' · '+s.city:''}}</option>}</datalist>
<rw-notice [text]="error()"/>
<rw-notice [text]="hint()" tone="info"/>
@if(recent().length){
  <section class="recent-searches" aria-label="Recent searches">
    <div class="recent-searches-heading"><span><rw-icon name="clock"/> Recent searches <small>{{auth.signedIn()?'Your account on this browser':'This visit only'}}</small></span><button type="button" class="text-link" (click)="clearRecent()">Clear all</button></div>
    <ul class="recent-search-list">
      @for(s of recent();track searchKey(s)){
        <li class="recent-search-item">
          <button type="button" class="recent-search-reuse" (click)="reuse(s)" [attr.aria-label]="'Reuse search '+s.fromStation+' to '+s.toStation+' on '+s.dateOfJourney">
            <strong>{{s.fromStation}} <span aria-hidden="true">→</span> {{s.toStation}}</strong>
            <small>{{(s.dateOfJourney+'T00:00:00Z')|date:'d MMM yyyy':'UTC'}}</small>
          </button>
          <button type="button" class="icon-button" [attr.aria-label]="'Remove search '+s.fromStation+' to '+s.toStation+' on '+s.dateOfJourney" (click)="removeRecent(s)"><rw-icon name="close"/></button>
        </li>
      }
    </ul>
  </section>
}
@if(unavailable()){<div class="subtle-help">Station suggestions are unavailable. Enter station codes to search, or <button class="text-link" (click)="load()">retry suggestions</button>.</div>}
`})
export class SearchForm implements OnInit {
  initial=input<Partial<RouteSearch>>({});catalogue=inject(Catalogue);router=inject(Router);error=signal('');unavailable=signal(false);
  auth=inject(Auth);private history=new RecentSearchStore();recent=signal<RouteSearch[]>([]);hint=signal('');searchKey=searchKey;
  min=indiaDate();max=addMonths(this.min,2);form:RouteSearch={fromStation:'',toStation:'',dateOfJourney:tomorrow()};
  constructor(){effect(()=>{this.form={...this.form,...this.initial()}});effect(()=>{this.recent.set(this.history.setOwner(this.auth.session()?.username||null));this.hint.set('')})}
  ngOnInit(){void this.load()}
  async load(){try{await this.catalogue.load();this.unavailable.set(false)}catch{this.unavailable.set(true)}}
  swap(){[this.form.fromStation,this.form.toStation]=[this.form.toStation,this.form.fromStation]}
  search(){const s={dateOfJourney:this.form.dateOfJourney,fromStation:this.form.fromStation.trim().toUpperCase(),toStation:this.form.toStation.trim().toUpperCase()};const e=routeSearchError(s);this.error.set(e);if(!e){this.hint.set('');this.recent.set(this.history.save(s));void this.router.navigate(['/journeys'],{queryParams:{...s}})}}
  reuse(s:RouteSearch){
    this.min=indiaDate();this.max=addMonths(this.min,2);
    this.form={...s};this.error.set('');
    if(routeSearchError(s)){this.form.dateOfJourney='';this.hint.set('Route restored. Choose a new travel date, then select Find trains.');return}
    this.hint.set('Search restored. Review the details or select Find trains for current availability.');
  }
  removeRecent(s:RouteSearch){this.recent.set(this.history.remove(s));this.hint.set('Search removed from this browser.')}
  clearRecent(){this.recent.set(this.history.clear());this.hint.set('Recent searches cleared from this browser.')}
}
