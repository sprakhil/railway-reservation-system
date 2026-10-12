import {test,expect} from '@playwright/test';
test('automatically finds alternatives and links each overnight leg using its actual stations and date',async({page})=>{
 const date=new Date(Date.now()+86400000).toISOString().slice(0,10),next=new Date(Date.now()+2*86400000).toISOString().slice(0,10);
 const leg=(trainNumber:string,fromStation:string,toStation:string,boardingDate:string,departureAt:string,arrivalAt:string)=>({trainNumber,trainName:'Test '+trainNumber,fromStation,toStation,boardingDate,departureAt,arrivalAt,seatClass:'SL',passengerCount:1,totalFare:110,availableSeats:10,suggestedAllocation:'CNF'});
 let requests=0;
 await page.route('**/api/**',async route=>{const url=new URL(route.request().url());let body:unknown={content:[],totalPages:0};
  if(url.pathname==='/api/reservations/trains')body=[];
  if(url.pathname==='/api/reservations/alternatives'){requests++;body={options:[{id:'connection',kind:'NEARBY_CONNECTION',legs:[leg('T1','AAN','XXX',date,date+'T16:30:00Z',date+'T18:00:00Z'),leg('T2','XXX','CCN',next,date+'T19:15:00Z',date+'T21:30:00Z')],interchangeStation:'XXX',transferMinutes:75,durationMinutes:300,totalFare:220,allocation:'CNF',originDistanceKm:11.1,destinationDistanceKm:11.1}],notices:[],limited:false,radiusKm:50,minTransferMinutes:60,maxTransferMinutes:360};}
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/journeys?fromStation=AAA&toStation=CCC&dateOfJourney='+date+'&seatClass=SL&passengers=2');
 const panel=page.getByRole('region',{name:'Connecting and nearby journeys'});
 await expect(panel.getByText('1 alternative',{exact:true})).toBeVisible();expect(requests).toBe(1);
 await panel.getByRole('button',{name:'Review itinerary'}).click();await expect(panel.getByRole('button',{name:'Book leg 1'})).toBeDisabled();
 await expect(panel.getByText(/75 minutes to change/)).toBeVisible();
 await panel.getByRole('checkbox',{name:/I have checked/}).check();
 const link=panel.getByRole('link',{name:/Book leg 2/});await expect(link).toHaveAttribute('href',new RegExp('dateOfJourney='+next));await expect(link).toHaveAttribute('href',/toStation=CCN/);await expect(link).toHaveAttribute('href',/journey=/);expect(new URL(await link.getAttribute('href')||'', 'http://localhost').searchParams.has('passengers')).toBeFalsy();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});
