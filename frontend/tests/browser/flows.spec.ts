import {test,expect,Page,Route} from '@playwright/test';
const travel=new Date(Date.now()+86400000).toISOString().slice(0,10);
const stations=[{stationId:1,stationCode:'DSTA',stationName:'Demo Origin',city:'Mumbai',state:'Maharashtra'},{stationId:2,stationCode:'DSTB',stationName:'Demo Junction',city:'Pune'},{stationId:3,stationCode:'DSTC',stationName:'Demo Destination',city:'Bengaluru'}];
const quote={trainNumber:'DM001',trainName:'Demo Small Capacity Express',fromStation:'DSTA',toStation:'DSTC',seatClass:'SL',boardingDate:travel,runDate:travel,departureAt:travel+'T04:35:00Z',arrivalAt:travel+'T06:30:00Z',distanceKm:200,passengerCount:1,farePerPassenger:120,totalFare:120,baseFare:20,ratePerKm:.5,minimumFare:40,availableSeats:2,suggestedAllocation:'CNF',waitlistOpen:true,waitlistDeadline:travel+'T03:35:00Z'};
const passenger={name:'Demo Traveller',age:25,gender:'OTHER',birthPreference:'LOWER',seatNumber:'S1-1'};
const booking={...quote,pnr:'1234567890',dateOfJourney:travel,holdExpiresAt:new Date(Date.now()+120000).toISOString(),allocation:'CNF',status:'PENDING_PAYMENT',paymentStatus:'PENDING',refundStatus:'NONE',refundAmount:0,waitlistPosition:null,legacyBooking:false,bookingTime:new Date().toISOString(),passengers:[passenger]};
const train={trainId:1,trainNumber:'DM001',trainName:'Demo Small Capacity Express',category:'EXPRESS',sourceStationId:1,destinationStationId:3,seatClasses:['SL'],operatingDays:['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'],classConfigurations:[{seatClass:'SL',coachCount:1,seatsPerCoach:2,baseFare:20,ratePerKm:.5,minimumFare:40}]};
const session={accessToken:'test-access-token',refreshToken:'test-refresh-token',username:'demo_user',role:'PASSENGER'};
const pageOf=(content:unknown[])=>({content,page:0,size:10,totalElements:content.length,totalPages:content.length?1:0});
async function json(route:Route,data:unknown,status=200){await route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)})}
async function auth(page:Page,role='PASSENGER'){await page.addInitScript(s=>{if(!sessionStorage.getItem('railwize.session.v1'))sessionStorage.setItem('railwize.session.v1',JSON.stringify(s))},{...session,role})}
async function mock(page:Page){await page.route('**/api/**',async route=>{const u=new URL(route.request().url()),p=u.pathname;
 if(p==='/api/stations')return json(route,pageOf(stations));
 if(p==='/api/trains')return json(route,pageOf([train]));
 if(p==='/api/reservations/trains')return json(route,[{trainNumber:quote.trainNumber,trainName:quote.trainName,classes:[quote]}]);
 if(p==='/api/reservations/quote'){const count=Number(u.searchParams.get('passengers')||1);return json(route,{...quote,passengerCount:count,totalFare:120*count})}
 if(p==='/api/users/me/passengers')return json(route,pageOf([{id:1,...passenger,berthPreference:'LOWER'}]));
 if(p==='/api/reservations/my-bookings')return json(route,pageOf([booking]));
 if(p==='/api/reservations/pnr/1234567890')return json(route,booking);
 if(p==='/api/payments/1234567890/status')return json(route,{pnr:booking.pnr,paymentStatus:'PENDING',bookingStatus:'PENDING_PAYMENT',payments:[],mode:'TEST'});
 if(p==='/api/auth/admin/invitations')return json(route,pageOf([]));
 if(p==='/api/auth/login')return json(route,session);
 if(p==='/api/auth/username')return json(route,{username:'demo_user',canChange:true,changedAt:null});
 if(p==='/api/auth/logout'||p==='/api/auth/logout-all')return route.fulfill({status:204});
 return json(route,{message:'Fixture endpoint not configured',status:404},404);
})}
test.beforeEach(async({page})=>{await mock(page)});

