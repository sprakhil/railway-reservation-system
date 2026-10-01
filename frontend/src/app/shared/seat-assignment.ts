import { Component, input } from '@angular/core';
import { Passenger } from '../core/models';

/** The backend owns berth numbering; the UI only presents its assignment. */
@Component({selector:'rw-seat-assignment',template:`
  @if(passenger().seatDetails; as seat){
    @if(seat.coach){
      <div class="assignment"><span class="coach" [attr.aria-label]="'Coach '+seat.coach">{{seat.coach}}</span>
        <strong>{{seat.berthType==='SEAT'?'Seat':'Berth'}} {{seat.position}}</strong></div>
      <span class="position">{{seat.berthLabel||'Position not specified'}}@if(seat.bay){<span class="bay"> · Bay {{seat.bay}}</span>}</span>
    }@else{<span class="position">{{seat.description||passenger().seatNumber||'Not assigned'}}</span>}
  }@else{<span class="position">{{passenger().seatNumber||'Not assigned'}}</span>}
`,styles:`
  :host{display:block;min-width:150px}
  .assignment{display:flex;align-items:center;gap:9px;color:var(--ink,#1e312b)}
  .coach{display:inline-flex;align-items:center;justify-content:center;min-width:36px;padding:5px 8px;border:1px solid #cbded0;border-radius:7px;background:#eef5ee;color:#164033;font-size:12px;font-weight:800;letter-spacing:.04em}
  .position{display:block;margin-top:5px;font-size:12px;line-height:1.5;color:var(--muted,#5b6f65)}
  .bay{white-space:nowrap}
`})
export class SeatAssignment {passenger=input.required<Passenger>();}
