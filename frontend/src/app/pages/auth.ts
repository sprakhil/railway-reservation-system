import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api, Auth } from '../core/api';
import { Session } from '../core/models';
import { messageOf, safeReturn } from '../core/utils';
import { Icon, Notice } from '../shared/ui';
@Component({selector:'rw-auth',imports:[FormsModule,RouterLink,Icon,Notice],template:`<div class="container auth-layout"><aside class="auth-story"><span class="eyebrow">A TICKET TO WHAT'S NEXT</span><h1>New places.<br>Old friends.<br><em>Your journey.</em></h1><div class="auth-route"><span class="route-dot"></span><div><small>DEPARTURE</small><strong>A little everyday</strong></div><span class="route-dashes"></span><rw-icon name="train"/><span class="route-dashes"></span><span class="route-dot filled"></span><div><small>ARRIVAL</small><strong>Something to look forward to</strong></div></div><p>One account for your tickets,<br>your people, and every next stop.</p><span class="auth-footnote"><rw-icon name="shield"/> Your journeys, all in one place.</span></aside><section class="auth-panel"><span class="eyebrow">{{mode()==='register'?'WELCOME ABOARD':'YOUR RAILWIZE ACCOUNT'}}</span><h2>{{titles[mode()]||'Welcome back.'}}</h2><p class="muted">{{subtitles[mode()]||'Your next journey is waiting for you.'}}</p><rw-notice [text]="error()"/><rw-notice [text]="success()" tone="success"/>
@if(mode()==='callback'){<div class="loading-state" role="status">@if(busy()){<span class="spinner"></span> Completing Google sign-in…}@else{<a routerLink="/auth/login" class="button primary">Return to sign in</a>}</div>}
@else{<form class="stack-form" (ngSubmit)="submit()" #f="ngForm">
@if(mode()==='login'){<label>Username or email<input name="identifier" [(ngModel)]="identifier" autocomplete="username" placeholder="Your username or email" required/></label>}
@if(mode()==='register'){<label>Username<input name="username" [(ngModel)]="username" autocomplete="username" minlength="4" maxlength="20" pattern="[a-zA-Z0-9_]+" placeholder="Choose a username" required/><small>4–20 letters, numbers or underscores.</small></label>}
@if(mode()!=='login'){<label>Email address<input name="email" type="email" [(ngModel)]="email" autocomplete="email" placeholder="you@example.com" required/></label>}
@if(mode()==='verify'||mode()==='reset'){<label>Six-digit email code<input name="otp" [(ngModel)]="otp" autocomplete="one-time-code" inputmode="numeric" maxlength="6" pattern="[0-9]{6}" placeholder="000000" required/></label>}
@if(mode()==='login'||mode()==='register'||mode()==='reset'){<label>{{mode()==='reset'?'New password':'Password'}}<div class="password-field"><input [type]="showPassword()?'text':'password'" [attr.aria-label]="mode()==='reset'?'New password':'Password'" name="password" [(ngModel)]="password" [attr.autocomplete]="mode()==='login'?'current-password':'new-password'" [minlength]="mode()==='login'?1:8" maxlength="30" placeholder="Enter your password" required/><button type="button" (click)="showPassword.set(!showPassword())" [attr.aria-label]="showPassword()?'Hide password':'Show password'">{{showPassword()?'Hide':'Show'}}</button></div>@if(mode()!=='login'){<small>8–30 characters, with uppercase, lowercase, a number and a special character (&#64;#$%^&amp;+=!).</small>}</label>}
@if(mode()==='login'){<div class="text-right"><a routerLink="/auth/forgot" class="text-link">Forgot password?</a></div>}
<button class="button primary full" type="submit" [disabled]="f.invalid||busy()">{{busy()?'Please wait…':actions[mode()]}}<rw-icon name="arrow"/></button>
</form>
@if(mode()==='login'||mode()==='register'){<div class="divider"><span>or continue with</span></div><button class="button google full" [disabled]="busy()" (click)="google()"><span class="google-g">G</span> Google</button><p class="auth-switch">{{mode()==='login'?'New to Railwize?':'Already have an account?'}} <a [routerLink]="mode()==='login'?'/auth/register':'/auth/login'" [queryParams]="{returnUrl:returnUrl()}">{{mode()==='login'?'Create an account':'Sign in'}}</a></p>}
@if(mode()==='verify'){<button class="text-link" [disabled]="busy()||!email" (click)="resend()">Send a new verification code</button>}
@if(mode()!=='login'&&mode()!=='register'){<a routerLink="/auth/login" [queryParams]="{returnUrl:returnUrl()}" class="back-link"><rw-icon name="back"/> Back to sign in</a>}
}<p class="auth-disclaimer">A smoother journey starts with your account.</p></section></div>`})
export class AuthPage {
  api=inject(Api);auth=inject(Auth);route=inject(ActivatedRoute);router=inject(Router);mode=signal('login');busy=signal(false);error=signal('');success=signal('');showPassword=signal(false);identifier='';username='';email='';password='';otp='';
  titles:Record<string,string>={login:'Welcome back.',register:'Your journey starts here.',verify:'Check your inbox.',forgot:'Let’s get you back in.',reset:'A fresh start.',callback:'One moment, please.'};
  subtitles:Record<string,string>={login:'Sign in to pick up where you left off.',register:'Create an account and keep every journey together.',verify:'Enter the registration code sent to your email.',forgot:'We’ll email you a code to reset your password.',reset:'Use your password-reset code and choose a new password.',callback:'We’re securely connecting your Google account.'};
  actions:Record<string,string>={login:'Sign in',register:'Create account',verify:'Verify email',forgot:'Send reset code',reset:'Reset password'};
  constructor(){this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(p=>{this.mode.set(p.get('mode')||'callback');if(!this.titles[this.mode()]){void this.router.navigateByUrl('/auth/login',{replaceUrl:true});return}this.email=this.route.snapshot.queryParamMap.get('email')||this.email;this.error.set('');this.success.set('');if(this.mode()==='callback')void this.callback()})}
  returnUrl(){return safeReturn(this.route.snapshot.queryParamMap.get('returnUrl'))}
  async finish(s:Session){this.auth.set(s);const target=s.role==='ADMIN'?'/admin':(this.route.snapshot.queryParamMap.get('returnUrl')||sessionStorage.getItem('railwize.returnTo')||'/bookings');sessionStorage.removeItem('railwize.returnTo');const destination=!this.auth.admin()&&s.usernameSetupSuggested?'/account?returnUrl='+encodeURIComponent(safeReturn(target)):safeReturn(target);await this.router.navigateByUrl(destination,{replaceUrl:this.mode()==='callback'})}
  async submit(){this.busy.set(true);this.error.set('');this.success.set('');try{
    if(['register','reset'].includes(this.mode())&&!/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\S+$).{8,30}$/.test(this.password))throw new Error('Use a password with uppercase, lowercase, a number and a supported special character.');
    switch(this.mode()){
      case 'login':await this.finish(await this.api.post<Session>('/auth/login',{identifier:this.identifier,password:this.password}));break;
      case 'register':await this.api.text('/auth/register',{username:this.username,email:this.email,password:this.password});this.password='';await this.router.navigate(['/auth/verify'],{queryParams:{email:this.email,returnUrl:this.returnUrl()}});break;
      case 'verify': {
          const verifiedEmail = this.email;
          const returnUrl = this.returnUrl();

          await this.api.text('/auth/verify-otp', {
            email: verifiedEmail,
            otp: this.otp
          });

          this.otp = '';
          this.password = '';

          await this.router.navigate(['/auth/login'], {
            queryParams: { returnUrl },
            replaceUrl: true
          });

          this.identifier = verifiedEmail;
          this.success.set(
            'Email verified successfully. Sign in to continue.'
          );
          break;
      }
      case 'forgot':await this.api.text('/auth/forgot-password?email='+encodeURIComponent(this.email));await this.router.navigate(['/auth/reset'],{queryParams:{email:this.email,returnUrl:this.returnUrl()}});break;
      case 'reset':this.success.set(await this.api.text('/auth/reset-password',{email:this.email,otp:this.otp,newPassword:this.password}));this.auth.clear();this.password='';this.otp='';break;
    }
  }catch(e){this.error.set(messageOf(e))}finally{this.busy.set(false)}}
  async resend(){this.busy.set(true);this.error.set('');try{this.success.set(await this.api.text('/auth/resend-otp?email='+encodeURIComponent(this.email)))}catch(e){this.error.set(messageOf(e))}finally{this.busy.set(false)}}
  google(){sessionStorage.setItem('railwize.returnTo',this.route.snapshot.queryParamMap.get('returnUrl')||'/bookings');window.location.assign('/api/auth/google/start')}
  async callback(){this.busy.set(true);try{const code=this.route.snapshot.queryParamMap.get('code');if(this.route.snapshot.queryParamMap.has('error'))throw new Error('Google sign-in was cancelled. You can try again.');if(!code)throw new Error('This sign-in link has no exchange code. Please start Google sign-in again.');const s=await this.api.post<Session>('/auth/google/exchange',{code});await this.finish(s)}catch(e){this.error.set(messageOf(e))}finally{this.busy.set(false)}}
}
