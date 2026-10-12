import {test,expect} from '@playwright/test';
const date=new Date(Date.now()+86400000*7).toISOString().slice(0,10);
const route={fromStation:'MMCT',toStation:'NDLS',dateOfJourney:date};
const q={trainNumber:'RW1001',trainName:'Mumbai - Delhi Express',...route,boardingDate:date,seatClass:'SL',passengerCount:1,farePerPassenger:870.4,totalFare:870.4,availableSeats:2,suggestedAllocation:'CNF',departureAt:date+'T10:30:00Z',arrivalAt:date+'T23:30:00Z',distanceKm:1384};
const empty={content:[],totalPages:0,totalElements:0,page:0,size:20};

test('search needs only route/date, groups all classes, and booking explicitly requotes the group',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'alice',role:'PASSENGER',accessToken:'fixture',refreshToken:'fixture'})));
 const counts:number[]=[];let fail=false;
 await page.route('**/api/**',async r=>{
  const u=new URL(r.request().url());let body:unknown=empty;
  if(u.pathname==='/api/reservations/trains'){
   expect([...u.searchParams.keys()].sort()).toEqual(['dateOfJourney','fromStation','toStation']);
   body=[{trainNumber:q.trainNumber,trainName:q.trainName,classes:[q,{...q,seatClass:'CLASS_3A',availableSeats:0,suggestedAllocation:'WL',farePerPassenger:2176},{...q,seatClass:'CLASS_2A',availableSeats:0,suggestedAllocation:'UNAVAILABLE',farePerPassenger:3194.8}]},
   {trainNumber:'12951',trainName:'Mumbai Rajdhani',classes:[{...q,trainNumber:'12951',seatClass:'CLASS_3A',availableSeats:50,farePerPassenger:2176}]}];
  }
  if(u.pathname==='/api/reservations/quote'){
   const n=Number(u.searchParams.get('passengers'));counts.push(n);
   if(fail)return r.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Please refresh your fare'})});
   body={...q,passengerCount:n,totalFare:870.4*n,suggestedAllocation:n>2?'WL':'CNF'};
  }
  if(u.pathname==='/api/reservations/notifications')body={notifications:empty,unreadCount:0};
  await r.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/');await expect(page.getByLabel('Travel class',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Passengers',{exact:true})).toHaveCount(0);
 await page.getByLabel('Departure station').fill('MMCT');await page.getByLabel('Arrival station').fill('NDLS');await page.getByLabel('Travel date').fill(date);await page.getByRole('button',{name:'Find trains',exact:true}).click();
 await expect(page.locator('.train-card')).toHaveCount(2);await expect(page.locator('.class-option')).toHaveCount(4);
 await expect(page.getByRole('heading',{name:'Mumbai Rajdhani'})).toBeVisible();await expect(page.getByRole('button',{name:'Unavailable',exact:true})).toBeDisabled();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
 await page.screenshot({path:test.info().outputPath('class-results-mobile.png'),fullPage:true});
 await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:test.info().outputPath('class-results-desktop.png'),fullPage:true});
 await page.getByRole('link',{name:'Choose SL',exact:false}).click();expect(new URL(page.url()).searchParams.has('passengers')).toBeFalsy();
 await expect(page.getByLabel('Full name')).toHaveCount(0);await expect(page.getByRole('button',{name:'Continue to checkout'})).toBeDisabled();
 await page.getByLabel('Number of passengers').selectOption({label:'2 passengers'});await expect(page.getByLabel('Full name')).toHaveCount(2);
 await page.getByLabel('Full name').first().fill('Alice');await page.getByLabel('Number of passengers').selectOption({label:'3 passengers'});
 await expect(page.getByLabel('Full name')).toHaveCount(3);await expect(page.getByLabel('Full name').first()).toHaveValue('Alice');
 await expect(page.locator('.fare-summary')).toContainText('₹2,611.20');await expect(page.locator('.fare-summary')).toContainText('Payment is required for waitlist');
 fail=true;await page.getByLabel('Number of passengers').selectOption({label:'4 passengers'});await expect(page.getByRole('alert')).toContainText('Please refresh your fare');await expect(page.getByRole('button',{name:'Continue to checkout'})).toBeDisabled();
 fail=false;await page.getByRole('button',{name:'Refresh fare & availability'}).click();await expect(page.getByRole('alert')).toHaveCount(0);await expect(page.locator('.fare-summary')).toContainText('₹3,481.60');
 expect(counts).toEqual([1,2,3,4,4]);await page.screenshot({path:test.info().outputPath('group-booking.png'),fullPage:true});
});
