import { Injectable, signal } from '@angular/core';

// Shared signal avoids a dependency cycle between Api and the notification inbox.
@Injectable({providedIn:'root'})
export class NotificationRefresh { version=signal(0); request(){this.version.update(n=>n+1)} }
