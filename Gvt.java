package uj.wmii.pwj.gvt;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Gvt {

    private final ExitHandler exitHandler;

    private final Path directoryPath = Paths.get(".gvt");
    private final Path activeVerPath = directoryPath.resolve("active.txt");
    private int activeVersion;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    enum commend {
        init,
        add,
        detach,
        checkout,
        commit,
        history,
        version
    }

    void mainInternal(String... args) {
        if (args.length == 0) {
            exitHandler.exit(1, "Please specify command.");
            return;
        }

        if (!Files.exists(Path.of(".gvt")) && !Objects.equals(args[0], "init")) {
            exitHandler.exit(-2, "Current directory is not initialized. Please use init command to initialize.");
            return;
        }

        try {
            commend com = commend.valueOf(args[0]);

            if (com != commend.init) activeVersion = Integer.parseInt(Files.readString(activeVerPath));

            switch (com) {
                case init -> init();
                case add -> {
                    add(args[1]);
                    checkMessege(args);
                }
                case detach -> {
                    detach(args);
                    checkMessege(args);
                }
                case checkout -> checkout(args);
                case commit -> {
                    commit(args);
                    checkMessege(args);
                }
                case history -> history(args);
                case version -> version();
                default -> System.out.println("Unknown command " + args[0]);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Underlying system problem. See ERR for details.");
        }
    }

    private void version() {

        Path versionPath = directoryPath.resolve(String.valueOf(activeVersion));
        Path filePath = versionPath.resolve("Message.txt");
        try {
            String firstLine = Files.readString(filePath);

            exitHandler.exit(0, "Version: " + activeVersion + "\n" + firstLine.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void history(String[] args) throws IOException {
        int end = (args.length < 2) ? -1 : activeVersion - Integer.parseInt(args[2]);

        StringBuilder history = new StringBuilder();
        String name = "Message.txt";

        for (int i = activeVersion; i > end; i--) {
            Path versPath = directoryPath.resolve(String.valueOf(i));
            Path messPath = versPath.resolve(name);
            history.append(i).append(": ").
                    append(Files.readAllLines(messPath).get(0)).append("\n");
        }
        exitHandler.exit(0, String.valueOf(history));
    }

    private void commit(String[] args) {
        if (args.length < 2) {
            exitHandler.exit(50, "Please specify file to commit.");
            return;
        }

        String name = args[1];

        try {
            if (!Files.exists(Path.of(name))) {
                exitHandler.exit(51, "File not found. File: " + name);
                return;
            }

            if (!doesFileExistInAnyVers(name)) {
                exitHandler.exit(0, "File is not added to gvt. File: " + name);
                return;
            }

            versionUpdate();
            Path actualVerPath = directoryPath.resolve(String.valueOf(activeVersion));
            createNewVersion(activeVersion, activeVersion - 1);
            addMessage(String.valueOf(activeVersion), "File committed successfully. File: " + name);

            Files.writeString(actualVerPath.resolve(name), Files.readString(Path.of(name)));


            exitHandler.exit(0, "File committed successfully. File: " + name);

        } catch (IOException e) {
            e.printStackTrace();
            exitHandler.exit(52, "File cannot be committed, see ERR for details. File: " + name);
        }

    }

    private boolean doesFileExistInAnyVers(String name) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path subPath : stream) {
                Path filePath = subPath.resolve(name);
                if (Files.exists(filePath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkout(String[] args) throws IOException {
        if (Integer.parseInt(args[1]) > activeVersion) {
            exitHandler.exit(60, "Invalid version number: " + args[1]);
            return;
        }

        String sourceVer = args[1];
        Path sourceVerPath = directoryPath.resolve(sourceVer);
        Path actualVerPath = directoryPath.resolve(String.valueOf(activeVersion));

        File sourceFile = new File(String.valueOf(sourceVerPath));
        File[] files = sourceFile.listFiles();

        assert files != null;
        for (File file : files) {
            String fileName = file.getName();
            if (Files.exists(Path.of(file.getName()))) {
                Files.deleteIfExists(Path.of(fileName));
                Files.copy(sourceVerPath.resolve(fileName), Path.of(fileName));
            }
        }

        exitHandler.exit(0, "Checkout successful for version: " + sourceVer);
    }

    private void detach(String[] args) {
        if (args.length < 2) {
            exitHandler.exit(30, "Please specify file to detach.");
            return;
        }
        String name = args[1];

        Path actualVerPath = directoryPath.resolve(String.valueOf(activeVersion));

        if (!Files.exists(actualVerPath.resolve(name))) {
            exitHandler.exit(0, "File is not added to gvt. File: " + name);
            return;
        }

        try {

            versionUpdate();
            actualVerPath = directoryPath.resolve(String.valueOf(activeVersion));

            createNewVersion(activeVersion, activeVersion - 1);
            addMessage(String.valueOf(activeVersion), "File detached successfully. File: " + name);

            Files.deleteIfExists(actualVerPath.resolve(name));

            exitHandler.exit(0, "File detached successfully. File: " + name);
        } catch (IOException e) {
            e.printStackTrace();
            exitHandler.exit(31, "File cannot be detached, see ERR for details. File: " + name);
        }

    }

    private void checkMessege(String[] args) throws IOException {
        if (args.length >= 3) {
            addMessage(String.valueOf(activeVersion), args[3]);
        }
    }

    private void add(String name) {
        if (name == null) {
            exitHandler.exit(20, "Please specify file to add");
            return;
        }

        if (!Files.exists(Path.of(name))) {
            exitHandler.exit(21, "File not found. File: " + name);
            return;
        }

        try {
            Path activeDir = Path.of(String.valueOf(directoryPath.resolve(String.valueOf(activeVersion))));
            Path filePath = activeDir.resolve(name);

            if (Files.exists(filePath)) {
                exitHandler.exit(0, "File already added. File: " + name);
                return;
            }

            versionUpdate();

            createNewVersion(activeVersion, activeVersion - 1);
            addFileToVer(String.valueOf(activeVersion), name);
            addMessage(String.valueOf(activeVersion), "File added successfully. File: " + name);

            exitHandler.exit(0, "File added successfully. File: " + name);

        } catch (Exception e) {
            exitHandler.exit(22, "File cannot be added. See ERR for details. File: " + name);
        }


    }

    private void versionUpdate() throws IOException {
        activeVersion++;
        Files.deleteIfExists(activeVerPath);
        Files.createFile(activeVerPath);
        Files.writeString(activeVerPath, String.valueOf(activeVersion));

    }

    private void addFileToVer(String version, String fileName) {
        try {
            String verPath = String.valueOf(directoryPath.resolve(version));
            Path filePath = Paths.get(verPath, fileName);
            Files.createFile(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void init() {
        try {

            if (!Files.isDirectory(Path.of(".gvt"))) {

                Files.createDirectories(directoryPath);

                activeVersion = 0;
                Files.createFile(directoryPath.resolve("active.txt"));
                Files.writeString(directoryPath.resolve("active.txt"), "0");

                createNewVersion(activeVersion, -1);
                addMessage(String.valueOf(0), "GVT initialized.");

                exitHandler.exit(0, "Current directory initialized successfully.");

            } else {
                exitHandler.exit(10, "Current directory is already initialized.");
            }
        } catch (Exception e) {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }

    }

    private void addMessage(String verName, String message) throws IOException {
        String verPath = String.valueOf(directoryPath.resolve(verName));
        String fileName = "Message.txt";
        Path filePath = Paths.get(verPath, fileName);

        Files.deleteIfExists(filePath);
        Files.createFile(filePath);
        Files.writeString(filePath, message);
    }

    private void createNewVersion(int activeVersion, int sourceVer) {
        try {

            String nameOfNewDir = String.valueOf(activeVersion);
            Path newDirectory = directoryPath.resolve(nameOfNewDir);
            Files.createDirectory(newDirectory);

            if (activeVersion == 0) return;

            Path sourceVerPath = directoryPath.resolve(String.valueOf(sourceVer));

            File sourceVerFile = new File(String.valueOf(sourceVerPath));

            File[] files = sourceVerFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    Files.copy(Path.of(file.getAbsolutePath()), newDirectory.resolve(file.getName()));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
