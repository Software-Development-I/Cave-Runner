import com.badlogic.Terraria.*;
public class Soak2 { public static void main(String[] a){
  java.util.Random r=new java.util.Random(5); int problems=0; long frames=0; int bosses=0, minionPeak=0, slams=0;
  for(int run=0;run<6;run++){
    GameWorld g=new GameWorld(null,300+run,2+run%4); Player.Controls c=new Player.Controls();
    for(int f=0;f<60*400;f++){
      c.reset(); c.moveX=(f/70%2==0)?1:-1; c.jumpPressed=r.nextInt(45)==0; c.attackPressed=r.nextInt(12)==0;
      // teleport the bot to the deep boss zone now and then so the boss fight really runs
      if(f%2000==0){ g.player.position.set(150*16, 12*16); g.player.velocity.setZero(); }
      g.update(1/60f,c); frames++;
      Player p=g.player; g.mineTile((int)(p.centerX()/16)+r.nextInt(5)-2,(int)(p.centerY()/16)+r.nextInt(5)-2,1/60f);
      if(g.evSlam) slams++; g.clearEvents();
      for(Enemy e:g.enemies) if(e.type==Enemy.BOSS&&!e.dead) bosses++;
      int sl=0; for(Enemy e:g.enemies) if(!e.dead&&e.type==Enemy.SLIME) sl++; minionPeak=Math.max(minionPeak,sl);
      if(g.levelComplete) g.nextLevel(f); if(g.gameOver) g=new GameWorld(null,900+run,2);
      if(Float.isNaN(p.position.x)||Float.isNaN(p.position.y)){problems++;System.out.println("NaN");break;}
      if(p.health<0||p.health>Config.MAX_HEALTH){problems++;System.out.println("hp "+p.health);break;}
      if(g.enemies.size()>80){problems++;System.out.println("enemy leak "+g.enemies.size());break;}
      for(Enemy e:g.enemies){ if(Float.isNaN(e.x)||Float.isNaN(e.y)){problems++;System.out.println("enemy NaN type "+e.type);break;} }
    }
  }
  System.out.printf("%d frames (%.0f game-min), boss-frames=%d slams=%d peakSlimes=%d problems=%d%n",frames,frames/3600.0,bosses,slams,minionPeak,problems);
  System.out.println(problems==0?"SOAK2 PASSED":"SOAK2 FAILED");
}}
