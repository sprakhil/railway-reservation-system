import { createEnvironmentInjector, EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { HttpBackend, HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, convertToParamMap, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { Auth, adminGuard, authInterceptor, guestOnlyGuard, signedInGuard } from '../../src/app/core/api';
import { Session } from '../../src/app/core/models';

describe('Authentication and route access', () => {
  let injector: EnvironmentInjector;
  let auth: Auth;
  let backend: jasmine.SpyObj<HttpBackend>;
  let storage: Map<string,string>;
  let previousStorage: PropertyDescriptor | undefined;
  const session: Session = {username:'alice',role:'PASSENGER',accessToken:'old-token',refreshToken:'old-refresh'};
  const state = {url:'/bookings'} as RouterStateSnapshot;
  const route = (mode:string) => ({paramMap:convertToParamMap({mode})}) as ActivatedRouteSnapshot;
  const tree = new UrlTree();
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    storage = new Map();
    previousStorage = Object.getOwnPropertyDescriptor(globalThis,'sessionStorage');
    Object.defineProperty(globalThis,'sessionStorage',{configurable:true,value:{
      getItem:(key:string)=>storage.get(key)??null,
      setItem:(key:string,value:string)=>storage.set(key,value),removeItem:(key:string)=>storage.delete(key)
    }});
    backend = jasmine.createSpyObj<HttpBackend>('HttpBackend',['handle']);
    router = jasmine.createSpyObj<Router>('Router',['createUrlTree']);
    router.createUrlTree.and.returnValue(tree as never);
    injector = createEnvironmentInjector([Auth,{provide:HttpBackend,useValue:backend},{provide:Router,useValue:router}], null as unknown as EnvironmentInjector);
    auth = injector.get(Auth);
  });
  afterEach(() => {
    injector.destroy();
    if(previousStorage) Object.defineProperty(globalThis,'sessionStorage',previousStorage);
    else Reflect.deleteProperty(globalThis,'sessionStorage');
  });

  it('allows guests to register and redirects authenticated passengers and admins', () => {
    const guard = (mode:string) => runInInjectionContext(injector,()=>guestOnlyGuard(route(mode),state));
    expect(guard('register')).toBeTrue();
    auth.set(session);
    expect(guard('login')).toBe(tree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/bookings']);
    auth.set({...session,role:'ADMIN'});
    expect(guard('register')).toBe(tree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/admin']);
    expect(guard('reset-password')).toBeTrue();
  });

  it('requires a login for protected routes and denies passenger access to admin routes', () => {
    expect(runInInjectionContext(injector,()=>signedInGuard(route(''),state))).toBe(tree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/login'],{queryParams:{returnUrl:'/bookings'}});
    auth.set(session);
    expect(runInInjectionContext(injector,()=>adminGuard(route(''),state))).toBe(tree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/forbidden'],{queryParams:{returnUrl:'/bookings'}});
    auth.set({...session,role:'ADMIN'});
    expect(runInInjectionContext(injector,()=>adminGuard(route(''),state))).toBeTrue();
  });

  it('clears persisted credentials and pending checkout on logout', () => {
    auth.set(session); storage.set('railwize.pending-booking','private booking');
    auth.clear();
    expect(auth.signedIn()).toBeFalse();
    expect(storage.has('railwize.session.v1')).toBeFalse();
    expect(storage.has('railwize.pending-booking')).toBeFalse();
  });

  it('shares one refresh request and prevents a late response from undoing logout', async () => {
    auth.set(session);
    const response = new Subject<HttpResponse<Session>>();
    backend.handle.and.returnValue(response);
    const first = auth.refresh();
    expect(auth.refresh()).toBe(first);
    expect(backend.handle).toHaveBeenCalledTimes(1);
    auth.clear();
    const rejected = expectAsync(first).toBeRejectedWithError(/session changed/);
    response.next(new HttpResponse({body:{...session,accessToken:'new-token'}}));
    await rejected;
    expect(auth.signedIn()).toBeFalse();
  });

  it('never attaches a bearer token to third-party URLs or login requests', async () => {
    auth.set(session);
    for(const url of ['https://third-party.invalid/data','/api/auth/login','/api/auth/register']) {
      const next = jasmine.createSpy('next').and.returnValue(of(new HttpResponse({status:200})));
      await firstValueFrom(runInInjectionContext(injector,()=>authInterceptor(new HttpRequest('GET',url),next)));
      expect(next.calls.mostRecent().args[0].headers.has('Authorization')).toBeFalse();
    }
  });

  it('refreshes an expired token once and retries a protected request with the new token', async () => {
    auth.set(session);
    backend.handle.and.returnValue(of(new HttpResponse({body:{...session,accessToken:'new-token',refreshToken:'new-refresh'}})));
    const next = jasmine.createSpy('next').and.callFake((req:HttpRequest<unknown>) =>
      req.headers.get('Authorization')==='Bearer old-token'
        ? throwError(()=>new HttpErrorResponse({status:401})) : of(new HttpResponse({status:200})));
    await firstValueFrom(runInInjectionContext(injector,()=>authInterceptor(new HttpRequest('GET','/api/users/me'),next)));
    expect(next).toHaveBeenCalledTimes(2);
    expect(next.calls.mostRecent().args[0].headers.get('Authorization')).toBe('Bearer new-token');
    expect(auth.session()?.accessToken).toBe('new-token');
  });
});

