package serverside.logic;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class PropertiesLoader {
    public static Properties propertiesLoader(String pathToFile) throws IOException {
        Path pathToProp = Path.of(pathToFile);
        Properties properties = new Properties();
        FileInputStream inputStream = new FileInputStream(pathToProp.toFile());
        properties.load(inputStream);
        inputStream.close();
        return properties;
    }
}
