package com.korl.javaquiz.practice;

import java.util.Map;

/**
 * Loads one submission's compiled classes and refuses to resolve anything {@link SandboxPolicy}
 * does not allow.
 *
 * <p>The innermost of three rings, and the one that does not depend on having understood the
 * submission. Whatever a class file says, a class it names has to come through here to be
 * resolved, so a reference {@link ClassFileGuard} misread ends as a {@link ClassNotFoundException}
 * rather than as a loaded class. The application's own classes are unreachable for the same
 * reason: the parent is the platform loader, not the one that loaded this.
 *
 * <p>A loader per attempt, thrown away with it. Two learners never share loaded state, and a
 * static field a submission sets does not outlive the submission that set it.
 */
final class SandboxClassLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final Map<String, byte[]> bytecode;

    /**
     * @param bytecode the submission's own classes by binary name, which are loaded from here
     *                 rather than looked up anywhere
     */
    SandboxClassLoader(Map<String, byte[]> bytecode) {
        // The platform loader, so that java.* resolves and the application classpath does not.
        super("practice-sandbox", ClassLoader.getPlatformClassLoader());
        this.bytecode = bytecode;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = bytecode.containsKey(name) ? define(name) : loadFromPlatform(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> define(String name) {
        byte[] bytes = bytecode.get(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    private Class<?> loadFromPlatform(String name) throws ClassNotFoundException {
        if (!SandboxPolicy.allowsClass(name)) {
            throw new ClassNotFoundException(name + " is not available in the practice sandbox");
        }
        return getParent().loadClass(name);
    }
}
