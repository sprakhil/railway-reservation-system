import { createEnvironmentInjector, EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Api } from '../../src/app/core/api';
import { Help } from '../../src/app/pages/help';
import { AdminRuns } from '../../src/app/pages/admin-runs';

describe('Help centre and train-run review',()=>{
 let injector:EnvironmentInjector;
 afterEach(()=>injector?.destroy());
 it('opens cancellation help from a ticket link and supports searching and reset',()=>{
  injector=createEnvironmentInjector([{provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:convertToParamMap({topic:'Train cancellations'})}}}],null as unknown as EnvironmentInjector);
  const page=runInInjectionContext(injector,()=>new Help());
  expect(page.answers().length).toBeGreaterThan(0);expect(page.answers().every(f=>f.category==='Train cancellations')).toBeTrue();
  page.query.set('calendar');expect(page.answers().some(f=>f.a.includes('calendar'))).toBeTrue();
  page.query.set('unmatched-xyz');expect(page.answers()).toEqual([]);page.reset();expect(page.answers().length).toBeGreaterThan(12);
 });
 it('requires reviewed run and acknowledgement, and clears approval on selection change',async()=>{
  const api=jasmine.createSpyObj<Api>('Api',['get','post']);api.get.and.resolveTo({content:[],page:0,size:10,totalElements:0,totalPages:0});api.post.and.resolveTo({cancelled:true});
  injector=createEnvironmentInjector([{provide:Api,useValue:api}],null as unknown as EnvironmentInjector);
  const page=runInInjectionContext(injector,()=>new AdminRuns());await page.cancel();expect(api.post).not.toHaveBeenCalled();
  api.get.and.resolveTo({trainNumber:'T1',runDate:'2030-01-02',cancelled:false});page.train='t1';page.date='2030-01-02';await page.review();
  await page.cancel();expect(api.post).not.toHaveBeenCalled();page.ack=true;page.reason='Track maintenance';await page.cancel();
  expect(api.post).toHaveBeenCalledOnceWith('/reservations/admin/train-runs/T1/2030-01-02/cancel',{reason:'Track maintenance'});
  page.clearReview();expect(page.view()).toBeNull();expect(page.ack).toBeFalse();
 });
 it('does not show successful cancellation when submission fails',async()=>{
  const api=jasmine.createSpyObj<Api>('Api',['get','post']);api.get.and.resolveTo({trainNumber:'T1',runDate:'2030-01-02',cancelled:false});api.post.and.rejectWith(new Error('Service unavailable'));
  injector=createEnvironmentInjector([{provide:Api,useValue:api}],null as unknown as EnvironmentInjector);
  const page=runInInjectionContext(injector,()=>new AdminRuns());page.train='T1';page.date='2030-01-02';await page.review();page.ack=true;page.reason='Track maintenance';await page.cancel();
  expect(page.view()?.cancelled).toBeFalse();expect(page.error()).toContain('Refresh this run');expect(page.busy()).toBeFalse();
 });
});