test('home is responsive and validates identical stations before search',async({page})=>{
 await page.goto('/');await expect(page.getByRole('heading',{name:'A better way to get there.'})).toBeVisible();
 await page.getByLabel('Departure station').fill('DSTA');await page.getByLabel('Arrival station').fill(' dsta ');await page.getByRole('button',{name:'Find trains',exact:true}).click();await expect(page.getByRole('alert')).toContainText('different stations');
 await page.screenshot({path:'test-results/home-desktop.png',fullPage:true});
 await page.setViewportSize({width:390,height:844});await expect(page.getByRole('heading',{name:'A better way to get there.'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
 await page.getByRole('button',{name:'Toggle navigation'}).click();await expect(page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'My journeys'})).toBeVisible();await page.getByRole('button',{name:'Toggle navigation'}).click();await page.screenshot({path:'test-results/home-mobile.png',fullPage:true});
});
test('search selects a train and preserves return journey through sign-in',async({page})=>{
 await page.goto('/');await page.getByLabel('Departure station').fill('DSTA');await page.getByLabel('Arrival station').fill('DSTC');await page.getByRole('button',{name:'Find trains',exact:true}).click();await expect(page.getByText('Demo Small Capacity Express')).toBeVisible();await page.getByRole('link',{name:'Choose SL'}).click();await expect(page).toHaveURL(/auth\/login\?returnUrl/);await page.getByLabel('Username or email').fill('demo_user');await page.getByLabel('Password',{exact:true}).fill('DemoPass@123');await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.getByRole('heading',{name:'Who’s coming along?'})).toBeVisible();await page.getByLabel('Number of passengers').selectOption({label:'1 passenger'});await page.getByLabel('Choose saved passenger').selectOption('1');await expect(page.getByLabel('Full name')).toHaveValue('Demo Traveller');
 await page.screenshot({path:'test-results/passenger-details.png',fullPage:true});
});
test('uncertain booking retries keep the same idempotency key and body',async({page})=>{
 await auth(page);let attempts=0;const keys:string[]=[],bodies:string[]=[];
 await page.route('**/api/reservations/book',async route=>{attempts++;keys.push(route.request().headers()['idempotency-key']);bodies.push(route.request().postData()!);if(attempts===1)return route.abort('failed');return json(route,booking,201)});
 await page.goto(`/book/DM001?fromStation=DSTA&toStation=DSTC&dateOfJourney=${travel}&seatClass=SL&passengers=1`);await page.getByLabel('Number of passengers').selectOption({label:'1 passenger'});await page.getByLabel('Choose saved passenger').selectOption('1');await page.getByRole('checkbox').check();await page.getByRole('button',{name:'Continue to checkout'}).click();await expect(page.getByRole('button',{name:'Retry the same booking'})).toBeVisible();await expect(page.getByLabel('Full name')).toBeDisabled();await page.getByRole('button',{name:'Retry the same booking'}).click();await expect(page).toHaveURL(/bookings\/1234567890/);expect(keys[0]).toBeTruthy();expect(keys[1]).toEqual(keys[0]);expect(bodies[1]).toEqual(bodies[0]);await expect(page.getByLabel('Time remaining')).toBeVisible();
});
test('signed test checkout is verified; a successful WL payment is not shown as confirmed',async({page})=>{
 await auth(page);let paid=false;let verified:unknown;
 await page.route('**/api/reservations/pnr/1234567890',route=>json(route,{...booking,allocation:'WL',status:paid?'WAITING':'PENDING_PAYMENT',paymentStatus:paid?'SUCCESS':'PENDING',waitlistPosition:paid?1:null}));
 await page.route('**/api/payments/1234567890/status',route=>json(route,{pnr:booking.pnr,paymentStatus:paid?'SUCCESS':'PENDING',bookingStatus:paid?'WAITING':'PENDING_PAYMENT',payments:paid?[{razorpayPaymentId:'pay_test',bookingConfirmed:false,refundAmount:0,refundStatus:'NONE'}]:[],mode:'TEST'}));
 await page.route('**/api/payments/create-order',route=>{expect(route.request().postDataJSON()).toEqual({pnr:'1234567890'});return json(route,{pnr:'1234567890',keyId:'rzp_test_local',razorpayOrderId:'order_test',amountInPaise:12000,currency:'INR',mode:'TEST'},201)});
 await page.route('**/api/payments/verify',route=>{verified=route.request().postDataJSON();paid=true;return json(route,{paymentStatus:'SUCCESS',bookingStatus:'WAITING'})});
 await page.route('https://checkout.razorpay.com/v1/checkout.js',route=>route.fulfill({contentType:'application/javascript',body:`window.Razorpay=class{constructor(o){this.o=o}on(){}close(){}open(){this.o.handler({razorpay_order_id:'order_test',razorpay_payment_id:'pay_test',razorpay_signature:'signed-fixture'})}}` }));
 await page.goto('/bookings/1234567890');await page.getByRole('button',{name:/^Pay /}).click();await expect(page.getByText(/You’re on the waitlist/)).toBeVisible();await expect(page.getByText('Waitlisted',{exact:true})).toBeVisible();expect(verified).toEqual({razorpayOrderId:'order_test',razorpayPaymentId:'pay_test',razorpaySignature:'signed-fixture'});await page.screenshot({path:'test-results/waitlisted-ticket.png',fullPage:true});
});
test('live checkout keys are rejected before any checkout opens',async({page})=>{
 await auth(page);await page.route('**/api/payments/create-order',route=>json(route,{pnr:'1234567890',keyId:'rzp_live_blocked',mode:'LIVE'}));await page.route('https://checkout.razorpay.com/v1/checkout.js',route=>route.fulfill({contentType:'application/javascript',body:'window.Razorpay=class{open(){throw new Error("Must not open")}}'}));await page.goto('/bookings/1234567890');await page.getByRole('button',{name:/^Pay /}).click();await expect(page.getByRole('alert')).toContainText('Only Razorpay test checkout');
});
test('changed cancellation preview requires review again before cancellation',async({page})=>{
 await auth(page);let previews=0,cancelled=0;
 await page.route('**/api/reservations/pnr/1234567890',route=>json(route,{...booking,status:'WAITING'}));
 await page.route('**/api/reservations/1234567890/cancellation-preview',route=>{previews++;return json(route,{pnr:booking.pnr,currentStatus:previews===1?'WAITING':'CONFIRMED',cancellationFee:previews===1?0:24,refundAmount:previews===1?120:96,originalFare:120})});
 await page.route('**/api/reservations/1234567890',route=>{cancelled++;return json(route,{message:'Booking cancelled.'})});
 await page.goto('/bookings/1234567890');await page.getByRole('button',{name:'Review cancellation'}).click();await page.getByRole('button',{name:'Confirm cancellation'}).click();await expect(page.getByText(/Your booking or refund estimate changed/)).toBeVisible();expect(cancelled).toBe(0);await page.getByRole('button',{name:'Confirm cancellation'}).click();await expect.poll(()=>cancelled).toBe(1);
});
test('booking filters are sent as journey-date filters and paging parameters',async({page})=>{
 await auth(page);const requests:URL[]=[];await page.route('**/api/reservations/my-bookings**',route=>{requests.push(new URL(route.request().url()));return json(route,pageOf([]))});await page.goto('/bookings');await page.getByLabel('Status',{exact:true}).selectOption('WL');await page.getByLabel('Travel from').fill(travel);await page.getByLabel('Travel until').fill(travel);await page.getByRole('button',{name:'Apply filters'}).click();await expect.poll(()=>requests.at(-1)?.searchParams.get('status')).toBe('WL');expect(requests.at(-1)?.searchParams.get('fromDate')).toBe(travel);expect(requests.at(-1)?.searchParams.get('page')).toBe('0');
});
test('expired access is refreshed before retry; logout clears tokens',async({page})=>{
 await auth(page);let refreshCount=0;
 await page.route('**/api/auth/refresh',async route=>{refreshCount++;await new Promise(r=>setTimeout(r,100));await json(route,{...session,accessToken:'new-access-token'})});
 await page.route('**/api/users/me',route=>route.request().headers()['authorization']==='Bearer new-access-token'?json(route,{firstName:'Demo',lastName:'Traveller',phoneNumber:'',gender:'OTHER',dateOfBirth:'2000-01-01',address:'Mumbai'}):json(route,{message:'Expired'},401));
 await page.goto('/account');await expect(page.getByLabel('First name')).toHaveValue('Demo');expect(refreshCount).toBe(1);await page.getByRole('button',{name:'Log out',exact:true}).click();await expect(page).toHaveURL('/');expect(await page.evaluate(()=>sessionStorage.getItem('railwize.session.v1'))).toBeNull();
});
test('passenger cannot enter admin; admin can create station and retains fixed train settings',async({page})=>{
 await auth(page);await page.goto('/admin');await expect(page.getByRole('heading',{name:'This stop is for admins.'})).toBeVisible();
 await page.evaluate(s=>sessionStorage.setItem('railwize.session.v1',JSON.stringify(s)),{...session,role:'ADMIN'});

 let created:unknown;await page.route('**/api/stations**',route=>{if(route.request().method()==='POST'){created=route.request().postDataJSON();return json(route,{stationId:4,...created as object},201)}return json(route,pageOf(stations))});
 await page.goto('/admin/stations');await page.getByRole('button',{name:'Add station'}).click();await page.getByLabel('Station code').fill(' new ');await page.getByLabel('Station name').fill('New Station');await page.getByRole('button',{name:'Save station'}).click();await expect(page.getByRole('status')).toContainText('Station saved');expect((created as {stationCode:string}).stationCode).toBe('NEW');
 await page.getByRole('link',{name:'Trains & fares'}).click();await page.getByRole('row').filter({hasText:'DM001'}).getByRole('button',{name:'Edit',exact:true}).click();await expect(page.getByLabel('Train number')).toHaveAttribute('readonly','');await expect(page.getByLabel('Seats per coach')).toHaveAttribute('readonly','');await expect(page.getByLabel('Base fare (INR)')).not.toHaveAttribute('readonly','');await page.screenshot({path:'test-results/admin-train.png',fullPage:true});
});
test('backend field errors and request references are visible',async({page})=>{
 await page.route('**/api/auth/login',route=>json(route,{status:400,message:'Request validation failed',fieldErrors:{identifier:'Account not verified'},requestId:'trace-123'},400));await page.goto('/auth/login');await page.getByLabel('Username or email').fill('demo_user');await page.getByLabel('Password',{exact:true}).fill('DemoPass@123');await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Account not verified');await expect(page.getByRole('alert')).toContainText('trace-123');await page.screenshot({path:'test-results/sign-in.png',fullPage:true});
});

