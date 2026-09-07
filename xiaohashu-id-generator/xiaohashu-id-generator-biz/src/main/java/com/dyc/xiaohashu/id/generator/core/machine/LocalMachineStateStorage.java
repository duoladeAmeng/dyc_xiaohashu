package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class LocalMachineStateStorage implements MachineStateStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalMachineStateStorage.class);

    private static final String STATE_FILE_DELIMITER = "__";
    private static final String STATE_FILE_VERSION = "v2";
    private static final String STATE_FILE_COMPONENT_DELIMITER = ".";
    public static final String DEFAULT_STATE_LOCATION_PATH = Paths.get(System.getProperty("user.home"), ".cosid-machine-state").toString();

    public final String stateLocation;

    public LocalMachineStateStorage(String stateLocation) {
        this.stateLocation = stateLocation;
    }

    public LocalMachineStateStorage() {
        this(DEFAULT_STATE_LOCATION_PATH);
    }

    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        checkNamespace(namespace);
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }
        File stateFile = getExistingStateFile(namespace, instanceId);
        log.debug("Get from stateLocation : [{}].", stateFile.getAbsolutePath());
        if (!stateFile.exists()) {
            log.info("Get from stateLocation : [{}] not found.", stateFile.getAbsolutePath());
            return MachineState.NOT_FOUND;
        }
        String stateString;
        try {
            stateString = Files.readString(stateFile.toPath(), StandardCharsets.UTF_8).lines().findFirst().orElse(null);
        } catch (IOException e) {
            throw new IdGeneratorException(e);
        }
        if (stateString == null || stateString.isEmpty()) {
            log.warn("Get from stateLocation : [{}] state data is empty.", stateFile.getAbsolutePath());
            return MachineState.NOT_FOUND;
        }
        log.debug("Get state data : [{}].", stateString);
        return MachineState.of(stateString);
    }

    private File getStateFile(String namespace, InstanceId instanceId) {
        File stateDirectory = getStateDirectory();
        String fileName = STATE_FILE_VERSION
                + STATE_FILE_COMPONENT_DELIMITER + encodeComponent(namespace)
                + STATE_FILE_COMPONENT_DELIMITER + encodeComponent(instanceId.getInstanceId())
                + STATE_FILE_COMPONENT_DELIMITER + instanceId.isStable();
        return new File(Paths.get(stateDirectory.getAbsolutePath(), fileName).toString());
    }

    private File getExistingStateFile(String namespace, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        if (stateFile.exists()) {
            return stateFile;
        }
        return getLegacyStateFile(namespace, instanceId);
    }

    private File getLegacyStateFile(String namespace, InstanceId instanceId) {
        File stateDirectory = getStateDirectory();
        String fileName = namespace + STATE_FILE_DELIMITER + instanceId.getInstanceId();
        return new File(Paths.get(stateDirectory.getAbsolutePath(), encode(fileName)).toString());
    }

    private File getStateDirectory() {
        File stateDirectory = new File(stateLocation);
        if (!stateDirectory.exists()) {
            stateDirectory.mkdirs();
        }
        return stateDirectory;
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private String encodeComponent(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeComponent(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }

    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        checkNamespace(namespace);
        if (machineId < 0) {
            throw new IllegalArgumentException(String.format("machineId:[%s] must be greater than or equal to 0!", machineId));
        }
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }
        File stateFile = getStateFile(namespace, instanceId);
        log.debug("Set machineId:[{}] to stateLocation : [{}].", machineId, stateFile.getAbsolutePath());
        String stateString = MachineState.of(machineId, System.currentTimeMillis()).toStateString();
        if (!stateFile.exists()) {
            try {
                boolean ignored = stateFile.createNewFile();
            } catch (IOException e) {
                throw new IdGeneratorException(e);
            }
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(stateFile, false)) {
            fileOutputStream.write(stateString.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.flush();
        } catch (IOException e) {
            throw new IdGeneratorException(e.getMessage(), e);
        }
        File legacyStateFile = getLegacyStateFile(namespace, instanceId);
        if (legacyStateFile.exists()) {
            legacyStateFile.delete();
        }
    }

    @Override
    public void remove(String namespace, InstanceId instanceId) {
        checkNamespace(namespace);
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }
        File stateFile = getStateFile(namespace, instanceId);
        log.info("Remove stateLocation : [{}].", stateFile.getAbsolutePath());
        if (stateFile.exists()) {
            boolean isDeleted = stateFile.delete();
            if (!isDeleted) {
                log.warn("Remove and delete instance :[{}] stateFile in namespace[{}] not successful! FilePath:[{}]", instanceId, namespace, stateFile.getAbsolutePath());
            }
        }
        File legacyStateFile = getLegacyStateFile(namespace, instanceId);
        if (legacyStateFile.exists()) {
            legacyStateFile.delete();
        }
    }

    @Override
    public void clear(String namespace) {
        checkNamespace(namespace);
        log.info("Clear namespace : [{}].", namespace);
        File[] stateFiles = getStateFilesOf(namespace);
        if (stateFiles == null) {
            return;
        }
        for (File stateFile : stateFiles) {
            log.info("Clear stateLocation : [{}].", stateFile.getAbsolutePath());
            boolean ignored = stateFile.delete();
        }
    }

    private File[] getStateFilesOf(String namespace) {
        File stateDirectory = new File(stateLocation);
        if (!stateDirectory.exists()) {
            return new File[0];
        }
        return stateDirectory.listFiles((dir, name) -> namespace.equals(decodeNamespace(name)));
    }

    private String decodeNamespace(String fileName) {
        String v2Namespace = decodeV2Namespace(fileName);
        if (v2Namespace != null) {
            return v2Namespace;
        }
        try {
            String decodedName = decode(fileName);
            int delimiterIndex = decodedName.lastIndexOf(STATE_FILE_DELIMITER);
            if (delimiterIndex < 0) {
                return null;
            }
            return decodedName.substring(0, delimiterIndex);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String decodeV2Namespace(String fileName) {
        String prefix = STATE_FILE_VERSION + STATE_FILE_COMPONENT_DELIMITER;
        if (!fileName.startsWith(prefix)) {
            return null;
        }
        String[] components = fileName.split("\\" + STATE_FILE_COMPONENT_DELIMITER, 4);
        if (components.length != 4) {
            return null;
        }
        try {
            return decodeComponent(components[1]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public int size(String namespace) {
        checkNamespace(namespace);
        return getStateFilesOf(namespace).length;
    }

    @Override
    public boolean exists(String namespace, InstanceId instanceId) {
        checkNamespace(namespace);
        if (instanceId == null) {
            throw new NullPointerException("instanceId can not be null!");
        }
        return getStateFile(namespace, instanceId).exists() || getLegacyStateFile(namespace, instanceId).exists();
    }

    private void checkNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace can not be empty!");
        }
    }
}
