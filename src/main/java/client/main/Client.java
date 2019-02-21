package client.main;

import client.handlers.connectionHandler.ConnectionHandler;
import client.handlers.inputHandler.KeyboardInput;
import client.handlers.inputHandler.MouseInput;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shared.gameObjects.players.Player;
import shared.handlers.levelHandler.GameState;
import shared.handlers.levelHandler.LevelHandler;
import shared.handlers.levelHandler.Map;
import shared.packets.PacketInput;
import shared.physics.Physics;
import shared.util.Path;

public class Client extends Application {

  private static final Logger LOGGER = LogManager.getLogger(Client.class.getName());
  public static LevelHandler levelHandler;
  public static Settings settings;
  public static boolean multiplayer;
  public static boolean singleplayerGame;
  public static ConnectionHandler connectionHandler;
  public static boolean sendUpdate;

  private final float timeStep = 0.0166f;
  private final String gameTitle = "Alone in the Dark";
  public static Timer timer = new Timer("Timer", true);

  public static int inputSequenceNumber;
  public static ArrayList<PacketInput> pendingInputs;
  public static TimerTask task;
  private LinkedList<Map> playlist;

  private KeyboardInput keyInput;
  private MouseInput mouseInput;
  private Group root;
  private Group backgroundRoot;
  public static Group gameRoot;
  private Scene scene;
  private float maximumStep;
  private long previousTime;
  private float accumulatedTime;
  private float elapsedSinceFPS = 0f;
  private int framesElapsedSinceFPS = 0;
  private AtomicBoolean found = new AtomicBoolean(false);

  public static void main(String args[]) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    playlist = new LinkedList<>();
    //Testing code
    for (int i = 0; i < 10; i++) {
      playlist
          .add(new Map("Map" + i, Path.convert("src/main/resources/maps/map" + i + ".map"),
              GameState.IN_GAME));
    }

    /** Setup Game timer */
    task = new TimerTask() {
      @Override
      public void run() {
        singleplayerGame = false;
        levelHandler.getPlayers().removeAll(levelHandler.getBotPlayerList());
        levelHandler.getBotPlayerList().forEach(gameObject -> gameObject.removeRender());
        levelHandler.getBotPlayerList().forEach(gameObject -> gameObject = null);
        levelHandler.getBotPlayerList().clear();
        levelHandler.changeMap(
            new Map("Main Menu", Path.convert("src/main/resources/menus/main_menu.map"),
                GameState.MAIN_MENU), false);
      }
    };

    setupRender(primaryStage);
    inputSequenceNumber = 0;
    pendingInputs = new ArrayList<>();
    singleplayerGame = false;
    sendUpdate = false;
    levelHandler = new LevelHandler(settings, root, backgroundRoot, gameRoot);
    keyInput = new KeyboardInput();
    mouseInput = new MouseInput();
    // Setup Input
    scene.setOnKeyPressed(keyInput);
    scene.setOnKeyReleased(keyInput);
    scene.setOnMousePressed(mouseInput);
    scene.setOnMouseMoved(mouseInput);
    scene.setOnMouseReleased(mouseInput);
    scene.setOnMouseDragged(mouseInput);