test('saved passengers can be created, edited and removed',async({page})=>{
 await auth(page);let people:any[]=[];
 await page.route('**/api/users/me/passengers**',route=>{const method=route.request().method();if(method==='POST'){people=[{id:7,...route.request().postDataJSON()}];return json(route,people[0],201)}if(method==='PUT'){people=[{id:7,...route.request().postDataJSON()}];return json(route,people[0])}if(method==='DELETE'){people=[];return route.fulfill({status:204})}return json(route,pageOf(people))});
 await page.goto('/passengers');await page.getByRole('button',{name:'Add passenger',exact:true}).click();await page.getByLabel('Full name').fill('Test Traveller');await page.getByLabel('Age',{exact:true}).fill('30');await page.locator('select[name="gender"]').selectOption('FEMALE');await page.getByRole('button',{name:'Save passenger'}).click();await expect(page.getByRole('heading',{name:'Test Traveller'})).toBeVisible();await page.getByRole('button',{name:'Edit Test Traveller'}).click();await page.getByLabel('Age',{exact:true}).fill('31');await page.getByRole('button',{name:'Save passenger'}).click();await expect(page.getByText('31 years')).toBeVisible();await page.getByRole('button',{name:'Delete Test Traveller'}).click();await page.getByRole('button',{name:'Remove',exact:true}).click();await expect(page.getByRole('heading',{name:'Your people belong here.'})).toBeVisible();expect(people).toEqual([]);
});
test('Google callback exchanges the one-time code and restores the selected page',async({page})=>{
 let exchange:any;await page.addInitScript(()=>sessionStorage.setItem('railwize.returnTo','/passengers'));await page.route('**/api/auth/google/exchange',route=>{exchange=route.request().postDataJSON();return json(route,session)});await page.goto('/auth/callback?code=single-use-fixture');await expect(page).toHaveURL('/passengers');await expect(page.getByRole('heading',{name:'Saved passengers.'})).toBeVisible();expect(exchange).toEqual({code:'single-use-fixture'});expect(await page.evaluate(()=>sessionStorage.getItem('railwize.returnTo'))).toBeNull();
});
test('admin invitation token is removed from URL and accepting requires a fresh login',async({page})=>{
 await auth(page);let accepted:any;await page.route('**/api/auth/admin-invitations/accept',route=>{accepted=route.request().postDataJSON();return json(route,{message:'Accepted'})});await page.goto('/admin/accept-invitation#token=one-time-invite');await expect(page).toHaveURL('/admin/accept-invitation');await page.getByRole('button',{name:'Accept administrator invitation'}).click();await expect(page.getByRole('link',{name:'Sign in again as admin'})).toBeVisible();expect(accepted).toEqual({token:'one-time-invite'});expect(await page.evaluate(()=>sessionStorage.getItem('railwize.session.v1'))).toBeNull();expect(await page.evaluate(()=>sessionStorage.getItem('railwize.invitation'))).toBeNull();
});
test('admin publishes an ordered timetable and its stops become read-only',async({page})=>{
 await auth(page,'ADMIN');let published:any;await page.route('**/api/schedules/DM001',route=>{if(route.request().method()==='POST'){published=route.request().postDataJSON();return json(route,published,201)}return json(route,{message:'No timetable yet'},404)});await page.goto('/admin/schedules');await page.locator('select[name="train"]').selectOption('DM001');await page.getByRole('button',{name:'Load timetable'}).click();await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(0)).toHaveValue('DSTA');await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(1)).toHaveValue('DSTC');await page.getByLabel('Departure time',{exact:true}).nth(0).fill('10:00');await page.getByLabel('Arrival time',{exact:true}).nth(1).fill('12:00');await page.getByLabel('Distance from origin (km)',{exact:true}).nth(1).fill('200');await page.getByRole('button',{name:'Publish timetable'}).click();await expect(page.getByRole('heading',{name:'Published timetable'})).toBeVisible();await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(0)).toBeDisabled();expect(published.map((s:any)=>s.stopSequence)).toEqual([1,2]);expect(published[0].arrivalTime).toBeNull();expect(published[1].departureTime).toBeNull();
});

