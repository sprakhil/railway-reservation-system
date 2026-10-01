package com.railway.reservationservice.client;
import com.railway.reservationservice.dto.CatalogTrain;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = "train-service")
public interface TrainCatalogClient {
    @GetMapping("/api/trains/{trainNumber}") CatalogTrain getTrain(@PathVariable("trainNumber") String trainNumber);
    @GetMapping("/api/trains") com.railway.reservationservice.dto.PageResult<CatalogTrain> getPage(@RequestParam("page") int page,@RequestParam("size") int size);
    default List<CatalogTrain> getTrains() {
        var result=new java.util.ArrayList<CatalogTrain>();
        for(int page=0;;page++) {
            var batch=getPage(page,100);
            if(batch==null) throw new IllegalStateException("Catalogue returned no page");
            result.addAll(batch.content()); if(page+1>=batch.totalPages()) return result;
        }
    }
}
