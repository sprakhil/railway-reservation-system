import {test,expect} from '@playwright/test';

test('help topics, search and mobile layout work without signing in',async({page})=>{
 await page.goto('/help?topic=Train%20cancellations');
 await expect(page.getByRole('heading',{name:'Your journey, explained.'})).toBeVisible();
 await page.getByLabel('Search the Help centre').fill('calendar');
 await page.locator('summary').filter({hasText:'Will my connecting train and calendar reminder be cancelled too?'}).click();
 await expect(page.getByText(/Connecting legs have independent PNRs/)).toBeVisible();
 await page.getByLabel('Search the Help centre').fill('unmatched-xyz');await expect(page.getByRole('heading',{name:'No matching answers'})).toBeVisible();
 await page.getByRole('button',{name:'Show all answers'}).click();await expect(page.locator('summary').filter({hasText:'How do I book a ticket?'})).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});

test('admin reviews a run before cancelling and sees refund progress',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'operator',role:'ADMIN',accessToken:'fixture',refreshToken:'fixture'})));
 let cancelled=false,posts=0;
 const view={trainNumber:'QA109',runDate:'2030-01-02',sourceDepartureAt:'2030-01-02T12:30:00Z',bookings:3,passengers:5,confirmedBookings:1,waitingBookings:1,unpaidBookings:1,refundAmount:280,processedRefunds:0,outstandingRefunds:280};
 await page.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let body:unknown={content:[],page:0,size:10,totalElements:0,totalPages:0};
  if(p==='/api/reservations/notifications')body={notifications:body,unreadCount:0};
  if(p.includes('/train-runs/QA109/2030-01-02')){if(route.request().method()==='POST'){posts++;expect(route.request().postDataJSON()).toEqual({reason:'Track maintenance'});cancelled=true;}body={...view,cancelled,reason:cancelled?'Track maintenance':null,cancelledBy:cancelled?'operator':null,cancelledAt:cancelled?'2030-01-01T00:00:00Z':null};}
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/admin/train-runs');await page.getByLabel('Train number',{exact:true}).fill('QA109');await page.getByLabel('Original departure date').fill('2030-01-02');
 await page.getByRole('button',{name:'Review affected bookings'}).click();await expect(page.getByRole('heading',{name:'Review cancellation'})).toBeVisible();expect(posts).toBe(0);
 const confirm=page.getByRole('button',{name:'Cancel this run & request full refunds'});await expect(confirm).toBeDisabled();
 await page.getByLabel('Reason shown to passengers').fill('Track maintenance');await page.getByRole('checkbox').check();await confirm.click();
 await expect(page.getByRole('heading',{name:'Run cancelled',exact:true})).toBeVisible();expect(posts).toBe(1);await expect(page.getByText(/Outstanding includes provider refunds/)).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});
