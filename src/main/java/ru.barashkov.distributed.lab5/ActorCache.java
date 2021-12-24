package ru.barashkov.distributed.lab5;

import akka.actor.AbstractActor;

public class ActorCache extends AbstractActor {
    private Map<String, Long> cache = new HashMap<>;

    public Recieve createRecieve() {
        return RecieveBuilder.create().
            match(
                MessageSet.class,
                m -> cache.put(m.getUrl(), m.getResponseTime())
            ).
            match(
                MessageGet.class,
                m -> sender().tell()
            ).
            build();
    }

}