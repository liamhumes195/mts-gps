package dev.mtsgps.traffic;

import dev.mtsgps.nav.Position;

/** Pure pursuit plus a bounded speed controller; outputs normal MTS control inputs. */
public final class TrafficControl {
    public record Input(double rudder,double throttle,double brake,double desiredSpeed) {}
    public static final class BrakingMemory {
        private double heldRange;
        public double range(double speed) { return Math.max(heldRange,6+Math.max(0,speed)*1.3); }
        public void observe(boolean blocked,double speed) { heldRange=blocked?range(speed):0; }
    }
    public static Input drive(Position car,double headingX,double headingZ,double speed,
                              TrafficPlan.Aim aim,double wheelbase,double cruise,boolean blocked) {
        if(blocked || aim.atEnd() || aim.offRoad()>6 || !car.valid()) return new Input(0,0,1,0);
        double dx=aim.position().x()-car.x(),dz=aim.position().z()-car.z();
        double length=Math.hypot(dx,dz),norm=Math.hypot(headingX,headingZ);
        if(length<.05 || norm<.01) return new Input(0,0,1,0);
        headingX/=norm; headingZ/=norm;
        double lateral=headingZ*dx-headingX*dz;
        double forward=headingX*dx+headingZ*dz;
        double angle=Math.atan2(lateral,forward);
        double steering=Math.atan2(2*wheelbase*lateral,length*length);
        // MTS getSteeringAngle() negates input_rudder. Its ground model has a 1/0.7 yaw gain.
        double rudder=-clamp(.7*steering/Math.toRadians(45),-1,1);
        double desired=cruise/(1+3*Math.abs(angle));
        desired=Math.min(desired,Math.sqrt(Math.max(0,aim.remaining()-1)*1.4));
        if(Math.abs(angle)>Math.toRadians(125)) return new Input(rudder,0,1,0);
        double throttle=clamp((desired-speed)*.32,0,.65);
        double brake=speed>desired+.2?clamp((speed-desired)*.45,0,1):0;
        return new Input(rudder,throttle,brake,desired);
    }
    public static boolean ahead(Position car,double hx,double hz,Position obstacle,double range,double halfWidth) {
        double norm=Math.hypot(hx,hz);
        if(norm<.01 || Math.abs(car.y()-obstacle.y())>2.5) return false;
        hx/=norm; hz/=norm;
        double dx=obstacle.x()-car.x(),dz=obstacle.z()-car.z();
        double forward=dx*hx+dz*hz,lateral=dx*hz-dz*hx;
        return forward>0 && forward<range && Math.abs(lateral)<halfWidth;
    }
    private static double clamp(double value,double min,double max) { return Math.max(min,Math.min(max,value)); }
    private TrafficControl() {}
}
