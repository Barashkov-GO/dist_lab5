package ru.barashkov.distributed.lab5;


public class App {

        
    public static void main(String[] args) throws IOException {
            System.out.println("start!");
            ActorSystem system = ActorSystem.create("routes");
            final Http http = Http.get(system);
            final ActorMaterializer materializer = ActorMaterializer.create(system);
            final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = <вызов метода которому передаем Http, ActorSystem и ActorMaterializer>;
            final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                    routeFlow,
                    ConnectHttp.toHost("localhost", 8080),
                    materializer
            );
            System.out.println("Server online at http://localhost:8080/\nPress
                    RETURN to stop...");
            System.in.read();
            binding
                    .thenCompose(ServerBinding::unbind)
                    .thenAccept(unbound -> system.terminate());
    }

}