    // Main Game Loop
    new AnimationTimer() {
      @Override
      public void handle(long now) {

        if (multiplayer) {
          processServerPackets();
        }

        if (previousTime == 0) {
          previousTime = now;
          return;
        }

        float secondElapsed = (now - previousTime) / 1e9f; // time elapsed in seconds
        float secondsElapsedCapped = Math.min(secondElapsed, maximumStep);
        accumulatedTime += secondsElapsedCapped;
        previousTime = now;

        if (accumulatedTime < timeStep) {
          float timeSinceInterpolation = timeStep - (accumulatedTime - secondElapsed);
          float alphaRemaining = secondElapsed / timeSinceInterpolation;
          levelHandler
              .getGameObjects()
              .forEach(gameObject -> gameObject.interpolatePosition(alphaRemaining));
          return;
        }

        while (accumulatedTime >= 2 * timeStep) {
          levelHandler.getGameObjects().forEach(gameObject -> gameObject.update());
          accumulatedTime -= timeStep;
        }

        /** Apply Input */
        levelHandler.getClientPlayer().applyInput();

        if (multiplayer && sendUpdate) {
          sendInput();
          sendUpdate = false;
        }

        if (!multiplayer && singleplayerGame && levelHandler.getPlayers().size() > 1) {
          /**Calculate Score*/
            ArrayList<Player> alive = new ArrayList<>();
          for (Player p : levelHandler.getPlayers()) {
            if (p.isActive()) {
              alive.add(p);
            }
            if (alive.size() > 1) {
              break;
            }
          }
            if (alive.size() == 1) {
              alive.forEach(player -> player.increaseScore());
              levelHandler.getPlayers().forEach(player -> player.reset());
              Map nextMap = playlist.poll();
              levelHandler.changeMap(nextMap, true);
            }
          /** Move bots */
          levelHandler.getBotPlayerList()
              .forEach(bot -> bot.applyInput());

        }

        /** Render Game Objects */
        levelHandler.getGameObjects().forEach(gameObject -> gameObject.render());
        if (levelHandler.getBackground() != null) {
          levelHandler.getBackground().render();
        }
        /** Check Collisions */
        Physics.gameObjects = levelHandler.getGameObjects();
        levelHandler
            .getGameObjects()
            .forEach(gameObject -> gameObject.updateCollision(levelHandler.getGameObjects()));
        Physics.processCollisions();

        /** Update Game Objects */
        levelHandler.getGameObjects().forEach(gameObject -> gameObject.update());
        accumulatedTime -= timeStep;
        float alpha = accumulatedTime / timeStep;
        levelHandler.getGameObjects().forEach(gameObject -> gameObject.interpolatePosition(alpha));

        calculateFPS(secondElapsed, primaryStage);
      }
    }.start();
  }

  public void calculateFPS(float secondElapsed, Stage primaryStage) {
    elapsedSinceFPS += secondElapsed;
    framesElapsedSinceFPS++;
    if (elapsedSinceFPS >= 0.5f) {
      int fps = Math.round(framesElapsedSinceFPS / elapsedSinceFPS);
      primaryStage.setTitle(
          gameTitle + "   --    FPS: " + fps + "    Score: " + Client.levelHandler.getClientPlayer()
              .getScore());
      elapsedSinceFPS = 0;
      framesElapsedSinceFPS = 0;
    }
  }

  public void init() {
    maximumStep = 0.0166f;
    previousTime = 0;
    accumulatedTime = 0;
    settings = new Settings();
    multiplayer = false;
    // Start off screen
  }

  private void setupRender(Stage primaryStage) {
    root = new Group();
    backgroundRoot = new Group();
    gameRoot = new Group();

    root.getChildren().add(backgroundRoot);
    root.getChildren().add(gameRoot);

    primaryStage.setTitle(gameTitle);
    primaryStage.getIcons().add(new Image(Path.convert("images/logo.png")));

    scene = new Scene(root, 1920, 1080);

    primaryStage.setScene(scene);
    primaryStage.setFullScreen(false);
    primaryStage.show();
  }

  //TEMP
  public void sendInput() {
    connectionHandler.send(levelHandler.getClientPlayer().getState());
    inputSequenceNumber++;
  }

  //TEMP
  private void processServerPackets() {
    if (connectionHandler.received.size() != 0) {
      try {
        String message = (String) connectionHandler.received.take();
        String[] unpackedData = message.split(";");
        System.out.println(message);
        found.set(false);
        levelHandler.getPlayers().forEach(player -> {
          if (player.getUUID() == UUID.fromString(unpackedData[0])) {
            player.setState(message);
            found.set(true);
          }
        });
        if (found.get() == false) {
          levelHandler.addPlayer(
              new Player(Double.parseDouble(unpackedData[2]), Double.parseDouble(unpackedData[3]),
                  UUID.fromString(unpackedData[0])), gameRoot);
          System.out.println("PLAYER CREATED");
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

}
