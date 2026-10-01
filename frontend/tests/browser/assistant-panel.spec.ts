import {test,expect} from '@playwright/test';

test('assistant panel retains the conversation and requires an explicit booking count',async({page})=>{
 const day=new Date(Date.now()+86400000).toISOString().slice(0,10);
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'alice',role:'PASSENGER',accessToken:'fixture',refreshToken:'fixture'})));
 let created=0;
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path.endsWith('/messages')){
   const event=(type:string,text='',cards:unknown[]=[])=>'data: '+JSON.stringify({type,text,cards,sources:[]})+'\n\n';
   await route.fulfill({contentType:'text/event-stream',body:event('delta','Here is your train option.')+event('cards','',[{kind:'TRAIN',title:'Fixture Express',trainNumber:'T1',fromStation:'AAA',toStation:'BBB',dateOfJourney:day,seatClass:'SL',passengers:1,status:'CNF',fare:100,availableSeats:10,departureAt:day+'T10:00:00Z'}])+event('done','complete')});return;
  }
  let body:unknown={content:[],totalElements:0,totalPages:0,page:0,size:10};
  if(path==='/api/chat/conversations'){created++;body={id:'test-chat',messages:[],expiresAt:'2030-01-01T00:00:00Z'};}
  if(path==='/api/reservations/notifications')body={notifications:body,unreadCount:0};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/bookings');await page.getByRole('button',{name:'Ask Railwize +'}).click();
 await expect(page).toHaveURL(/\/bookings$/);const panel=page.getByRole('dialog',{name:'Ask Railwize'});
 await panel.getByRole('textbox',{name:'Your message'}).fill('Find a train');await panel.getByRole('button',{name:'Send message'}).click();
 await expect(panel.getByText('Here is your train option.')).toBeVisible();
 await panel.getByRole('link',{name:'Open full assistant page'}).click();
 await expect(page).toHaveURL(/\/assistant$/);await expect(page.getByText('Here is your train option.')).toBeVisible();expect(created).toBe(1);
 await page.getByRole('button',{name:'Book now'}).click();
 await expect(page.getByRole('button',{name:'Continue to passenger details'})).toBeDisabled();
 await page.getByRole('combobox',{name:'Number of passengers'}).selectOption({label:'3 passengers'});
 await page.getByRole('button',{name:'Continue to passenger details'}).click();await expect(page).toHaveURL(/passengers=3/);
 await page.getByRole('button',{name:'Ask Railwize +'}).click();await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
 await page.getByRole('button',{name:'Close assistant'}).click();await expect(page.getByRole('dialog',{name:'Ask Railwize'})).toHaveCount(0);
});
