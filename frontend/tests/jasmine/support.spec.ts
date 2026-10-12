import {SupportSubmission} from '../../src/app/core/support';
import {notificationRoute,NotificationItem} from '../../src/app/core/notifications';
describe('Support retries and notification destinations',()=>{
 it('reuses a UUID for identical retries but changes it for a different submission',()=>{
  const refs=new SupportSubmission(),body={subject:'Payment question',message:'Please check'};
  const first=refs.reference(body);expect(refs.reference({...body})).toBe(first);
  expect(refs.reference({...body,message:'A different request'})).not.toBe(first);
  refs.clear();expect(refs.reference(body)).not.toBe(first);
 });
 it('opens support updates without a PNR and preserves existing booking links',()=>{
  const item={supportTicketId:'SUP-12345678-1234-1234-1234-123456789012',pnr:null} as NotificationItem;
  expect(notificationRoute(item)).toEqual(['/support',item.supportTicketId!]);
  expect(notificationRoute({...item,supportTicketId:null,pnr:'1234567890'})).toEqual(['/bookings','1234567890']);
  expect(notificationRoute({...item,supportTicketId:'https://evil.example'})).toEqual(['/notifications']);
 });
});
