package clientside;

import com.google.common.math.PairedStats;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

public class Client {
 private static Socket clientSocket;

    private static DataOutputStream dataOutputStream = null;
    private static DataInputStream dataInputStream = null;

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            clientSocket = new Socket("localhost", 8321);
            dataInputStream = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            dataOutputStream = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            String input;
            while (true) {
                System.out.println(dataInputStream.readUTF());
                System.out.print("input> ");
                input = scanner.nextLine();
                String command = input;
                String parameter = input;
                //Разделяем значения на команду и параметр при помощи индекса первого пробела.
                if (input.indexOf(' ') >= 0) {
                    command = input.substring(0, input.indexOf(' '));
                    parameter = input.substring(input.indexOf(' ') + 1).trim();
                }
                dataOutputStream.writeUTF(input);
                if ("send".equalsIgnoreCase(command)) {
                    dataOutputStream.writeUTF(parameter);
                    Path filePath = Path.of(parameter);
                    if (isPath(filePath)) {
                            uploadFile(filePath);
                        } else System.out.println("Provided path doesn't contain any files");
                }
                dataOutputStream.flush();
            }
        } catch (IOException exception) {
            System.err.println("Lost connection to the server");
        }
    }

    public static void uploadFile(Path pathToFile) throws IOException {
        String fileName = pathToFile.getFileName().toString();
        long fileSize = Files.size(pathToFile);

        dataOutputStream.writeUTF(fileName);
        dataOutputStream.writeLong(fileSize);
        dataOutputStream.flush();

        try (FileChannel fileChannel = FileChannel.open(pathToFile, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);

            while (fileChannel.read(buffer) != -1) {
                buffer.flip();
                dataOutputStream.write(buffer.array(), 0, buffer.remaining());
                dataOutputStream.flush();
                buffer.clear();
            }

            String serverResponse = dataInputStream.readUTF();
            if (!serverResponse.isEmpty()) {
                System.out.println(serverResponse);
            }
        }
    }
    public static boolean isPath(Path path) {
        return Files.exists(path) && Files.isRegularFile(path);
    }
}
