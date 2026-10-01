package com.railway.trainservice.service;
import com.railway.trainservice.entity.Station;import com.railway.trainservice.repository.StationRepository;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.transaction.annotation.Transactional;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class CataloguePagingTest {
 @Autowired StationService service;@Autowired StationRepository stations;
 @Test void cataloguePagesSortAndReportTotals(){stations.deleteAll();for(String code:java.util.List.of("TPC","TPA","TPB")){var s=new Station();s.setStationCode(code);s.setStationName("Demo "+code);stations.save(s);}stations.flush();var first=service.page(0,2,"stationCode","asc");assertEquals(3,first.totalElements());assertEquals(2,first.totalPages());assertEquals("TPA",first.content().getFirst().getStationCode());assertEquals("TPC",service.page(1,2,"stationCode","asc").content().getFirst().getStationCode());assertThrows(IllegalArgumentException.class,()->service.page(0,101,"stationCode","asc"));assertThrows(IllegalArgumentException.class,()->service.page(0,20,"arbitraryField","asc"));}
}
