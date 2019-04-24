package com.team3;

import com.team3.grpc.AppResponse;
import com.team3.grpc.AppServiceGrpc;
import com.team3.grpc.ClientRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class AppServer{
    private static final Logger logger = LogManager.getLogger("AppServer");
    private io.grpc.Server grpcServer;
    private int port;
    private ServerConfig serverConfig;
    private final int CAPACITY = 10000;
    private ManagedChannel channel;
    private AppServiceGrpc.AppServiceBlockingStub stub;
    private Map<String, String> userLogMap = new LinkedHashMap<String, String>(CAPACITY, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > CAPACITY;
        }
    };

    AppServer(int port, ServerConfig serverConfig) {
        this.port = port;
        this.serverConfig = serverConfig;
    }

    class AppServiceImpl extends AppServiceGrpc.AppServiceImplBase {
        @Override
        public void clientRequestHandling(ClientRequest request, StreamObserver<AppResponse> responseObserver) {
            String action = request.getAction();
            AppResponse.Builder response = AppResponse.newBuilder();
            if (action.equals("login")) {
                String userName = request.getLogMsg().getUserName();
                String password = request.getLogMsg().getPassword();
                String cryptPassword = Utility.getSHA(password);
                if (userLogMap.containsKey(userName)) {
                    if (cryptPassword.equals(userLogMap.get(userName))) {
                        response.setSucceed(true).setMsg("Successfully logged in");
                        /* change client status, username of client is known now */
                    } else {
                        response.setSucceed(false).setMsg("Incorrect password");
                    }
                } else {
                    /* find in database */
                }

            } else if (action.equals("logout")) {


            } else if (action.equals("register")) {
                String userName = request.getLogMsg().getUserName();
                String password = request.getLogMsg().getPassword();
                String cryptPassword = Utility.getSHA(password);
                userLogMap.put(userName, cryptPassword);
                /* if username exist, fail to register */
            } else if (action.equals("tweet")) {

            } else if (action.equals("read")) {

            } else if (action.equals("follow")) {

            } else if (action.equals("unfollow")) {

            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }
    }

    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                AppServer.this.stop();
                logger.info("Keyboard Interrupt, Shutdown...");
            }
        });

        /* connect to db server leader
         * if connection failed at some point,
         * read the config again to find the leader
         * build a new channel,
         * db server need to write the leader to config after leader election
         */
        initializeChannel();

        try {
            this.grpcServer = ServerBuilder.forPort(port)
                    .addService(new AppServiceImpl())
                    .build()
                    .start();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void initializeChannel() {
        buildChannel(ManagedChannelBuilder
                        .forAddress(serverConfig.defaultLeader.getKey(),
                                serverConfig.defaultLeader.getValue())
                        .usePlaintext()
                        .build());
    }

    private void buildChannel(ManagedChannel channel) {
        this.channel = channel;
        this.stub = AppServiceGrpc.newBlockingStub(channel);
    }

    private void stop() {
        if (this.grpcServer != null) {
            this.grpcServer.shutdown();
        }
    }

    private void blockUntilShutdown() {
        try {
            if (grpcServer != null) {
                grpcServer.awaitTermination();
            }
        } catch (InterruptedException e ) {
            logger.error("Interrupted: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        /* set log configuration file location */
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        String propLocation = "src/main/resources/log4j2.xml";
        File file = new File(propLocation);

        context.setConfigLocation(file.toURI());

        logger.info("Log properties file location: " + propLocation);

        /* parse server config file */
        String configPath = "etc/server_config.json";
        ServerConfig serverConfig = Utility.readConfig(configPath);


        int port;
        Pair<Boolean, Integer> portPair;

        Scanner scanner = new Scanner(System.in);

        if (args.length == 1 && (portPair = Utility.isPortValid(args[0])).getKey()) {
            port = portPair.getValue();
        } else {
            while (true) {
                logger.info("Please give one valid port number");
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] line_arg = line.trim().split("\\s+");
                    if (line_arg.length >= 1 && (portPair = Utility.isPortValid(line_arg[0])).getKey()) {
                        port = portPair.getValue();
                        break;
                    }
                }
            }
        }

        AppServer appServer = new AppServer(port, serverConfig);
        appServer.run();
        appServer.blockUntilShutdown();
    }

}