test('timetable endpoints stay fixed while intermediate stops are inserted and removed',async({page})=>{
 await auth(page,'ADMIN');
 await page.route('**/api/schedules/DM001',route=>json(route,{message:'No timetable yet'},404));
 await page.goto('/admin/schedules');await page.locator('select[name="train"]').selectOption('DM001');
 await page.getByRole('button',{name:'Load timetable'}).click();
 await expect(page.getByLabel('Arrival time',{exact:true}).nth(0)).toBeDisabled();
 await expect(page.getByLabel('Departure time',{exact:true}).nth(1)).toBeDisabled();
 await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(0)).toBeDisabled();
 await page.getByRole('button',{name:'Add intermediate stop'}).click();
 await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(2)).toHaveValue('DSTC');
 await expect(page.getByLabel('Departure time',{exact:true}).nth(2)).toBeDisabled();
 await expect(page.getByLabel('Arrival time',{exact:true}).nth(1)).toBeEnabled();
 await expect(page.getByLabel('Departure time',{exact:true}).nth(1)).toBeEnabled();
 await page.getByRole('button',{name:'Remove stop 2'}).click();
 await expect(page.getByRole('combobox',{name:'Station',exact:true}).nth(1)).toHaveValue('DSTC');
});

test('saved passenger load failure can be retried and selection avoids duplicates across rows',async({page})=>{
 await auth(page);let failed=true;
 await page.route('**/api/users/me/passengers**',route=>{
  if(failed)return json(route,{message:'Temporary failure'},503);
  const second=new URL(route.request().url()).searchParams.get('page')==='1';
  return json(route,{content:[{id:second?2:1,...passenger,name:second?'Second Traveller':'Demo Traveller',berthPreference:'LOWER'}],page:second?1:0,size:1,totalPages:2,totalElements:2});
 });
 await page.goto(`/book/DM001?fromStation=DSTA&toStation=DSTC&dateOfJourney=${travel}&seatClass=SL&passengers=2`);await page.getByLabel('Number of passengers').selectOption({label:'2 passengers'});
 await expect(page.getByText(/Saved passengers could not load/)).toBeVisible();
 failed=false;await page.getByRole('button',{name:'Retry saved passengers'}).click();
 const selectors=page.getByLabel('Choose saved passenger');
 await selectors.nth(0).selectOption('1');
 await expect(selectors.nth(1).locator('option[value="1"]')).toHaveJSProperty('disabled',true);
 await selectors.nth(1).selectOption('2');
 await expect(page.getByLabel('Full name').nth(1)).toHaveValue('Second Traveller');
 await page.getByLabel('Full name').nth(1).fill('Edited for this booking');
 await selectors.nth(0).selectOption('');
 await expect(selectors.nth(1).locator('option[value="1"]')).toHaveJSProperty('disabled',false);
});

