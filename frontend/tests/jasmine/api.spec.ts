import { createEnvironmentInjector, EnvironmentInjector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Api, Catalogue } from '../../src/app/core/api';
import { NotificationRefresh } from '../../src/app/core/notification-refresh';
import { Checkout } from '../../src/app/core/checkout';
import { Order } from '../../src/app/core/models';

describe('API side effects', () => {
  let injector: EnvironmentInjector;
  let http: jasmine.SpyObj<HttpClient>;
  beforeEach(() => {
    http=jasmine.createSpyObj<HttpClient>('HttpClient',['get','post','delete']);
    injector=createEnvironmentInjector([Api,Catalogue,NotificationRefresh,{provide:HttpClient,useValue:http}],null as unknown as EnvironmentInjector);
  });
  afterEach(()=>injector.destroy());

  it('refreshes notifications after successful booking and payment changes only', async () => {
    const api=injector.get(Api),refresh=injector.get(NotificationRefresh);
    http.post.and.returnValue(of({}));
    await api.post('/reservations',{});
    await api.post('/payments/order',{});
    expect(refresh.version()).toBe(2);
    await api.post('/auth/login',{});
    expect(refresh.version()).toBe(2);
    http.post.and.returnValue(throwError(()=>new Error('failed')));
    await expectAsync(api.post('/reservations',{})).toBeRejected();
    expect(refresh.version()).toBe(2);
  });

  it('loads every catalogue page once and shares simultaneous requests', async () => {
    http.get.and.returnValues(of({content:[{stationCode:'AAA'}],totalPages:2}),of({content:[{stationCode:'BBB'}],totalPages:2}));
    const catalogue=injector.get(Catalogue);
    const first=catalogue.load();
    expect(catalogue.load()).toBe(first);
    expect((await first).map(row=>row.stationCode)).toEqual(['AAA','BBB']);
    await catalogue.load();
    expect(http.get).toHaveBeenCalledTimes(2);
    expect(http.get.calls.mostRecent().args[1]?.params?.toString()).toContain('page=1');
  });

  it('rejects live payment keys before loading the external checkout script', async () => {
    const checkout=new Checkout();
    const load=spyOn(checkout,'load').and.resolveTo();
    await expectAsync(checkout.open({keyId:'rzp_live_fake',mode:'TEST'} as Order)).toBeRejectedWithError(/test checkout/);
    await expectAsync(checkout.open({keyId:'rzp_test_fake',mode:'LIVE'} as unknown as Order)).toBeRejectedWithError(/test checkout/);
    expect(load).not.toHaveBeenCalled();
  });
});
