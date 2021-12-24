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
import akka.japi.Pair;
import akka.japi.function.Function2;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class App {
        private static final String IP = "localhost";
        private static final Integer PORT = 8080;
        private static final Integer PARALLELISM = 10;
        private static final java.time.Duration TIMEOUT = java.time.Duration.ofMillis(3000);

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
                            request -> {
                                Query q = request.getUri().query();
                                String url = q.get("url").get();
                                Integer count = Integer.parseInt(q.get("count").get());
                                return new Pair<>(url, count);
                            }
                    ).
                    mapAsync(
                            PARALLELISM,
                            request -> Patterns.ask(
                                    actorCache,
                                    new MessageGet(request.first()),
                                    TIMEOUT
                            ).thenCompose(
                                    result -> {
                                        if (((Optional<Long>) result).isPresent()) {
                                            return CompletableFuture.completedFuture(
                                                    new Pair<>(
                                                            request.first(),
                                                            ((Optional<Long>) result).get()
                                                    )
                                            );
                                        } else {
                                            Sink<Integer, CompletionStage<Long>> fold = Sink.fold(
                                                    0L,
                                                    (Function2<Long, Integer, Long>) (Long::sum)
                                            );
                                            Sink<Pair<String, Integer>, CompletionStage<Long>> testSink = Flow.
                                                    <Pair<String, Integer>>create().
                                                    mapConcat(
                                                            r -> new ArrayList<>(Collections.nCopies(r.second(), r.first()))).
//                                                            {
//                                                                ArrayList<String> out = new ArrayList<>();
//                                                                for (int i = 0; i < r.second(); i++) {
//                                                                    out.add(r.first());
//                                                                }
//                                                                return out;
//                                                            }
//                                                    ).
                                                    mapAsync(
                                                            request.second(), url -> {
                                                                long begin = System.currentTimeMillis();
                                                                Request request1 = Dsl.get(url).build();
                                                                CompletableFuture<Response> responseCompletableFuture = Dsl.asyncHttpClient().executeRequest(request1)
                                                                asyncHttpClient().prepareGet(url).execute();
                                                                System.out.println((int) (System.currentTimeMillis() - begin));
                                                                return CompletableFuture.completedFuture(
                                                                        (int) (System.currentTimeMillis() - begin)
                                                                );
                                                            }
                                                    ).toMat(fold, Keep.right());
                                            return Source.
                                                    from(Collections.singletonList(request)).
                                                    toMat(testSink, Keep.right()).
                                                    run(materializer).
                                                    thenApply(
                                                            sum -> new Pair<>(request.first(), sum / request.second())
                                                    );
                                        }
                                    }
                            )
                    ).map(
                            result -> {
                                actorCache.tell(
                                        new MessageSet(
                                                result.first(),
                                                result.second()
                                        ),
                                        ActorRef.noSender()
                                );
                                System.out.println("Average:\t" + result.second());
                                return HttpResponse.create().withEntity(
                                            result.first() +
                                            " - " +
                                            result.second().toString()
                                );
                            }
                    );
        }
}