test('empty master list keeps manual booking available',async({page})=>{
 await auth(page);await page.route('**/api/users/me/passengers**',route=>json(route,pageOf([])));
 await page.goto(`/book/DM001?fromStation=DSTA&toStation=DSTC&dateOfJourney=${travel}&seatClass=SL&passengers=1`);await page.getByLabel('Number of passengers').selectOption({label:'1 passenger'});
 await expect(page.getByText(/No saved passengers yet/)).toBeVisible();
 await expect(page.getByLabel('Full name')).toBeEnabled();
 await expect(page.getByLabel('Choose saved passenger')).toBeDisabled();
});

test('password and Google admin sign-in go to the admin workspace',async({page})=>{
 await page.route('**/api/auth/login',route=>json(route,{...session,role:'ADMIN'}));
 await page.goto('/auth/login');await page.getByLabel('Username or email').fill('admin');
 await page.getByLabel('Password',{exact:true}).fill('DemoPass@123');await page.getByRole('button',{name:'Sign in',exact:true}).click();
 await expect(page).toHaveURL('/admin');
 await page.evaluate(()=>sessionStorage.clear());
 await page.route('**/api/auth/google/exchange',route=>json(route,{...session,role:'ADMIN'}));
 await page.goto('/auth/callback?code=admin-code');await expect(page).toHaveURL('/admin');
});

