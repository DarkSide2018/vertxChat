package example2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;

public class FirstVerticle extends AbstractVerticle {

    private static final String ADDRESS = "greeting";

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        super.start(startFuture);

        vertx.eventBus().consumer(ADDRESS, this::hello1);
        vertx.eventBus().consumer(ADDRESS, this::hello2);

        vertx.eventBus().publish(ADDRESS, "hello");
        // Round Robin
        for (int i = 0; i < 5; i++) {
            vertx.eventBus().send(ADDRESS, "hello from send" + i, this::response);
        }
    }

    private void response(AsyncResult<Message<String>> res) {
        if (res.succeeded()) {
            System.out.println("Answer" + res.result().body());
        } else {
            System.out.println("Error" + res.cause());
        }


    }

    private void hello1(Message<String> data) {
        String message = "Handler 1 : " + data.body();
        System.out.println(message);
        data.fail(400,"Error");
    }

    private void hello2(Message<String> data) {
        String message = "Handler 2 : " + data.body();
        System.out.println(message);
        data.reply(message);
    }
}
