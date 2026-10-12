export const SUPPORT_CATEGORIES = [
  {value:'BOOKING',label:'Booking'}, {value:'PAYMENT_REFUND',label:'Payment or refund'},
  {value:'ACCOUNT',label:'Account'}, {value:'OTHER',label:'Something else'}
];
export const SUPPORT_STATUSES = ['OPEN','IN_PROGRESS','RESOLVED'];
export interface SupportTicket {
  id:string;subject:string;category:string;status:string;pnr:string|null;username:string;email:string|null;
  createdAt:string;updatedAt:string;version:number;
}
export interface SupportMessage {id:string;author:string;staff:boolean;systemMessage:boolean;body:string;createdAt:string}
/** Reuse a submission reference only for an identical retry in the current form. */
export class SupportSubmission {
  private fingerprint='';private id='';
  reference(payload:unknown){const next=JSON.stringify(payload);if(next!==this.fingerprint){this.fingerprint=next;this.id=crypto.randomUUID()}return this.id}
  clear(){this.fingerprint='';this.id=''}
}
