export interface AdminUser {id:number;accountUsername:string;username:string;email:string;role:string;status:string;provider:string;verified:boolean}
export interface AdminBooking {pnr:string;username:string;email:string|null;trainNumber:string;fromStation:string;toStation:string;dateOfJourney:string;bookingTime:string;status:string;totalFare:number;paid:boolean;cancellationFee:number;refundAmount:number;refundStatus:string}
export interface AdminBucket {label:string;bookings:number;cancellations:number;paidBookings:number;gross:number;refunded:number;outstandingRefunds:number;net:number}
export interface AdminDashboard {totals:AdminBucket;byStatus:Record<string,number>;popularTrains:AdminBucket[];generatedAt:string}
export interface AdminReport {fromDate:string;toDate:string;groupBy:string;dateBasis:string;totals:AdminBucket;rows:AdminBucket[];generatedAt:string}
export const ADMIN_STATUSES=['PENDING_PAYMENT','CONFIRMED','WAITING','CANCELLED','EXPIRED'];
export function cancellationRate(row:AdminBucket){return row.bookings?100*row.cancellations/row.bookings:0}
