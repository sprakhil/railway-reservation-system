import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpBackend, HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpParams } from '@angular/common/http';
import { Router, CanActivateFn } from '@angular/router';
import { firstValueFrom, from, throwError } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { NotificationRefresh } from './notification-refresh';
import { Session, Station, Page } from './models';

// Same-origin /api is proxied to port 8080 in development. No provider secrets belong here.
const KEY='railwize.session.v1';
@Injectable({providedIn:'root'})
export class Auth {
  private raw=new HttpClient(inject(HttpBackend));
  session=signal<Session|null>(this.restore());
  signedIn=computed(()=>!!this.session());
  admin=computed(()=>this.session()?.role==='ADMIN');
  username=computed(()=>this.session()?.displayUsername||this.session()?.username||'');
  private refreshing:Promise<Session>|null=null;
  private restore():Session|null {try {const s=JSON.parse(sessionStorage.getItem(KEY)||'null');return s?.accessToken&&s?.refreshToken&&s?.username?s:null}catch{return null}}
  set(s:Session){this.session.set(s);sessionStorage.setItem(KEY,JSON.stringify(s))}
  clear(){this.session.set(null);sessionStorage.removeItem(KEY);sessionStorage.removeItem('railwize.pending-booking')}
  refresh():Promise<Session> {
    if(this.refreshing)return this.refreshing;
    const previous=this.session();
    if(!previous)return Promise.reject(new Error('Please sign in to continue.'));
    this.refreshing=firstValueFrom(this.raw.post<Session>('/api/auth/refresh',{refreshToken:previous.refreshToken},{withCredentials:true}))
      .then(s=>{if(this.session()?.refreshToken!==previous.refreshToken)throw new Error('Your session changed. Please try again.');this.set(s);return s})
      .catch(e=>{if(this.session()?.refreshToken===previous.refreshToken)this.clear();throw e})
      .finally(()=>{this.refreshing=null});
    return this.refreshing;
  }
}
export const authInterceptor:HttpInterceptorFn=(req,next)=>{
  const auth=inject(Auth);
  const isApi=req.url.startsWith('/api/');
  const publicAuth=/\/auth\/(login|register|verify-otp|resend-otp|forgot-password|reset-password|refresh|google\/exchange)(\?|$)/.test(req.url);
  const token=isApi&&!publicAuth?auth.session()?.accessToken:null;
  const outgoing=isApi?req.clone({withCredentials:true,...(token?{setHeaders:{Authorization:`Bearer ${token}`}}:{})}):req;
  return next(outgoing).pipe(catchError((e:HttpErrorResponse)=>{
    if(e.status!==401||!token||!auth.session())return throwError(()=>e);
    return from(auth.refresh()).pipe(switchMap(s=>next(req.clone({withCredentials:true,setHeaders:{Authorization:`Bearer ${s.accessToken}`}}))));
  }));
};
@Injectable({providedIn:'root'})
export class Api {
  private http=inject(HttpClient);
  private notificationRefresh=inject(NotificationRefresh);
  private changed(path:string){if(path.startsWith('/reservations')||path.startsWith('/payments'))this.notificationRefresh.request()}
  get<T>(path:string,params:Record<string,unknown>={}) {let p=new HttpParams();for(const [k,v] of Object.entries(params))if(v!==''&&v!=null)p=p.set(k,String(v));return firstValueFrom(this.http.get<T>('/api'+path,{params:p}))}
  post<T>(path:string,body:unknown={},headers:Record<string,string>={}){return firstValueFrom(this.http.post<T>('/api'+path,body,{headers}).pipe(tap(()=>this.changed(path))))}
  put<T>(path:string,body:unknown){return firstValueFrom(this.http.put<T>('/api'+path,body))}
  delete<T>(path:string){return firstValueFrom(this.http.delete<T>('/api'+path).pipe(tap(()=>this.changed(path))))}
  text(path:string,body:unknown={}){return firstValueFrom(this.http.post('/api'+path,body,{responseType:'text'}))}
  async pdf(pnr:string){const blob=await firstValueFrom(this.http.get(`/api/reservations/${encodeURIComponent(pnr)}/ticket.pdf`,{responseType:'blob'}));const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`Railwize-${pnr}.pdf`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
  async downloadCsv(path:string,params:Record<string,unknown>,filename:string){
    let p=new HttpParams();for(const [key,value] of Object.entries(params))if(value!==''&&value!=null)p=p.set(key,String(value));
    try{const blob=await firstValueFrom(this.http.get('/api'+path,{params:p,responseType:'blob'}));const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
    catch(e){if(e instanceof HttpErrorResponse&&e.error instanceof Blob){let message='The report could not be downloaded. Please retry.';try{message=JSON.parse(await e.error.text()).message||message}catch{}throw new Error(message)}throw e}
  }
}
@Injectable({providedIn:'root'})
export class Catalogue {
  private api=inject(Api);stations=signal<Station[]>([]);private loading:Promise<Station[]>|null=null;
  load():Promise<Station[]> {if(this.stations().length)return Promise.resolve(this.stations());if(this.loading)return this.loading;this.loading=this.fetch().finally(()=>this.loading=null);return this.loading}
  private async fetch(){const rows:Station[]=[];let page=0;while(true){const p=await this.api.get<Page<Station>>('/stations',{page,size:100});rows.push(...p.content);if(++page>=p.totalPages)break}this.stations.set(rows);return rows}
  name(code:string){return this.stations().find(s=>s.stationCode===code)?.stationName||code}
}
export const signedInGuard:CanActivateFn=(_,state)=>inject(Auth).signedIn()||inject(Router).createUrlTree(['/auth/login'],{queryParams:{returnUrl:state.url}});
export const adminGuard:CanActivateFn=(_,state)=>{const auth=inject(Auth),router=inject(Router);return auth.admin()?true:router.createUrlTree(auth.signedIn()?['/forbidden']:['/auth/login'],{queryParams:{returnUrl:state.url}})};

export const guestOnlyGuard: CanActivateFn = (route) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const mode = route.paramMap.get('mode');

  if (!['login', 'register'].includes(mode ?? '')) return true;

  return !auth.signedIn()
    || router.createUrlTree([auth.admin() ? '/admin' : '/bookings']);
};
