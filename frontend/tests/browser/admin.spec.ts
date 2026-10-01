import {test,expect} from '@playwright/test';

test('admin user status requires confirmation and history uses the stable account key',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'operator',role:'ADMIN',accessToken:'fixture',refreshToken:'fixture'})));
 let blocked=false,updates=0;
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname;
  let body:unknown={content:[],page:0,size:20,totalElements:0,totalPages:0};
  if(p==='/api/reservations/notifications')body={notifications:body,unreadCount:0};
  if(p==='/api/auth/admin/users')body={content:[{id:1,accountUsername:'google_stable',username:'asha',email:'asha@example.test',role:'PASSENGER',status:blocked?'BLOCKED':'ACTIVE',provider:'GOOGLE',verified:true}],page:0,size:20,totalElements:1,totalPages:1};
  if(p==='/api/auth/admin/users/google_stable/status'){updates++;blocked=true;expect(route.request().postDataJSON()).toEqual({status:'BLOCKED'});body={status:'BLOCKED'};}
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/admin/users');await page.getByRole('button',{name:'Block',exact:true}).click();expect(updates).toBe(0);
 await page.getByRole('button',{name:'Keep current status'}).click();expect(updates).toBe(0);
 await page.getByRole('button',{name:'Block',exact:true}).click();await page.getByRole('button',{name:'Confirm block',exact:true}).click();
 await expect(page.getByRole('button',{name:'Reactivate',exact:true})).toBeVisible();expect(updates).toBe(1);
 await page.getByRole('row').filter({hasText:'asha@example.test'}).getByRole('link',{name:'Bookings',exact:true}).click();
 await expect(page).toHaveURL(/username=google_stable/);await expect(page.getByLabel('Account (exact)')).toHaveValue('google_stable');
});

test('report exports use applied filters and report is usable on mobile',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'operator',role:'ADMIN',accessToken:'fixture',refreshToken:'fixture'})));
 let exportTrain:string|null=null;
 const totals={label:'All',bookings:2,cancellations:1,paidBookings:2,gross:200,refunded:80,outstandingRefunds:0,net:120};
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname;
  if(p.endsWith('/export')){exportTrain=url.searchParams.get('trainNumber');await route.fulfill({contentType:'text/csv;charset=UTF-8',body:'\uFEFFTrain,Net\r\nT1,120\r\n'});return;}
  let body:unknown={content:[],page:0,size:20,totalElements:0,totalPages:0};
  if(p==='/api/reservations/notifications')body={notifications:body,unreadCount:0};
  if(p==='/api/reservations/admin/reports')body={fromDate:url.searchParams.get('fromDate'),toDate:url.searchParams.get('toDate'),groupBy:'train',dateBasis:'JOURNEY_DATE',totals,rows:[{...totals,label:'T1'}],generatedAt:new Date().toISOString()};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/admin/reports');await page.getByLabel('Train number',{exact:true}).fill('T1');await page.getByRole('button',{name:'Apply filters'}).click();
 await expect(page.getByText(/Applied:.*T1/)).toBeVisible();await page.getByLabel('Train number',{exact:true}).fill('UNAPPLIED');
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'Export report CSV'}).click();await download;expect(exportTrain).toBe('T1');
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});

test('passenger cannot navigate into admin management',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'alice',role:'PASSENGER',accessToken:'fixture',refreshToken:'fixture'})));
 await page.route('**/api/**',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({notifications:{content:[],page:0,size:20,totalElements:0,totalPages:0},unreadCount:0})}));
 await page.goto('/admin/users');await expect(page).toHaveURL(/\/forbidden/);
});
