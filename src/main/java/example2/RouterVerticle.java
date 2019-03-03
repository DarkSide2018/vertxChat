package example2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;



public class RouterVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.eventBus().consumer("router",this::router);
    }
    void router(Message<String> message){
        ExampleData exampleData = Json.decodeValue(message.body(), ExampleData.class);
        vertx.eventBus().publish("/token/" + exampleData.getAddress(), message.body());

    }
}
