import { Injectable } from '@angular/core';
import { Order } from './models';
export interface CheckoutResult {razorpay_order_id:string;razorpay_payment_id:string;razorpay_signature:string}
interface RazorpayInstance {open():void;close():void;on(event:string,handler:(e:{error?:{description?:string}})=>void):void}
declare global {interface Window {Razorpay?:new(options:Record<string,unknown>)=>RazorpayInstance}}
@Injectable({providedIn:'root'})
export class Checkout {
  private loading:Promise<void>|null=null;
  private instance:RazorpayInstance|null=null;
  load():Promise<void>{
    if(window.Razorpay)return Promise.resolve();if(this.loading)return this.loading;
    this.loading=new Promise<void>((resolve,reject)=>{const script=document.createElement('script');script.src='https://checkout.razorpay.com/v1/checkout.js';script.async=true;const timer=setTimeout(()=>{script.remove();reject(new Error('Checkout took too long to load. Check your connection and try again.'))},15000);script.onload=()=>{clearTimeout(timer);window.Razorpay?resolve():reject(new Error('Checkout is unavailable. Please try again.'))};script.onerror=()=>{clearTimeout(timer);script.remove();reject(new Error('Razorpay could not load. Check your connection or browser content blocker.'))};document.head.appendChild(script)}).catch(e=>{this.loading=null;throw e});return this.loading;
  }
  async open(order:Order):Promise<CheckoutResult|null>{
    if(!order.keyId.startsWith('rzp_test_')||order.mode!=='TEST')throw new Error('Only Razorpay test checkout is supported.');
    await this.load();
    return new Promise((resolve,reject)=>{this.instance=new window.Razorpay!({key:order.keyId,order_id:order.razorpayOrderId,amount:order.amountInPaise,currency:order.currency,name:'Railwize',description:`Booking · PNR ${order.pnr}`,theme:{color:'#2459e0'},handler:(result:CheckoutResult)=>resolve(result),modal:{ondismiss:()=>resolve(null)}});this.instance.on('payment.failed',e=>{this.instance?.close();reject(new Error(e.error?.description||'Payment could not be completed. You can retry while your booking hold is active.'))});this.instance.open()});
  }
  close(){this.instance?.close();this.instance=null}
}
