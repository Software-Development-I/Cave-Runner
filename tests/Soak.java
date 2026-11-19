import com.badlogic.Terraria.*;
public class Soak { public static void main(String[] a){
  java.util.Random r=new java.util.Random(11); int problems=0; long t0=System.nanoTime(); long frames=0;
  for(int run=0;run<4;run++){
    GameWorld g=new GameWorld(null,100+run,1+run%3); Player.Controls c=new Player.Controls(); float maxAbs=0; int deaths=0, prevLives=g.player.lives;
    for(int f=0;f<60*300;f++){
      c.reset(); c.moveX = (f/90%2==0)?1:-1; c.jumpPressed = r.nextInt(50)==0; c.attackPressed = r.nextInt(25)==0;
      g.update(1/60f,c); frames++;
      Player p=g.player;
      // bot also mines a random nearby tile and places blocks
      int tx=(int)(p.centerX()/16)+r.nextInt(5)-2, ty=(int)(p.centerY()/16)+r.nextInt(5)-2; g.mineTile(tx,ty,1/60f);
      if(f%40==0) g.placeTile(tx,ty);
      if(g.levelComplete){ g.nextLevel(f); }
      if(g.gameOver){ g=new GameWorld(null,200+run,1); }
      // invariants
      if(Float.isNaN(p.position.x)||Float.isNaN(p.position.y)||Float.isInfinite(p.position.y)){problems++; System.out.println("NaN position"); break;}
      if(p.position.y< -64||p.position.x<-1||p.position.x>World.WIDTH*16+1){problems++; System.out.println("player escaped world "+p.position); break;}
      if(p.health<0||p.health>Config.MAX_HEALTH){problems++; System.out.println("hp out of range "+p.health); break;}
      if(g.enemies.size()>60){problems++; System.out.println("enemy leak "+g.enemies.size()); break;}
      if(g.drops.size()>400){problems++; System.out.println("drop leak "+g.drops.size()); break;}
      if(g.projectiles.size()>200){problems++; System.out.println("projectile leak"); break;}
      for(int i=0;i<Inventory.HOTBAR;i++) if(p.inventory.counts[i]<0||p.inventory.counts[i]>99){problems++; System.out.println("bad stack"); break;}
    }
  }
  double sec=(System.nanoTime()-t0)/1e9;
  System.out.printf("simulated %d frames (%.0f game-minutes) in %.1fs real time = %.0fx realtime, problems=%d%n",frames,frames/3600.0,sec,frames/60.0/sec,problems);
  System.out.println(problems==0?"SOAK PASSED":"SOAK FAILED");
}}
