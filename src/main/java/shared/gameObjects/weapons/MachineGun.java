package shared.gameObjects.weapons;

import client.handlers.audioHandler.AudioHandler;
import java.util.UUID;
import javafx.scene.transform.Rotate;
import shared.gameObjects.Utils.ObjectID;
import shared.gameObjects.players.Player;
import shared.util.Path;
import shared.util.maths.Vector2;

public class MachineGun extends Gun {

  private static String imagePath = "images/weapons/guns/assult-rifle/AK47.png"; // path to Machine Gun image
  private static String audioPath = "audio/sound-effects/laser_gun.wav"; // path to Machine Gun sfx
  private static float PI = 3.141592654f;
  private static double sizeX = 80, sizeY = 20;

  private double[] holderHandPos;
  private double angleGun; // angle of gun (hand and mouse vs x-axis) (radian)
  private Rotate rotate; // rotate property of gun wrt grip

  public MachineGun(double x, double y, String name, Player holder, UUID uuid) {

    super(
        x,
        y,
        sizeX, // sizeX
        sizeY, // sizeY
        ObjectID.Weapon, // ObjectID
        5, // damage
        10, // weight
        name,
        50, // ammo
        50, // bulletSpeed
        70, // fireRate
        12, // bulletWidth
        holder,
        true, // fullAutoFire
        false, // singleHanded
        uuid);
    holderHandPos = holder.getHandPos();

    rotate = new Rotate();
    // pivot = position of the grip
    // If changing the value of this, change the value in all getGrip() methods
    rotate.setPivotX(20);
    rotate.setPivotY(10);
  }

  @Override
  public void fire(double mouseX, double mouseY) {
    if (canFire()) {
      UUID uuid = UUID.randomUUID();
      // double bulletX     = getGripX() + 68 * Math.cos(angleGun) - 4 * Math.sin(angleGun);
      // double bulletY     = getGripY() + 68 * Math.sin(angleGun) - 4 * Math.cos(angleGun);
      double bulletX = getMuzzleX() - 68 + 68 * Math.cos(-angleGun);
      double bulletY = getMuzzleY() - 68 * Math.sin(-angleGun);
      double bulletFlipX = getMuzzleFlipX() + 68 - 68 * Math.cos(angleGun);
      double bulletFlipY = getMuzzleFlipY() - 68 * Math.sin(angleGun);
      Bullet bullet =
          new MachineGunBullet(
              (holder.getFacingRight() ? bulletX : bulletFlipX),
              (holder.getFacingRight() ? bulletY : bulletFlipY),
              mouseX,
              mouseY,
              this.bulletWidth,
              this.bulletSpeed,
              this.damage,
              this.holder,
              uuid);
      this.currentCooldown = getDefaultCoolDown();
      // new AudioHandler(super.getSettings()).playSFX("CHOOSE_YOUR_CHARACTER");
      new AudioHandler(settings).playSFX("MACHINEGUN");
      deductAmmo();
    }
  }

  @Override
  public void update() {
    super.update();
    holderHandPos = holder.getHandPos();
  }

  @Override
  public void render() {
    super.render();

    imageView.getTransforms().remove(rotate);

    double mouseX = holder.mouseX;
    double mouseY = holder.mouseY;
    Vector2 mouseV = new Vector2((float) mouseX, (float) mouseY);
    Vector2 gripV = new Vector2((float) holder.getX(), (float) holder.getY());
    angleGun = mouseV.sub(gripV).angle(); // radian
    double angle = angleGun * 180 / PI; // degree

    rotate.setAngle(angle);
    imageView.getTransforms().add(rotate);
    imageView.setTranslateX(this.getGripX());
    imageView.setTranslateY(this.getGripY());
  }

  @Override
  public void initialiseAnimation() {
    this.animation.supplyAnimationWithSize("default", 40, 40, true, Path.convert(this.imagePath));
  }

  // =============================
  // Get Grip and Muzzle positions
  // =============================
  public double getGripX() {
    return holderHandPos[0] - 20;
  }

  public double getGripY() {
    return holderHandPos[1] - 10;
  }

  public double getGripFlipX() {
    return holderHandPos[0] - 55;
  }

  public double getGripFlipY() {
    return holderHandPos[1] - 10;
  }

  public double getMuzzleX() {
    return getGripX() + 68;
  }

  public double getMuzzleY() {
    return getGripY() - 4;
  }

  public double getMuzzleFlipX() {
    return getGripFlipX() - 12;
  }

  public double getMuzzleFlipY() {
    return getGripFlipY() - 8;
  }
}