test('new account profile is editable when backend returns empty fields',async({page})=>{
 await auth(page);await page.route('**/api/users/me',route=>json(route,{username:'demo_user',firstName:null,lastName:null,phoneNumber:null,gender:null,dateOfBirth:null,address:null}));
 await page.goto('/account');await expect(page.getByText('Welcome! Add your details to complete your profile.')).toBeVisible();
 await expect(page.getByLabel('First name')).toHaveValue('');
 await expect(page.locator('select[name="gender"]')).toHaveValue('');
});

test('Google account can choose its username once without changing its booking owner',async({page})=>{
 let changed=false;
 await page.route('**/api/auth/google/exchange',route=>json(route,{...session,username:'google_1234567890123',displayUsername:'google_1234567890123',usernameChangeAllowed:true,usernameSetupSuggested:true}));
 await page.route('**/api/users/me',route=>json(route,{username:'google_1234567890123',firstName:null,address:null}));
 await page.route('**/api/auth/username',route=>{
  if(route.request().method()==='PUT'){expect(route.request().postDataJSON()).toEqual({username:'my_travels'});changed=true;}
  return json(route,{username:changed?'my_travels':'google_1234567890123',canChange:!changed,changedAt:changed?new Date().toISOString():null});
 });
 await page.goto('/auth/callback?code=google-username-choice');await expect(page).toHaveURL(/\/account\?returnUrl=/);
 await page.getByLabel(/^New username/).fill('My_Travels');
 await page.getByLabel('I understand that this is my one username change.').check();
 await page.getByRole('button',{name:'Save username',exact:true}).click();
 await expect(page.getByText('Your one username change has been used.',{exact:false})).toBeVisible();
 const stored=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('railwize.session.v1')!));
 expect(stored.username).toBe('google_1234567890123');expect(stored.displayUsername).toBe('my_travels');
 await expect(page.locator('.account-link')).toContainText('my_travels');
});

