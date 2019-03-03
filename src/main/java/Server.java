import com.google.gson.Gson;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Starter;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.ext.bridge.BridgeEventType.*;

public class Server extends AbstractVerticle {

    private Logger log = LoggerFactory.getLogger(Server.class);
    private SockJSHandler handler = null;
    private AtomicInteger online = new AtomicInteger(0);

    @Override
    public void start() throws Exception {

        if (!deploy()) {
            log.error("Failed to deploy the server.");
            return;
        }

        handle();
    }

    private void handle() {
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

        //обработка приходящих событий.
        handler.bridge(opts, event -> {
            if (event.type() == PUBLISH)
                publishEvent(event);

            if (event.type() == REGISTER)
                registerEvent(event);

            if (event.type() == SOCKET_CLOSED)
                closeEvent(event);

            //обратите внимание, после обработки события
            // должен вызываться говорящий сам за себя метод.
            event.complete(true);
        });
    }

    private boolean deploy() {
        int hostPort = getFreePort();

        if (hostPort < 0)
            return false;

        Router router = Router.router(vertx);

        //обработчик событий.
        handler = SockJSHandler.create(vertx);

        router.route("/eventbus/*").handler(handler);
        router.route().handler(StaticHandler.create());

        //запуск веб-сервера.
        vertx.createHttpServer().requestHandler(router::accept).listen(hostPort);

        try {
            String addr = InetAddress.getLocalHost().getHostAddress();
            log.info("Access to \"CHAT\" at the following address: \nhttp://" + addr + ":" + hostPort);
        } catch (UnknownHostException e) {
            log.error("Failed to get the local address: [" + e.toString() + "]");
            return false;
        }

        return true;
    }

    private int getFreePort() {
        int hostPort = 8080;

        //если порт задан в качестве аргумента,
        // при запуске приложения.
        if (Starter.PROCESS_ARGS != null
                && Starter.PROCESS_ARGS.size() > 0) {
            try {
                hostPort = Integer.valueOf(Starter.PROCESS_ARGS.get(0));
            } catch (NumberFormatException e) {
                log.warn("Invalid port: [" + Starter.PROCESS_ARGS.get(0) + "]");
            }
        }

        //если некорректно указан порт.
        if (hostPort < 0 || hostPort > 65535)
            hostPort = 8080;

        return getFreePort(hostPort);
    }

    private int getFreePort(int hostPort) {
        try {
            ServerSocket socket = new ServerSocket(hostPort);
            int port = socket.getLocalPort();
            socket.close();

            return port;
        } catch (BindException e) {
            //срабатывает, когда указанный порт уже занят.
            if (hostPort != 0)
                return getFreePort(0);

            log.error("Failed to get the free port: [" + e.toString() + "]");
            return -1;
        } catch (IOException e) {
            log.error("Failed to get the free port: [" + e.toString() + "]");
            return -1;
        }
    }

    private boolean publishEvent(BridgeEvent event) {
        JsonObject rawMessage = event.getRawMessage();

        if (rawMessage != null
                && rawMessage.getString("address").equals("chat.to.server")) {
            String message = rawMessage.getString("body");
            if (!verifyMessage(message))
                return false;

            String host = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            Map<String, Object> publicNotice = createPublicNotice(host, port, message);
            vertx.eventBus().publish("chat.to.client", new Gson().toJson(publicNotice));
            return true;
        } else
            return false;
    }

    private Map<String, Object> createPublicNotice(String host, int port, String message) {
        Date time = Calendar.getInstance().getTime();
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "publish");
        notice.put("time", time.toString());
        notice.put("host", host);
        notice.put("port", port);
        notice.put("message", message);
        return notice;
    }

    //тип события - регистрация обработчика.
    private void registerEvent(BridgeEvent event) {
        if (event.getRawMessage() != null
                && event.getRawMessage().getString("address").equals("chat.to.client"))
            new Thread(() ->
            {
                Map<String, Object> registerNotice = createRegisterNotice();
                vertx.eventBus().publish("chat.to.client", new Gson().toJson(registerNotice));
            }).start();
    }

    //создание уведомления о регистрации пользователя.
    private Map<String, Object> createRegisterNotice() {
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "register");
        notice.put("online", online.incrementAndGet());
        return notice;
    }

    //тип события - закрытие сокета.
    private void closeEvent(BridgeEvent event) {
        new Thread(() ->
        {
            Map<String, Object> closeNotice = createCloseNotice();
            vertx.eventBus().publish("chat.to.client", new Gson().toJson(closeNotice));
        }).start();
    }

    //создание уведомления о выходе пользвателя из чата.
    private Map<String, Object> createCloseNotice() {
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "close");
        notice.put("online", online.decrementAndGet());
        return notice;
    }

    private boolean verifyMessage(String msg) {
        return msg.length() > 0
                && msg.length() <= 140;
    }
}
