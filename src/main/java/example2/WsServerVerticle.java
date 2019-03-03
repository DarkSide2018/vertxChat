package example2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;

public class WsServerVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.createHttpServer().listen(8080)
                .websocketHandler(this::createWebSocketServer);


    }

    private void createWebSocketServer(ServerWebSocket wsServer) {
        vertx.eventBus().<String>consumer(wsServer.path(),
                data -> {
                    wsServer.writeFinalTextFrame(data.body());
                    data.reply("ok");
                });
    }
}
