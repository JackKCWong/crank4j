package com.danielflower.crank4j.sharedstuff;

import com.danielflower.crank4j.utils.Crank4jException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;


public class Config {

    public static Config load(String[] commandLineArgs) throws IOException {
        Map<String, String> envVars = new HashMap<>(System.getenv());
        Map<String, String> env = new HashMap<>();

        for (String key : envVars.keySet()) {
            String value = envVars.get(key);
            env.put(key.replace("_", ".").toLowerCase(), value);
        }
        for (String key : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(key);
            env.put(key, value);
        }
        for (String commandLineArg : commandLineArgs) {
            File file = new File(commandLineArg);
            if (file.isFile()) {
                Properties props = new Properties();
                try (FileInputStream inStream = new FileInputStream(file)) {
                    props.load(inStream);
                }
                for (String key : props.stringPropertyNames()) {
                    env.put(key, props.getProperty(key));
                }
            }
        }
        return new Config(env);
    }

    private final Map<String, String> raw;

    public Config(Map<String, String> raw) {
        this.raw = raw;
    }


    public String get(String name, String defaultVal) {
        return raw.getOrDefault(name, defaultVal);
    }

    public String get(String name) {
        String s = get(name, null);
        if (s == null) {
            throw new Crank4jException("Missing config item: " + name);
        }
        return s;
    }

    public Integer[] getIntArray(String name) {
        String s = get(name);
        try {
            String[] token = s.trim().split(",");
            Integer[] ints = new Integer[token.length];
            for (int i = 0; i < token.length; i++) {
                ints[i] = Integer.parseInt(token[i]);
            }
            return ints;
        } catch (NumberFormatException e) {
            throw new Crank4jException("Could not convert " + name + "=" + s + " to an integer");
        }
    }

    public int getInt(String name) {
        String s = get(name);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new Crank4jException("Could not convert " + name + "=" + s + " to an integer");
        }
    }

    public boolean hasItem(String name) {
        return raw.containsKey(name);
    }

    public File getFile(String name) {
        File f = new File(get(name));
        if (!f.isFile()) {
            throw new Crank4jException("Could not find " + name + " file: " + dirPath(f));
        }
        return f;
    }

}

