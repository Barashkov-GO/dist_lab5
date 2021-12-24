package ru.barashkov.distributed.lab5;


import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import javafx.util.Pair;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class App {
        private static final String IP = "localhost";
        private static final Integer PORT = 8080;
        private static final Integer PARALLELISM = 10;

        public static void main(String[] args) throws IOException {
            System.out.println("start!");
            ActorSystem system = ActorSystem.create("routes");
            ActorRef actorCache = system.actorOf(Props.create(ActorCache.class));
            final Http http = Http.get(system);
            final ActorMaterializer materializer = ActorMaterializer.create(system);
            final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = func(http, system, materializer, actorCache);
            final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                    routeFlow,
                    ConnectHttp.toHost(IP, PORT),
                    materializer
            );
            System.out.println("Server online at http://" + IP + ":" + PORT + "\nPress RETURN to stop...");
            System.in.read();
            binding
                    .thenCompose(ServerBinding::unbind)
                    .thenAccept(unbound -> system.terminate());
        }

        public static Flow<HttpRequest, HttpResponse, NotUsed> func(
                Http http,
                ActorSystem system,
                ActorMaterializer materializer,
                ActorRef actorCache) {
            return Flow.of(HttpRequest.class).
                    map(
                        m -> {
                            Query q = m.getUri().query();
                            String url = String.valueOf(q.get("url"));
                            Integer count = Integer.parseInt(String.valueOf(q.get("count")));
                            return new Pair<String, Integer>(url, count);
                        }
                    ).
                    mapAsync(
                            PARALLELISM,
                            m -> {
                                Patterns.ask(
                                        actorCache,
                                        new MessageGet(m.first),
                                        java
                                )

                            }
                    )

        }

}
