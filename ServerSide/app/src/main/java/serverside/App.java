/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package serverside;

import serverside.logic.PropertiesLoader;
import serverside.logic.Server;

import java.io.IOException;

public class App {

    public static void main(String[] args) {
        try {
            Server server = new Server(PropertiesLoader.propertiesLoader("C:\\Users\\Ineed\\Desktop\\ITHillelGit\\ItHillelGit\\ithillelhomework\\Hometasks\\Lecture 20\\ServerSide\\app\\src\\props.properties"));
            server.start();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}