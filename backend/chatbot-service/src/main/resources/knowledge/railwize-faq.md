# Railwize platform FAQ — policy revision 2026-10-11
Only the application rules below are authoritative. Live quotes, payment state and booking details must come from authenticated backend tools. Configuration changes require updating this resource and restarting chatbot-service to rebuild its in-memory vectors and invalidate caches.

## Ticket types: CNF, WL and RAC
Railwize supports confirmed (CNF) and waitlisted (WL) bookings. RAC is not supported. All passengers in one booking share the allocation status. There is no partial-passenger confirmation, cancellation or refund. A paid WL ticket does not guarantee a seat.

## Waitlist promotion and cutoff
Paid WL bookings are automatically promoted as a whole when enough eligible seats become available. The user does not need to accept promotion. Queue order is preserved. The default WL cutoff is one hour before boarding; unconfirmed WL bookings are cancelled and an eligible full test refund is initiated. The booking screen shows the actual deadline and current status. Email and in-app notifications announce promotion and refund updates.

## Cancellation fees and refund policy
For a paid confirmed booking cancelled before boarding, the default cancellation fee is 20 percent and the eligible refund is the remaining 80 percent. Paid WL cancellation has no cancellation fee and the eligible full fare is refunded. Unpaid cancelled or expired bookings have no refund due. Partial passenger cancellation is not supported. The live cancellation preview is authoritative for a particular booking. Cancellation and refund processing are separate steps; initiated is not the same as processed. All Razorpay payments and refunds are TEST MODE: no real money is charged or returned. Do not promise a real bank credit or a precise completion time.

If the operator cancels a dated train run before its first-station departure, all active CNF, WL and unpaid bookings on that original run are cancelled, including next-day boarding. Captured ticket fares are refunded in full with zero deduction. Late captures are also refunded; previously cancelled tickets keep their existing decision. The reason and pending/processed refund status are on the booking page, with email and in-app updates. Independently booked onward trains are not automatically cancelled or refunded. Remove imported calendar reminders manually. Chat cannot cancel a train or ticket. Help is available at /help.

## Booking holds and failed payments
Payment holds last up to two minutes and can end earlier near departure or the WL cutoff. Complete test checkout before the actual hold deadline. A failed attempt can be retried only while the hold remains active. Closing checkout is not evidence of a successful or failed payment; the backend verifies provider state. Late captured test payments on expired bookings trigger an eligible test refund, not automatic seat confirmation. Never send payment passwords, OTPs, card numbers or CVVs to chat.

## Advance booking and passenger limits
Journeys can be booked from today through two calendar months ahead, subject to the actual boarding time. Past dates, departures that have passed, identical source and destination, reversed routes, and invalid stops are rejected. Each booking has one train and one class with 1 to 6 passengers, within the train class capacity. Connected multi-train journeys need separate bookings. Availability is date, route, class and passenger-count specific. Search results show operating days and current fares; a fare is rechecked before booking.

## Registration, login and account security
Register and verify the email using its five-minute, single-use OTP, then sign in. Google sign-in is also supported. A verified account can change its display/login username once. Administrator access is granted through an authorized invitation, not a registration role selector. Use the normal account screens for login, reset, logout and invitations. Chat cannot grant roles, change account details, book, charge, cancel or issue refunds. Only the signed-in user's own bookings may be retrieved in chat, including for admin accounts.

## Saved passengers, tickets and calendar
Saved passengers can be selected during booking to avoid re-entering details. Paid bookings provide a downloadable PDF ticket; a WL ticket shows its waitlisted status. Confirmation and promotion emails include the updated PDF. Future confirmed bookings can export a calendar reminder; calendar events do not automatically update after cancellation. Booking history provides status/date filtering and pagination.

## Notifications and booking updates
The in-app bell shows unread notifications for new bookings, payments, WL promotion, cancellation and test refunds. It refreshes every five seconds in a visible signed-in tab and after successful booking actions. Read state survives logout. Chat is optional and does not replace the live booking screen. Do not assume availability, confirmation or refund completion from a previous conversation.

## Train discovery and passenger selection
On the website, search using departure station, destination and boarding date. Results list every configured class for each matching train, with a per-passenger fare and current availability. Select a class, then explicitly choose 1–6 passengers on the booking page. The backend requotes the total and whole-group CNF/WL availability before checkout. A chatbot train card may quote a particular class/count, but its Book button opens the same booking page without assuming your passenger count. Connecting/nearby alternatives have a separate class control and show fares per passenger.
