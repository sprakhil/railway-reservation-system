export interface Page<T> {content:T[]; page:number; size:number; totalElements:number; totalPages:number}
export interface Session {accessToken:string; refreshToken:string; username:string; role:string; displayUsername?:string; usernameChangeAllowed?:boolean; usernameSetupSuggested?:boolean}
export interface Station {stationId:number; stationCode:string; stationName:string; city?:string; state?:string; zone?:string; latitude?:number|null; longitude?:number|null}
export interface ClassConfig {seatClass:string; coachCount:number; seatsPerCoach:number; baseFare:number; ratePerKm:number; minimumFare:number}
export interface Train {trainId?:number; trainNumber:string; trainName:string; category:string; sourceStationId:number; destinationStationId:number; seatClasses:string[]; operatingDays:string[]; classConfigurations:ClassConfig[]}
export interface Stop {id?:number; trainNumber?:string; stationCode:string; stopSequence:number; arrivalTime:string|null; departureTime:string|null; dayNumber:number; departureDayNumber:number; distanceKm:number}
export interface RouteSearch {fromStation:string; toStation:string; dateOfJourney:string}
export interface Search extends RouteSearch {seatClass:string; passengers:number}
export interface TrainSearchResult {trainNumber:string;trainName:string;classes:Quote[]}
export interface Quote {trainNumber:string; trainName:string; fromStation:string; toStation:string; seatClass:string; boardingDate:string; runDate:string; departureAt:string; arrivalAt:string; distanceKm:number; passengerCount:number; farePerPassenger:number; totalFare:number; baseFare:number; ratePerKm:number; minimumFare:number; availableSeats:number; suggestedAllocation:string; waitlistDeadline:string; waitlistOpen:boolean}
export interface SeatDetails {coach:string|null; position:number|null; berthType:string|null; berthLabel:string|null; description:string; layout:string|null; bay:number|null}
export interface Passenger {name:string; age:number|null; gender:string; birthPreference:string; seatNumber?:string; seatDetails?:SeatDetails; bookingStatus?:string}
export interface SavedPassenger {id:number; name:string; age:number; gender:string; berthPreference:string|null}
export interface Booking {operatorCancellationReason?:string|null;operatorCancelledAt?:string|null;pnr:string; trainNumber:string; fromStation:string; toStation:string; seatClass:string; dateOfJourney:string; departureAt:string; holdExpiresAt:string; waitlistDeadline:string; allocation:string; waitlistPosition:number|null; totalFare:number; farePerPassenger:number; paymentStatus:string; refundStatus:string; refundAmount:number; status:string; passengers:Passenger[]; legacyBooking:boolean; bookingTime:string}
export interface Cancellation {pnr:string; message:string; originalFare:number; cancellationFee:number; refundAmount:number; refundStatus:string; currentStatus:string}
export interface Order {pnr:string; keyId:string; razorpayOrderId:string; amountInPaise:number; currency:string; holdExpiresAt:string; mode:string}
export interface Payment {pnr:string; paymentStatus:string; bookingStatus:string; mode:string; payments:{razorpayPaymentId:string; bookingConfirmed:boolean; refundAmount:number; refundStatus:string; razorpayRefundId:string}[]}
export interface Profile {username?:string; email?:string; firstName:string; lastName:string; phoneNumber:string; gender:string; dateOfBirth:string; address:string}
export interface Invitation {id:number; email:string; invitedBy:string; acceptedBy:string|null; expiresAt:string; acceptedAt:string|null; revokedAt:string|null}
export const CLASSES = [{value:'SL',label:'Sleeper (SL)'},{value:'CLASS_3A',label:'AC 3 Tier (3A)'},{value:'CLASS_2A',label:'AC 2 Tier (2A)'},{value:'CLASS_1A',label:'AC First Class (1A)'},{value:'CLASS_3E',label:'AC 3 Economy (3E)'},{value:'EC',label:'Executive Chair (EC)'},{value:'CLASS_2S',label:'Second Sitting (2S)'},{value:'UR',label:'Unreserved (UR)'}];
export const PREFERENCES = ['NONE','LOWER','MIDDLE','UPPER','SIDE_LOWER','SIDE_UPPER'];
export const DAYS = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];


export interface JourneyPlan {id:string;kind:string;legs:Quote[];interchangeStation:string|null;transferMinutes:number;durationMinutes:number;totalFare:number;allocation:string;originDistanceKm:number;destinationDistanceKm:number}
export interface JourneyAlternatives {options:JourneyPlan[];notices:string[];limited:boolean;radiusKm:number;minTransferMinutes:number;maxTransferMinutes:number}
