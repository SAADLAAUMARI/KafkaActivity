package com.example.kafka.web;

import com.example.kafka.entities.PageEvent;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController

public class PageEventRestController {
    /*
     un service producer kafka via RestController
     */

    @Autowired
    private StreamBridge streamBridge;
    @Autowired
    private InteractiveQueryService interactiveQueryService;
    // publier une page dans un topic kafka
    @GetMapping("/publish/{topic}/{name}")
    public PageEvent publish(@PathVariable String topic, @PathVariable String name){
        PageEvent pageEvent=new PageEvent();
        pageEvent.setName(name);
        pageEvent.setDate(new Date());
        pageEvent.setDuration(new Random().nextInt(1000));
        pageEvent.setUser(Math.random()>0.5?"U1":"U2");
        streamBridge.send(topic,pageEvent);
        return pageEvent;
    }
    // server send event
    @GetMapping(path="/analytics/{page}" ,produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String,Long>> analytics(@PathVariable String page){
        return Flux.interval(Duration.ofSeconds(1))
                .map(seqence->{
                    Map<String,Long> stringLongMap = new HashMap<>();
                    ReadOnlyWindowStore<String,Long> stats= interactiveQueryService.getQueryableStore("page-count", QueryableStoreTypes.windowStore());
                    Instant now= Instant.now();
                    Instant from= now.minusMillis(5000);
                    // personnaliser la data pour qu'il affiche sauf les donnes du seul page
                    WindowStoreIterator<Long> fetchAll2= stats.fetch(page,from,now);
                    while (fetchAll2.hasNext()){
                        KeyValue<Long,Long> next = fetchAll2.next();
                        stringLongMap.put(page,next.value);
                    }
                    return stringLongMap;
                }).share();
        //share permet que la meme resultat pour tous les utilisateurs connecte
    }
    @GetMapping(path="/analytics" ,produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String,Long>> analytics(){
        return Flux.interval(Duration.ofSeconds(1))
                .map(seqence->{
                    Map<String,Long> stringLongMap = new HashMap<>();
                    ReadOnlyWindowStore<String,Long> stats= interactiveQueryService.getQueryableStore("page-count", QueryableStoreTypes.windowStore());

                    Instant now= Instant.now();
                    Instant from= now.minusMillis(5000);
                    KeyValueIterator<Windowed<String>,Long> fetchAll= stats.fetchAll(from,now);
                    while (fetchAll.hasNext()){
                        KeyValue<Windowed<String>,Long> next = fetchAll.next();
                        stringLongMap.put(next.key.key(),next.value);
                    }
                    return stringLongMap;
                }).share();
    }
}

