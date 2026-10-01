package com.railway.notificationservice.service;
import com.railway.notificationservice.repository.NotificationDeliveryRepository; import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import java.time.Instant;
@Component @RequiredArgsConstructor @Slf4j @ConditionalOnProperty(name="notification.jobs.enabled",havingValue="true",matchIfMissing=true)
public class NotificationJobs {
 private final NotificationDeliveryRepository deliveries; private final NotificationInbox inbox;
 @Scheduled(fixedDelayString="${notification.scan-ms:10000}") public void sendDue() {
  for(var d:deliveries.findTop50BySentAtIsNullAndAttemptsLessThanAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(5,Instant.now())) {
   try{inbox.deliver(d.getEventId());}catch(Exception ex){log.warn("Notification attempt deferred for {}",d.getEventId());}
  }
 }
}
