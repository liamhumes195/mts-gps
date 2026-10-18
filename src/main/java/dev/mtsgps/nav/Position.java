package dev.mtsgps.nav;

public record Position(double x, double y, double z) {
    public boolean valid() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x)<=30_000_000 && Math.abs(z)<=30_000_000 && Math.abs(y)<=4096;
    }
    public double distance(Position p) { return Math.sqrt(square(x-p.x)+square(y-p.y)+square(z-p.z)); }
    public double horizontal(Position p) { return Math.hypot(x-p.x,z-p.z); }
    public Position interpolate(Position p,double t) { return new Position(x+(p.x-x)*t,y+(p.y-y)*t,z+(p.z-z)*t); }
    private static double square(double x) { return x*x; }
}
