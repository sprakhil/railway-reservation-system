import {test,expect} from '@playwright/test';
test('automatically finds alternatives and links each overnight leg using its actual stations and date',async({page})=>{
 const date=new Date(Date.now()+86400000).toISOString().slice(0,10),next=new Date(Date.now()+2*86400000).toISOString().slice(0,10);
 const leg=(trainNumber:string,fromStation:string,toStation:string,boardingDate:string,departureAt:string,arrivalAt:string)=>({trainNumber,trainName:'Test '+trainNumber,fromStation,toStation,boardingDate,departureAt,arrivalAt,seatClass:'SL',passengerCount:1,totalFare:110,availableSeats:10,suggestedAllocation:'CNF'});
 let requests=0;
 await page.route('**/api/**',async route=>{const url=new URL(route.request().url());let body:unknown={content:[],totalPages:0};
  if(url.pathname==='/api/reservations/trains')body=[];
  if(url.pathname==='/api/reservations/alternatives'){requests++;expect(url.searchParams.get('seatClass')).toBe('ALL');body={options:[{id:'connection',kind:'NEARBY_CONNECTION',legs:[leg('T1','AAN','ST',date,date+'T16:30:00Z',date+'T18:00:00Z'),leg('T2','ST','CCN',next,date+'T19:15:00Z',date+'T21:30:00Z')],interchangeStation:'ST',transferMinutes:75,durationMinutes:300,totalFare:220,allocation:'CNF',originDistanceKm:11.1,destinationDistanceKm:11.1}],stationNames:{AAA:'Mumbai Central',CCC:'Delhi Junction',AAN:'Panvel Junction',CCN:'Hazrat Nizamuddin',ST:'Surat'},notices:[],limited:false,radiusKm:50,minTransferMinutes:60,maxTransferMinutes:360};}
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/journeys?fromStation=AAA&toStation=CCC&dateOfJourney='+date+'&seatClass=SL&passengers=2');
 const panel=page.getByRole('region',{name:'Connecting and nearby journeys'});
 await expect(panel.getByText('1 alternative',{exact:true})).toBeVisible();expect(requests).toBe(1);
 await panel.getByRole('button',{name:'Review itinerary'}).click();await expect(panel.getByRole('button',{name:'Book leg 1'})).toBeDisabled();
 await expect(panel.getByText('75 minutes to change trains at Surat (ST)',{exact:true})).toBeVisible();await expect(panel.getByText(/Arrive at/)).toContainText('Hazrat Nizamuddin (CCN)');
 await panel.getByRole('checkbox',{name:/I have checked/}).check();
 const link=panel.getByRole('link',{name:/Book leg 2/});await expect(link).toHaveAttribute('href',new RegExp('dateOfJourney='+next));await expect(link).toHaveAttribute('href',/toStation=CCN/);await expect(link).toHaveAttribute('href',/journey=/);expect(new URL(await link.getAttribute('href')||'', 'http://localhost').searchParams.has('passengers')).toBeFalsy();
 await page.screenshot({path:test.info().outputPath('nearby-both-ends-desktop.png'),fullPage:true});await page.setViewportSize({width:390,height:844});await page.screenshot({path:test.info().outputPath('nearby-both-ends-mobile.png'),fullPage:true});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
});

test('destination alternatives default to all classes and book the actual New Delhi station',async({page})=>{
 const date=new Date(Date.now()+86400000).toISOString().slice(0,10);const requested:string[]=[];
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url());let body:unknown={content:[],totalPages:0};
  if(url.pathname==='/api/reservations/trains')body=[];
  if(url.pathname==='/api/reservations/alternatives'){
   requested.push(url.searchParams.get('seatClass')||'');
   body={stationNames:{MMCT:'Mumbai Central',DLI:'Delhi Junction',NDLS:'New Delhi'},options:[{
    id:'nearby-destination',kind:'NEARBY_DIRECT',legs:[{trainNumber:'12951',trainName:'Mumbai Rajdhani',fromStation:'MMCT',toStation:'NDLS',boardingDate:date,departureAt:date+'T10:30:00Z',arrivalAt:date+'T23:30:00Z',seatClass:'CLASS_3A',passengerCount:1,totalFare:2200,availableSeats:30,suggestedAllocation:'CNF'}],interchangeStation:null,transferMinutes:0,durationMinutes:780,totalFare:2200,allocation:'CNF',originDistanceKm:0,destinationDistanceKm:2.2}],notices:[],limited:false,radiusKm:50,minTransferMinutes:60,maxTransferMinutes:360};
  }
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/journeys?fromStation=MMCT&toStation=DLI&dateOfJourney='+date);
 const panel=page.getByRole('region',{name:'Connecting and nearby journeys'});
 await expect(panel.getByLabel('Class for alternatives')).toHaveValue('ALL');
 await expect(panel.getByText(/Arrive at/)).toHaveText(/Arrive at New Delhi \(NDLS\), about 2.2 km from Delhi Junction \(DLI\)/);
 expect(requested).toEqual(['ALL']);
 await panel.getByRole('button',{name:'Review itinerary'}).click();await panel.getByRole('checkbox',{name:/I have checked/}).check();
 const href=await panel.getByRole('link',{name:'Book this train'}).getAttribute('href');
 const params=new URL(href!,'http://localhost').searchParams;expect(params.get('toStation')).toBe('NDLS');expect(params.get('seatClass')).toBe('CLASS_3A');expect(params.has('passengers')).toBeFalsy();
 await panel.getByLabel('Class for alternatives').selectOption('CLASS_3A');await panel.getByRole('button',{name:'Refresh alternatives'}).click();
 await expect(panel.getByText('1 alternative',{exact:true})).toBeVisible();expect(requested).toEqual(['ALL','CLASS_3A']);
});
