import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Icon } from '../shared/ui';
const FAQS=[
  {
    "q": "How do I book a ticket?",
    "a": "Choose different departure and arrival stations and a date. Compare each train’s classes, per-passenger fares and seat availability. Select a class, choose 1–6 passengers on the booking page, review the updated group fare, enter their details and create the booking. Check the confirmed/waitlist allocation, then complete checkout within the payment hold.",
    "category": "Finding trains"
  },
  {
    "q": "How far ahead can I book?",
    "a": "From today through today plus two calendar months, inclusive, using India Standard Time. The boarding time must still be in the future. The date refers to when you board, including trains that started their run on an earlier day.",
    "category": "Finding trains"
  },
  {
    "q": "What does the two-minute timer mean?",
    "a": "Your unpaid booking has a hold lasting at most two minutes. It may end sooner at boarding or the waitlist cutoff. Confirmed-allocation seats are held during that period. An unpaid waitlisted booking does not hold a seat or an active queue position. An expired hold cannot be revived by a late payment; late captures receive a full refund.",
    "category": "Payments & refunds"
  },
  {
    "q": "How do confirmed and waitlisted bookings work?",
    "a": "Everyone in a booking has the same allocation: confirmed (CNF) or waitlisted (WL). Both require payment. There is no RAC or mixed allocation. A payment marked successful can still belong to a waitlisted ticket—always check the booking status.",
    "category": "Waitlist"
  },
  {
    "q": "Will I have to accept or pay again after promotion?",
    "a": "No. Paid waitlisted bookings promote automatically in creation order when the entire booking fits on its route. Smaller bookings cannot bypass an older booking. A promotion email is sent and your journey page updates when refreshed. No additional payment or passenger action is required.",
    "category": "Waitlist"
  },
  {
    "q": "What happens if my waitlisted ticket is not confirmed?",
    "a": "The waitlist closes one hour before your boarding time. Any remaining paid WL booking is cancelled and a full refund is requested. Refund completion depends on provider processing; a pending refund is not a completed refund.",
    "category": "Waitlist"
  },
  {
    "q": "Can I cancel one passenger?",
    "a": "Cancellation applies to the entire booking, before boarding. There is no individual-passenger cancellation. The cancellation preview shows the current fee and refund estimate. Paid WL bookings have no cancellation fee; paid CNF bookings have a default 20% cancellation fee. Promotion between preview and cancellation can change that fee.",
    "category": "Payments & refunds"
  },
  {
    "q": "Where is my PDF ticket?",
    "a": "Open My journeys, choose a paid booking, and select Download PDF. The PDF shows the booking state at download time. Download it again after a waitlist promotion or cancellation to see the updated state.",
    "category": "Tickets & updates"
  },
  {
    "q": "How do saved passengers work?",
    "a": "Add yourself, family or friends in Saved passengers. When booking, select a saved person to copy their details into the form. Ages must be checked because they do not increase automatically. Editing or deleting a saved passenger never changes an existing booking.",
    "category": "Account & passengers"
  },
  {
    "q": "How does Google sign-in work?",
    "a": "Use Continue with Google on the sign-in page. Choose your Google account and complete sign-in in the same browser. Your account connects securely, so you can get back to planning your journey.",
    "category": "Account & passengers"
  },
  {
    "q": "What if I close checkout or lose my connection?",
    "a": "Open the booking again and refresh its payment status. The backend can reconcile a completed payment even if the browser callback was missed. Do not assume a booking succeeded from the checkout screen alone. Retry payment only after checking the booking state and before the hold expires.",
    "category": "Payments & refunds"
  },
  {
    "q": "How is administrator access granted?",
    "a": "Administrator access is by invitation. Accept the emailed invitation using the verified account it was sent to. Public registration and Google sign-in do not let users choose an admin role.",
    "category": "Account & passengers"
  },
  {
    "category": "Train cancellations",
    "q": "What happens when the operator cancels my train?",
    "a": "Cancellation applies to one train run, identified by its departure date from the first station. All active bookings on that run, including later boarding stations and every class, are cancelled. Paid confirmed and waitlisted tickets receive a full test refund with zero cancellation fee. Your booking shows the reason and refund progress; email and in-app notifications keep you informed."
  },
  {
    "category": "Train cancellations",
    "q": "Must I cancel the ticket myself to receive the full refund?",
    "a": "No. The operator cancellation automatically cancels affected tickets and requests eligible full refunds. Do not create another cancellation request. Refund requested or pending means processing is continuing; only processed means the provider has completed the test refund."
  },
  {
    "category": "Train cancellations",
    "q": "What if I was still paying when the train was cancelled?",
    "a": "The pending booking is cancelled and checkout is no longer available. If a payment already in flight is captured later, the backend reconciles it and requests a full refund. Open My journeys and refresh payment status. Never assume an old checkout screen confirms a valid ticket."
  },
  {
    "category": "Train cancellations",
    "q": "Will my connecting train and calendar reminder be cancelled too?",
    "a": "Connecting legs have independent PNRs and payments. Only tickets for the cancelled run are cancelled automatically; review any onward booking separately. Imported calendar reminders do not update automatically, so remove the event yourself."
  },
  {
    "category": "Finding trains",
    "q": "Why is a train missing from my search?",
    "a": "The train must have a published timetable, serve your stations in the correct direction, operate on its original run date, and support the selected class with configured fares and capacity. Boarding must still be in the future. Cancelled runs cannot be booked. Direct results include all configured classes. Try another date or review connecting and nearby alternatives."
  },
  {
    "category": "Finding trains",
    "q": "How do connecting and nearby-station suggestions work?",
    "a": "Journey options may combine two trains changing at the same station, or use a nearby boarding or arrival station. Check the actual station names, each boarding date, transfer time and availability. Nearby distance is a straight-line estimate, not road travel time. Local transport costs are not included. Each leg needs its own booking and payment; buying one does not reserve the other."
  },
  {
    "category": "Finding trains",
    "q": "How are ticket fares calculated?",
    "a": "Each class has a base fare, per-kilometre rate and minimum fare. Distance is the difference between cumulative distances recorded in the timetable. Fare per passenger is the higher of the minimum fare and base fare plus distance times the rate. The backend checks the fare again before booking."
  },
  {
    "category": "Payments & refunds",
    "q": "Are payments and refunds real?",
    "a": "Railwize uses Razorpay test mode. No real money is charged or returned. Refunds still move through requested, pending and processed states so you can follow the complete workflow. No real bank-credit timeline is promised."
  },
  {
    "category": "Tickets & updates",
    "q": "How do I follow a refund or an unread notification?",
    "a": "Open My journeys, select your booking and check Payment & refund updates. Use Refresh to request current status. The notification bell refreshes about every five seconds while the tab is visible and links to your booking. An old notification records an earlier event; the booking page shows its current state."
  },
  {
    "category": "Tickets & updates",
    "q": "What should I do if an email is missing?",
    "a": "Check Spam and the verified email shown in Your account. Open the booking to verify its current status and download the PDF for a paid booking. Email delivery can be delayed without reversing a successful booking. In-app updates remain available independently."
  },
  {
    "category": "Contact support",
    "q": "How do I contact the support team and follow my request?",
    "a": "Sign in and select Contact support. Choose a category, describe your issue and optionally link a PNR from your own account. Open My support requests to read replies and add more details. Requests move through Open, In progress and Resolved; replying to a resolved request reopens it. Updates also appear in your notifications and email. Never include passwords, OTPs or full card details. A support request does not cancel a booking or process a refund automatically."
  },
  {
    "category": "Account & passengers",
    "q": "Can I change my username or recover my password?",
    "a": "Your account allows one username change, including replacing a generated Google username. Existing bookings remain linked to your account. For a password account, use Forgot password and the reset OTP. Google-only accounts should use Google sign-in; do not share passwords or OTPs with the assistant."
  },
  {
    "category": "Tickets & updates",
    "q": "Can I choose a particular lower berth?",
    "a": "You can record a berth preference, but it is not guaranteed. Seats are allocated from available positions. Confirmed tickets show the coach, position and supported berth type; waitlisted tickets have no assigned seat until promotion."
  },
  {
    "category": "Tickets & updates",
    "q": "Where is the assistant and what can it do?",
    "a": "When signed in, Ask Railwize opens a panel on the current page. You can ask policy questions or request your bookings, cancellations, booking count and next journey. The assistant reads information; it cannot book, cancel or pay for you. If AI is unavailable, use the normal search, booking and account pages."
  }
];
@Component({selector:'rw-help',imports:[RouterLink,Icon,FormsModule],styles:[`
.help-search{max-width:720px;margin-top:24px}.help-topics{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0}.help-topics button[aria-pressed=true]{background:var(--green);color:white}.help-shortcuts{display:grid;gap:12px;margin-top:24px}.help-layout{align-items:start}.help-intro{position:static}.faq-list p{white-space:normal}.help-count{color:var(--muted);margin-bottom:16px}.help-empty{padding:30px;border:1px solid var(--line);border-radius:16px}.help-layout>*{min-width:0}
`],template:`<section class="page-header"><div class="container"><span class="eyebrow">HERE TO HELP</span><h1>Your journey, explained.</h1><p>Find answers about bookings, train cancellations, payments and your account.</p><div class="help-search"><label for="help-search">Search the Help centre</label><input id="help-search" type="search" placeholder="Try refund, waitlist or cancelled train" [ngModel]="query()" (ngModelChange)="query.set($event)"/></div><div class="help-topics" aria-label="Help topics">@for(t of topics;track t){<button type="button" class="button secondary small" [attr.aria-pressed]="topic()===t" (click)="topic.set(t)">{{t}}</button>}</div></div></section><div class="container page-content help-layout"><aside class="help-intro"><span class="feature-icon mint"><rw-icon name="ticket"/></span><h2>Get back on track.</h2><p>Check your booking for the latest status. Notifications and downloaded tickets may describe an earlier update.</p><div class="help-shortcuts"><a class="button primary" routerLink="/support">Contact support <rw-icon name="arrow"/></a><a class="text-link" routerLink="/support">My support requests ↗</a><p class="support-note">Sign in to send a private request and follow replies from our team.</p><a class="button primary" routerLink="/bookings">My bookings & refunds <rw-icon name="arrow"/></a><a class="button secondary" routerLink="/journeys">Find another train</a><a class="text-link" routerLink="/account">Account & verified email ↗</a><a class="text-link" routerLink="/assistant">Ask Railwize ↗</a></div></aside><section aria-label="Help answers"><p class="help-count" role="status">{{answers().length}} answers @if(topic()!=='All'){ · {{topic()}}}</p><div class="faq-list">@for(f of answers();track f.q){<details><summary>{{f.q}}<span>+</span></summary><p>{{f.a}}</p></details>}@empty{<div class="help-empty"><h3>No matching answers</h3><p>Try a shorter phrase or choose another topic.</p><button type="button" class="text-link" (click)="reset()">Show all answers</button></div>}</div></section></div>`})
export class Help {
 query=signal('');topic=signal(inject(ActivatedRoute).snapshot.queryParamMap.get('topic')||'All');
 topics=['All',...new Set(FAQS.map(f=>f.category))];
 answers=computed(()=>{const words=this.query().toLowerCase().trim().split(/\s+/).filter(Boolean);return FAQS.filter(f=>(this.topic()==='All'||f.category===this.topic())&&words.every(w=>(f.q+' '+f.a+' '+f.category).toLowerCase().includes(w)))});
 constructor(){if(!this.topics.includes(this.topic()))this.topic.set('All')}
 reset(){this.query.set('');this.topic.set('All')}
}
@Component({selector:'rw-not-found',imports:[RouterLink,Icon],template:`<div class="container page-content"><div class="empty-state spacious"><span class="eyebrow">{{forbidden?'ACCESS RESTRICTED':'A SMALL DETOUR'}}</span><rw-icon [name]="forbidden?'shield':'route'"/><h1>{{forbidden?'This stop is for admins.':'Looks like we missed a stop.'}}</h1><p>{{forbidden?'Sign in with an administrator account to use this workspace.':'The page you’re looking for isn’t on this route.'}}</p><a routerLink="/" class="button primary">Back to the start <rw-icon name="arrow"/></a></div></div>`})
export class NotFound {forbidden=inject(ActivatedRoute).snapshot.data['forbidden']===true}
