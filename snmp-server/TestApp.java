import io.javalin.Javalin;
public class TestApp {
    public static void main(String[] args) {
        Javalin app = Javalin.create();
        app.start(8081);
        System.out.println("Line after app.start");
        System.exit(0);
    }
}
