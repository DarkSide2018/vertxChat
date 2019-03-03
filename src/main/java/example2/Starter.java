package example2;

import io.vertx.core.Vertx;

public class Starter {
    public static void main(String[] args) {
        System.out.println("Start Vertix chat");
        Vertx.vertx().deployVerticle(new FirstVerticle());

    }
}
