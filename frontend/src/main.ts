import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling, withComponentInputBinding } from '@angular/router';
import { App } from './app/app';
import { routes } from './app/routes';
import { authInterceptor } from './app/core/api';
bootstrapApplication(App,{providers:[provideHttpClient(withFetch(),withInterceptors([authInterceptor])),provideRouter(routes,withComponentInputBinding(),withInMemoryScrolling({scrollPositionRestoration:'enabled',anchorScrolling:'enabled'}))]}).catch(error=>console.error('Application could not start',error));