test('unavailable username is explained and the form remains editable',async({page})=>{
 await auth(page);await page.route('**/api/auth/username',route=>route.request().method()==='PUT'?json(route,{message:'That username is already taken. Choose another.'},409):json(route,{username:'demo_user',canChange:true,changedAt:null}));
 await page.goto('/account');await page.getByLabel(/^New username/).fill('taken_name');
 await page.getByLabel('I understand that this is my one username change.').check();await page.getByRole('button',{name:'Save username',exact:true}).click();
 await expect(page.getByText('That username is already taken. Choose another.',{exact:false})).toBeVisible();
 await expect(page.getByLabel(/^New username/)).toBeEnabled();
});

test('recent searches persist, restore details and support remove and clear',async({page})=>{
 await auth(page);
 await page.goto('/');await page.getByLabel('Departure station').fill('DSTA');await page.getByLabel('Arrival station').fill('DSTC');await page.getByLabel('Travel date').fill(travel);await page.getByRole('button',{name:'Find trains',exact:true}).click();
 await expect(page.getByRole('region',{name:'Recent searches'})).toBeVisible();await page.reload();
 const history=page.getByRole('region',{name:'Recent searches'});await expect(history.getByRole('listitem')).toHaveCount(1);
 await page.getByLabel('Departure station').fill('DSTB');await history.getByRole('button',{name:/Reuse search/}).click();await expect(page.getByLabel('Departure station')).toHaveValue('DSTA');await expect(page.getByLabel('Passengers',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Find trains',exact:true}).click();await expect(history.getByRole('listitem')).toHaveCount(1);
 await history.getByRole('button',{name:/Remove search/}).click();await expect(history).toHaveCount(0);
 await page.getByRole('button',{name:'Find trains',exact:true}).click();await history.getByRole('button',{name:'Clear all',exact:true}).click();await page.reload();await expect(history).toHaveCount(0);
 expect(await page.evaluate(()=>localStorage.getItem('railwize.recent-searches.v2.demo_user'))).toBeNull();
});

test('a historic search restores its route but requires a new travel date',async({page})=>{
 await auth(page);
 await page.addInitScript(()=>localStorage.setItem('railwize.recent-searches.v2.demo_user',JSON.stringify([{fromStation:'DSTA',toStation:'DSTC',dateOfJourney:'2020-01-01',seatClass:'SL',passengers:3}])));
 await page.goto('/');await page.getByRole('button',{name:/Reuse search/}).click();await expect(page.getByLabel('Travel date')).toHaveValue('');await expect(page.getByLabel('Departure station')).toHaveValue('DSTA');await expect(page.getByRole('status')).toContainText('Choose a new travel date');
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});

test('confirmed ticket downloads a calendar file and rechecks status before export',async({page})=>{
 await auth(page);let status='CONFIRMED';
 await page.route('**/api/reservations/pnr/1234567890',route=>json(route,{...booking,status,paymentStatus:'PAID'}));
 await page.goto('/bookings/1234567890');
 const button=page.getByRole('button',{name:'Add to calendar',exact:true});await expect(button).toBeVisible();
 const downloaded=page.waitForEvent('download');await button.click();const download=await downloaded;expect(download.suggestedFilename()).toBe('Railwize-1234567890.ics');
 const stream=await download.createReadStream();const chunks:Buffer[]=[];if(stream)for await(const chunk of stream)chunks.push(Buffer.from(chunk));const content=Buffer.concat(chunks).toString('utf8');
 expect(content).toContain('BEGIN:VCALENDAR');expect(content).toContain('TRIGGER:-PT1H');expect(content).not.toContain(passenger.name);
 status='CANCELLED';await button.click();await expect(page.getByRole('alert')).toContainText('confirmed bookings');await expect(button).toHaveCount(0);
});
