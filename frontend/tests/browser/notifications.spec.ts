import {test,expect,Page} from '@playwright/test';

const sample=(id:string,title:string)=>({id,pnr:'1234567890',type:'PAYMENT_RECEIVED',title,message:'Mumbai → Goa · Train 56565. Your seats are confirmed: SL-C1-24.',createdAt:new Date().toISOString(),readAt:null as string|null});
async function setup(page:Page){
 const items=[sample('1','Your ticket is confirmed')];let polls=0;let fail=false;
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({accessToken:'fixture-token',refreshToken:'fixture-refresh',username:'alice',role:'PASSENGER'})));
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname;
  const json=(body:unknown,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(path==='/api/reservations/notifications'){
   polls++;if(fail)return json({message:'Temporarily unavailable'},503);
   const filtered=url.searchParams.get('unreadOnly')==='true'?items.filter(n=>!n.readAt):items;
   const page=Number(url.searchParams.get('page')||0),size=Number(url.searchParams.get('size')||20);
   return json({notifications:{content:filtered.slice(page*size,(page+1)*size),page,size,totalElements:filtered.length,totalPages:Math.ceil(filtered.length/size)},unreadCount:items.filter(n=>!n.readAt).length});
  }
  if(path.endsWith('/read-all')){items.forEach(n=>n.readAt=new Date().toISOString());return route.fulfill({status:204})}
  if(path.endsWith('/read')){const id=decodeURIComponent(path.split('/').at(-2)!);const item=items.find(n=>n.id===id);if(item)item.readAt=new Date().toISOString();return route.fulfill({status:204})}
  if(path==='/api/auth/logout')return route.fulfill({status:204});
  if(path==='/api/stations'||path==='/api/reservations/my-bookings')return json({content:[],page:0,size:20,totalElements:0,totalPages:0});
  return json({message:'Not found'},404);
 });
 return {items,polls:()=>polls,fail:(value:boolean)=>{fail=value}};
}

test('polls in five seconds, persists reads, filters history and clears the bell on logout',async({page})=>{
 const fixture=await setup(page);await page.goto('/notifications');
 await expect(page.getByRole('button',{name:'Notifications, 1 unread'})).toBeVisible();
 fixture.items.unshift(sample('2','Good news! Your waitlist is confirmed'));
 await expect(page.getByRole('button',{name:'Notifications, 2 unread'})).toBeVisible({timeout:7500});
 await page.getByRole('button',{name:'Notifications, 2 unread'}).click();
 const panel=page.getByRole('region',{name:'Notifications',exact:true});
 await expect(panel.getByText('Good news! Your waitlist is confirmed')).toBeVisible();
 await panel.getByRole('button',{name:'Mark all as read',exact:true}).click();
 await expect(page.getByRole('button',{name:'Notifications, 0 unread'})).toBeVisible();
 await page.keyboard.press('Escape');await expect(panel).toHaveCount(0);
 await page.getByRole('button',{name:'Unread · 0'}).click();await expect(page.getByRole('heading',{name:'You’re all caught up'})).toBeVisible();
 await page.reload();await expect(page.getByRole('button',{name:'Notifications, 0 unread'})).toBeVisible();
 await page.getByRole('button',{name:'Log out',exact:true}).click();await expect(page.getByRole('button',{name:/Notifications, /})).toHaveCount(0);
 const stopped=fixture.polls();await page.waitForTimeout(5500);expect(fixture.polls()).toBe(stopped);
});

test('mobile panel stays inside the viewport and recovery preserves notifications',async({page})=>{
 const fixture=await setup(page);await page.setViewportSize({width:390,height:844});await page.goto('/notifications');
 await page.getByRole('button',{name:'Notifications, 1 unread'}).click();
 const panel=page.getByRole('region',{name:'Notifications',exact:true});await expect(panel).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
 fixture.fail(true);await expect(panel.getByText(/Notifications could not refresh/)).toBeVisible({timeout:7500});
 await expect(panel.getByText('Your ticket is confirmed')).toBeVisible();
 fixture.fail(false);await panel.getByRole('button',{name:'Retry',exact:true}).click();await expect(panel.getByText(/Notifications could not refresh/)).toHaveCount(0);
 await panel.getByRole('button',{name:/Your ticket is confirmed/}).click();await expect(page).toHaveURL(/\/bookings\/1234567890$/);
 expect(fixture.items[0].readAt).not.toBeNull();
});
