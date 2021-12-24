package ru.barashkov.distributed.lab5;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ActorCache extends AbstractActor {
    private final Map<String, Long> cache = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().
            match(
                MessageSet.class,
                m -> cache.put(
                        m.getUrl(),
                        m.getResponseTime()
                )
            ).
            match(
                MessageGet.class,
                m -> sender().
                        tell(
                                Optionalcache.get(m.getUrl()),
                                self()
                        )
            ).
            build();
    }

}