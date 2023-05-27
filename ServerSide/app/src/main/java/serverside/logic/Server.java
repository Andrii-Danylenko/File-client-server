package serverside.logic;

import java.io.*;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server extends Thread {
    protected static List<ClientHandler> clientsList = new ArrayList<>();
    private ServerSocket serverSocket;
    private int port = 8021;
    private static int maxClients = 10;
    private String timeout;
    private String serverIp = "localhost";
    private final List<String> catalogueNamesList = new ArrayList<>();
    private final List<Path> cataloguesPathList = new ArrayList<>();
    private static final ExecutorService inputListener = Executors.newSingleThreadExecutor();
    private static boolean isStarted = false;
    private static final ExecutorService executor = Executors.newFixedThreadPool(maxClients);

    public Server(Properties properties) throws IOException {
        try {
            int portValue = Integer.parseInt((String) properties.get("server.port"));
            if (portValue > 0 && portValue < 65535) {
                this.port = Integer.parseInt((String) properties.get("server.port"));
            }
        } catch (NumberFormatException exception) {
            System.out.println("Provided port is invalid. Default port will be used: " + port);
        }
        this.timeout = properties.getProperty("file.timeout", "1s");
        String ip = properties.getProperty("server.address");
        loadCatalogues(properties);
        try {
            if (!validateIp(ip)) {
                throw new InvalidIpException();
            }
            serverIp = ip;
        } catch (InvalidIpException exception) {
            System.out.println("Provided ip is invalid. Default address will be used: " + serverIp + ":" + port);
        }
    }

    private void loadCatalogues(Properties properties) {
        //RegEx для считывания записанный в props путей
        Pattern pattern = Pattern.compile("catalog\\.(\\w:[a-zA-Z0-9\\\\. ]+|[a-zA-Z0-9\\\\. ]+)\\.(name|dir)");
        Matcher matcher1 = pattern.matcher(properties.getProperty("catalog.<id>.name"));
        Matcher matcher2 = pattern.matcher(properties.getProperty("catalog.<id>.dir"));
        while (matcher1.find()) {
            String name = matcher1.group(1);
            if (validateCataloguePath(name)) {
                catalogueNamesList.add(name);
            }
        }
        while (matcher2.find()) {
            String dir = matcher2.group(1);
            if (validateCataloguePath(dir)) {
                cataloguesPathList.add(Path.of(dir));
            }
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted() || serverSocket.isClosed()) {
            inputListener.execute(() -> {
                String input;
                BufferedReader serverInputReader;
                while (true) {
                    try {
                        serverInputReader = new BufferedReader(new InputStreamReader(System.in));
                        System.out.print("input>");
                        input = serverInputReader.readLine();
                        switch (input.toLowerCase()) {
                            case ("start"): {
                                if (!isStarted) {
                                    this.serverSocket = new ServerSocket(port, maxClients, InetAddress.getByName(serverIp));
                                    isStarted = true;
                                    System.out.println("Server running on address: " + serverIp + ":" + port);
                                } else System.out.println("Server is already running!");
                                break;
                            }
                            case ("stop"): {
                                close();
                                break;
                            }
                            case ("info"): {
                                clientsList.forEach(System.out::println);
                                break;
                            }
                            case ("exit"): {
                                System.exit(0);
                            }
                            default:
                                System.out.println("Unknown command");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            if (isStarted) {
                try {
                    Socket socket = serverSocket.accept();
                    final UUID UUID = java.util.UUID.randomUUID();
                    if (clientsList.size() <= maxClients) {
                        executor.execute(() -> {
                            try {
                                ClientHandler handler = new ClientHandler(UUID, socket);
                                clientsList.add(handler);
                                handler.run();
                            } catch (Exception exception) {
                                exception.printStackTrace();
                            }
                        });
                    } else {
                        System.out.println("Too many clients. No free space left!");
                    }
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private boolean validateIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private boolean validateCataloguePath(String path) {
        try {
            Path.of(path);
            return true;
        } catch (NullPointerException | InvalidPathException exception) {
            return false;
        }
    }

    private void close() {
        System.out.println("Stopping server...");
        isStarted = false;
        try {
            serverSocket.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        System.out.println("Server stopped.");
    }

    protected void sendGlobalMessage(String message) {
        clientsList.forEach(i -> i.sendPrivateMessage(String.format("[SERVER] %s", message)));
    }

    private class ClientHandler implements Runnable {
        private final ArrayDeque<String> lastUsedCommands = new ArrayDeque<>(3);
        private static final AtomicInteger counter = new AtomicInteger(1);
        private final Socket clientSocket;
        private final String clientName;
        private final LocalDateTime connectionTime;
        private DataInputStream dataInputStream;
        private final UUID UUID;
        private DataOutputStream dataOutputStream;
        private String currentCatalogue;

        public ClientHandler(UUID uuid, Socket socket) {
            this.UUID = uuid;
            this.clientName = "Client_" + counter.getAndAdd(1);
            this.connectionTime = LocalDateTime.now();
            this.clientSocket = socket;
            try {
                dataInputStream = new DataInputStream(new BufferedInputStream(this.clientSocket.getInputStream()));
                dataOutputStream = new DataOutputStream(new BufferedOutputStream((this.clientSocket.getOutputStream())));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            sendGlobalMessage("New user connected: " + this.clientName);
        }

        @Override
        public void run() {
            sendPrivateMessage("Hello, " + clientName);
            String input;
            try {
                while (true) {
                    input = dataInputStream.readUTF();
                    String command = input;
                    String parameter = input;
                    //Разделяем значения на команду и параметр при помощи индекса первого пробела.
                    if (input.indexOf(' ') >= 0) {
                        command = input.substring(0, input.indexOf(' '));
                        parameter = input.substring(input.indexOf(' ') + 1).trim();
                    }
                    switch (command) {
                        case ("bye"):
                            addUsedCommand(command);
                            clientsList.remove(this);
                            close();
                            break;
                        case ("catalogs"):
                            addUsedCommand(command);
                            sendPrivateMessage(catalogueOutput());
                            break;
                        case ("use"):
                            addUsedCommand(command);
                            if (!catalogueNamesList.contains(parameter)) {
                                sendPrivateMessage("Haven't found any catalogue with given name");
                                break;
                            }
                            currentCatalogue = parameter;
                            sendPrivateMessage("Successfully updated current catalogue. Current catalogue is: " + currentCatalogue);
                            break;
                        case ("currcat_files"):
                            addUsedCommand(command);
                            try {
                                sendPrivateMessage(outputFilesInCatalogue(currentCatalogue));
                                break;
                            } catch (NullPointerException exception) {
                                sendPrivateMessage("No catalogues chosen yet!");
                            }
                        case ("files"):
                            addUsedCommand(command);
                            if (!catalogueNamesList.contains(parameter)) {
                                sendPrivateMessage("Haven't found any catalogue with given name");
                                break;
                            }
                            sendPrivateMessage(outputFilesInCatalogue(parameter));
                            break;
                        case ("search"):
                            addUsedCommand(command);
                            try {
                                sendPrivateMessage(searchFileByName(parameter));
                            } catch (NullPointerException exception) {
                                sendPrivateMessage("No catalogues chosen yet!");
                            }
                            break;
                        case ("send"): {
                            addUsedCommand(command);
                            Path filePath = Path.of(dataInputStream.readUTF());
                            if (isPath(filePath)) {
                                receiveFile();
                                sendPrivateMessage("Success!");
                            }
                            break;
                        }
                        default:
                            sendPrivateMessage("Unrecognized command!");
                    }
                }
            } catch (IOException exception) {
                if (serverSocket.isClosed()) {
                    try {
                        this.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        public void close() {
            sendGlobalMessage("Server closing.");
            try {
                clientSocket.shutdownInput();
                clientSocket.shutdownOutput();
                clientSocket.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        private synchronized void addUsedCommand(String command) {
            if (lastUsedCommands.size() == 3) {
                //Если размер равен 3, то самая старая запись удаляется
                lastUsedCommands.removeFirst();
            }
            //Добавление команд, использованных пользователем.
            lastUsedCommands.add(command);
        }

        public synchronized void sendPrivateMessage(String message) {
            try {
                dataOutputStream.writeUTF(message);
                dataOutputStream.flush();
            } catch (IOException exception) {
                System.out.println("Something went wrong...");
                exception.printStackTrace();
            }
        }

        private String outputFilesInCatalogue(String pathToCatalogue) {
            //Обход всех папок в данной директории
            StringBuffer result = new StringBuffer();
            try (Stream<Path> directoryStream = Files.walk(Path.of(pathToCatalogue))) {
                directoryStream.filter(Files::isRegularFile).forEach(i -> result.append(i.getFileName()).append("\n"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            return result.toString();
        }

        private String searchFileByName(String fileName) throws NullPointerException {
            //Обход всех папок в данной директории с поиском файла
            StringBuffer result = new StringBuffer();
            try (Stream<Path> directoryStream = Files.walk(Path.of(currentCatalogue))) {
                directoryStream.filter(Files::isRegularFile).filter(i -> i.getFileName().toString().equals(fileName)).forEach(i -> result.append("found " + i.getFileName() + " on path: " + i.toAbsolutePath()).append("\n"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            return result.length() == 0 ? result.append("No such file found.").toString() : result.toString();
        }

        private String catalogueOutput() {
            //Обход всех папок в данной директории с поиском файла
            StringBuffer result = new StringBuffer();
            catalogueNamesList.forEach(i -> result.append(i).append("\n"));
            return currentCatalogue == null ? result.append("No directory selected yet!").toString() : result.append("current catalog: ").append(currentCatalogue).toString();
        }

        public void receiveFile() throws IOException {
            String fileName = dataInputStream.readUTF();
            String targetFilePath = currentCatalogue + File.separator + fileName;
            Path targetPath = Path.of(targetFilePath);

            if (Files.exists(targetPath)) {
                throw new FileAlreadyExistsException("This file already exists!");
            }

            try (FileChannel fileChannel = FileChannel.open(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                long size = dataInputStream.readLong();
                ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);

                while (size > 0 && fileChannel.read(buffer) != -1) {
                    buffer.flip();
                    fileChannel.write(buffer);
                    buffer.clear();
                    size -= buffer.remaining();
                }

                sendPrivateMessage("File sent successfully");
            } catch (FileAlreadyExistsException exception) {
                sendPrivateMessage(exception.getMessage());
            }
        }

        public static boolean isPath(Path path) {
            return Files.exists(path) && Files.isRegularFile(path);
        }

        public String toString() {
            return String.format("Client's name: %s, \nclient's UUID: %s, \nclient's ip: %s, \nclient's port: %s, \nlast %d used commands: %s\n--------------------------------",
                    this.clientName, this.UUID, this.clientSocket.getInetAddress(), this.clientSocket.getPort(), 3, lastUsedCommands.isEmpty() ? "no commands used yet." : lastUsedCommands);
        }
    }
}
