import {test,expect} from '@playwright/test';
const id='SUP-12345678-1234-1234-1234-123456789012';
const ticket={id,subject:'Refund question',category:'PAYMENT_REFUND',status:'OPEN',pnr:null,username:'alice',email:null,createdAt:'2030-01-01T10:00:00Z',updatedAt:'2030-01-01T10:00:00Z',version:0};
function pageOf(content:unknown[]){return {content,page:0,size:20,totalElements:content.length,totalPages:content.length?1:0}}
test('passenger creates a request, retries safely and reads escaped conversation content on mobile',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'alice',role:'USER',accessToken:'fixture',refreshToken:'fixture'})));
 const sent:any[]=[];let fail=true;const messages=[{id:'m1',author:'alice',staff:false,systemMessage:false,body:'<script>not executable</script>',createdAt:ticket.createdAt}];
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let body:unknown=pageOf([]);
  if(path==='/api/reservations/notifications')body={notifications:pageOf([]),unreadCount:0};
  if(path==='/api/reservations/support'&&route.request().method()==='POST'){
   sent.push(route.request().postDataJSON());if(fail){fail=false;return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Please retry'})})}body=ticket;
  }else if(path==='/api/reservations/support/'+id)body=ticket;
  else if(path==='/api/reservations/support/'+id+'/messages')body=pageOf(messages);
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/help');await page.getByRole('link',{name:'Contact support',exact:false}).click();
 await page.getByRole('button',{name:'New support request'}).click();
 await page.getByLabel('Subject',{exact:true}).fill('Refund question');await page.getByLabel('Message',{exact:true}).fill('Please check my refund.');
 await page.getByRole('button',{name:'Send support request',exact:false}).click();await expect(page.getByText('Please retry',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Send support request',exact:false}).click();await expect(page).toHaveURL(new RegExp('/support/'+id));
 expect(sent.length).toBe(2);expect(sent[0].requestId).toBe(sent[1].requestId);expect(sent[0].pnr).toBeNull();
 await expect(page.getByText('<script>not executable</script>',{exact:true})).toBeVisible();expect(await page.locator('.thread-message script').count()).toBe(0);
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await page.screenshot({path:test.info().outputPath('support-mobile.png'),fullPage:true});
});
test('admin replies and resolves using the latest version',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('railwize.session.v1',JSON.stringify({username:'operator',role:'ADMIN',accessToken:'fixture',refreshToken:'fixture'})));
 let current={...ticket,email:'alice@example.test'};const messages:any[]=[];let replies=0,statusUpdates=0;
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let body:unknown=pageOf([]);
  if(path==='/api/reservations/notifications')body={notifications:pageOf([]),unreadCount:0};
  if(path.endsWith('/support/'+id+'/messages')){
   if(route.request().method()==='POST'){replies++;const input=route.request().postDataJSON();messages.unshift({id:'reply',author:'operator',staff:true,systemMessage:false,body:input.message,createdAt:ticket.createdAt});current={...current,status:'IN_PROGRESS',version:1};body=current}else body=pageOf(messages);
  }else if(path.endsWith('/support/'+id+'/status')){statusUpdates++;expect(route.request().postDataJSON()).toEqual({status:'RESOLVED',version:1});current={...current,status:'RESOLVED',version:2};body=current}
  else if(path.endsWith('/support/'+id))body=current;
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/admin/support/'+id);await expect(page.getByText('alice · alice@example.test',{exact:true})).toBeVisible();
 await page.getByLabel('Your reply',{exact:true}).fill('We have checked the update.');await page.getByRole('button',{name:'Send reply',exact:false}).click();
 await expect(page.getByText('We have checked the update.',{exact:true})).toBeVisible();expect(replies).toBe(1);
 await page.getByLabel('Status',{exact:true}).selectOption('RESOLVED');await page.getByRole('button',{name:'Update status',exact:true}).click();
 await expect(page.locator('.support-status')).toHaveText('Resolved');expect(statusUpdates).toBe(1);await page.screenshot({path:test.info().outputPath('support-admin.png'),fullPage:true});
});
