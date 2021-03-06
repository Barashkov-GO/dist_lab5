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
import org.asynchttpclient.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class App {
        private static final String IP = "localhost";
        private static final String URL_PARAMETER = "testUrl";
        private static final String COUNT_PARAMETER = "count";

        private static final Integer PORT = 8080;
        private static final Integer PARALLELISM = 1;
        private static final java.time.Duration TIMEOUT = java.time.Duration.ofMillis(3000);

        public static void main(String[] args) throws IOException {
            System.out.println("Starting...");
            ActorSystem system = ActorSystem.create("routes");
            ActorRef actorCache = system.actorOf(Props.create(ActorCache.class));
            final Http http = Http.get(system);
            final ActorMaterializer materializer = ActorMaterializer.create(system);
            final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = getFlow(http, system, materializer, actorCache);
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

        public static Flow<HttpRequest, HttpResponse, NotUsed> getFlow(
                Http http,
                ActorSystem system,
                ActorMaterializer materializer,
                ActorRef actorCache) {
            return Flow.of(HttpRequest.class).
                    map(
                            request -> {
                                Query q = request.getUri().query();
                                String url = q.get(URL_PARAMETER).get();
                                Integer count = Integer.parseInt(q.get(COUNT_PARAMETER).get());
                                System.out.println("Testing : " + url + " " + count);
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
                                                                Request requestTemp = Dsl.get(url).build();
                                                                CompletableFuture<Response> responseCompletableFuture =
                                                                        Dsl.
                                                                        asyncHttpClient().
                                                                        executeRequest(requestTemp).
                                                                        toCompletableFuture();
                                                                return responseCompletableFuture.thenCompose(
                                                                        response -> {
                                                                            int duration = (int) (System.currentTimeMillis() - begin);
                                                                            System.out.println(duration);
                                                                            return CompletableFuture.completedFuture(duration);
                                                                        }
                                                                );
                                                            }
                                                    ).toMat(fold, Keep.right());
                                            return Source.
                                                    from(Collections.singletonList(request)).
                                                    toMat(testSink, Keep.right()).
                                                    run(materializer).
                                                    thenApply(
                                                            sum -> new Pair<>(
                                                                    request.first(),
                                                                    (sum / request.second().longValue())
                                                            )
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
