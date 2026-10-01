import { Routes } from '@angular/router';
import { adminGuard, signedInGuard } from './core/api';
import { guestOnlyGuard } from './core/api';

export const routes:Routes=[
 {path:'assistant',title:'Your travel companion · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/chat').then(m=>m.ChatPage)},
 {path:'notifications',title:'Your updates · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/notifications').then(m=>m.NotificationHistory)},
 {path:'',title:'Railwize — A better way to get there',loadComponent:()=>import('./pages/home').then(m=>m.Home)},
 {path:'journeys',title:'Find a train · Railwize',loadComponent:()=>import('./pages/search').then(m=>m.Journeys)},
 {path:'book/:trainNumber',title:'Passenger details · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/book').then(m=>m.Book)},
 {path:'bookings',title:'My journeys · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/bookings').then(m=>m.Bookings)},
 {path:'bookings/:pnr',title:'Your booking · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/ticket').then(m=>m.Ticket)},
 {path:'passengers',title:'Saved passengers · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/passengers').then(m=>m.Passengers)},
 {path:'account',title:'Your account · Railwize',canActivate:[signedInGuard],loadComponent:()=>import('./pages/account').then(m=>m.Account)},
 {path:'auth/callback',title:'Google sign-in · Railwize',loadComponent:()=>import('./pages/auth').then(m=>m.AuthPage)},
 {path:'auth/:mode',title:'Your account · Railwize',canActivate: [guestOnlyGuard],loadComponent:()=>import('./pages/auth').then(m=>m.AuthPage)},
 {path:'admin/accept-invitation',title:'Accept invitation · Railwize',loadComponent:()=>import('./pages/admin').then(m=>m.AcceptInvitation)},
 {path:'admin',canActivate:[adminGuard],loadComponent:()=>import('./pages/admin').then(m=>m.AdminShell),children:[
  {path:'',title:'Admin workspace · Railwize',loadComponent:()=>import('./pages/admin-insights').then(m=>m.AdminDashboardPage)},
  {path:'users',title:'User management · Railwize',loadComponent:()=>import('./pages/admin-management').then(m=>m.AdminUsersPage)},
  {path:'bookings',title:'Booking management · Railwize',loadComponent:()=>import('./pages/admin-management').then(m=>m.AdminBookingsPage)},
  {path:'reports',title:'Revenue & reports · Railwize',loadComponent:()=>import('./pages/admin-insights').then(m=>m.AdminReportsPage)},
  {path:'stations',title:'Manage stations · Railwize',data:{kind:'stations'},loadComponent:()=>import('./pages/admin').then(m=>m.AdminCatalogue)},
  {path:'trains',title:'Manage trains · Railwize',data:{kind:'trains'},loadComponent:()=>import('./pages/admin').then(m=>m.AdminCatalogue)},
  {path:'schedules',title:'Manage timetables · Railwize',loadComponent:()=>import('./pages/admin').then(m=>m.AdminSchedules)},
  {path:'invitations',title:'Admin invitations · Railwize',loadComponent:()=>import('./pages/admin').then(m=>m.AdminInvitations)}
 ]},
 {path:'help',title:'Booking guide · Railwize',loadComponent:()=>import('./pages/help').then(m=>m.Help)},
 {path:'forbidden',title:'Access restricted · Railwize',data:{forbidden:true},loadComponent:()=>import('./pages/help').then(m=>m.NotFound)},
 {path:'**',title:'Page not found · Railwize',loadComponent:()=>import('./pages/help').then(m=>m.NotFound)}
];
